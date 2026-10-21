#!/usr/bin/env python3
"""Vigila la carpeta del marco en Google Drive y carga solo los turnos y los menús del cole.

En el servidor: «rutas.base» de config/familia.json (p. ej. /srv/familia/marco), lanzado por familia-drive.timer cada
15 minutos. Lee Drive con la cuenta de servicio (solo lectura; la carpeta está compartida con ella) y escribe en
Google Calendar con los scripts de siempre.

- <carpeta>/Turnos: cada `.Shifter` nuevo o cambiado → backups/turnos/ → shifter_sync.py (desde hace 31 días).
- <carpeta>/Menús: cada PDF nuevo o cambiado → Claude Code (`claude -p`) lo pasa a menus/menu-AAAA-MM.json (con
  «menu.ejemplo» como modelo) → comprobación estricta → menu_sync.py. Si algo no cuadra, no se carga nada.
- Aviso en la lista de tareas de Todoist (vía Home Assistant) de lo cargado o de lo que ha fallado.
- Estado en drive-state.json (fichero → modifiedTime): cada versión se procesa una vez.

De config/familia.json: «drive» (ID de la carpeta y nombres de las subcarpetas), «turnos.persona», «menu» y las
rutas. Si «drive.carpeta» está vacío se usa `drive.marco` de calendarios.properties; si tampoco, no hace nada.

Uso: python3 -I scripts/drive_watch.py [--dry-run]
"""
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta

SCRIPTS = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPTS)
import familia  # noqa: E402
import gcal  # noqa: E402

CFG = familia.config()
ROOT = familia.base()
STATE = familia.ruta("drive_estado", "drive-state.json")
LOG = familia.ruta("drive_log", "drive-watch.log")
MENUS = familia.ruta("menus", "menus")
BACKUPS = familia.ruta("backups_turnos", "backups/turnos")
HA = familia.ha_url()
TASKS = familia.ha().get("tareas_todoist", "")
MENU = CFG.get("menu", {})
EXAMPLE = familia.ruta("menu_ejemplo", MENU.get("ejemplo") or "menus/menu-ejemplo.json")
PERSONA = CFG.get("turnos", {}).get("persona", "")
SUBCARPETAS = {"turnos": "Turnos", "menus": "Menús", **CFG.get("drive", {}).get("subcarpetas", {})}
MESES = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre",
         "noviembre", "diciembre"]


def log(msg: str) -> None:
    line = f"{datetime.now():%Y-%m-%d %H:%M:%S} {msg}"
    print(line)
    with open(LOG, "a") as f:
        f.write(line + "\n")


def drive(tok: str, path: str, **q) -> dict:
    url = "https://www.googleapis.com/drive/v3" + path + ("?" + urllib.parse.urlencode(q) if q else "")
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers={"Authorization": f"Bearer {tok}"}), timeout=30))


def download(tok: str, file_id: str, dest: str) -> None:
    url = f"https://www.googleapis.com/drive/v3/files/{file_id}?alt=media"
    data = urllib.request.urlopen(urllib.request.Request(url, headers={"Authorization": f"Bearer {tok}"}), timeout=60).read()
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, "wb") as f:
        f.write(data)


def children(tok: str, parent: str, folders: bool = False) -> list[dict]:
    mime = "mimeType='application/vnd.google-apps.folder'" if folders else "mimeType!='application/vnd.google-apps.folder'"
    res = drive(tok, "/files", q=f"'{parent}' in parents and trashed=false and {mime}",
                fields="files(id,name,modifiedTime,mimeType,size)", pageSize=200)
    return res.get("files", [])


def notify(title: str, detail: str = "") -> None:
    """Tarea en la lista de Todoist a través de Home Assistant (se ve en el marco y en los móviles)."""
    if not TASKS:
        return
    try:
        token = familia.ha_token()
        body = {"entity_id": TASKS, "item": title}
        if detail:
            body["description"] = detail
        req = urllib.request.Request(f"{HA}/api/services/todo/add_item", data=json.dumps(body).encode(), method="POST",
                                     headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"})
        urllib.request.urlopen(req, timeout=20)
    except Exception as e:  # noqa: BLE001
        log(f"AVISO no enviado a Todoist ({e}): {title}")


def run(*args: str) -> str:
    r = subprocess.run([sys.executable, "-I", *args], cwd=ROOT, capture_output=True, text=True, timeout=600)
    if r.returncode != 0:
        raise RuntimeError((r.stderr or r.stdout).strip()[-400:])
    return r.stdout.strip()


# --- Turnos ---

def handle_shifter(path: str, dry: bool) -> str:
    with open(path, "rb") as f:
        if f.read(16) != b"SQLite format 3\x00":
            raise RuntimeError("no es un fichero de Shifter (no es una base SQLite)")
    desde = (date.today() - timedelta(days=31)).isoformat()
    return run(os.path.join(SCRIPTS, "shifter_sync.py"), path, "--desde", desde, *(["--dry-run"] if dry else []))


# --- Menús ---

PROMPT = """Lee el PDF del menú del comedor escolar {pdf} y escribe SOLO un objeto JSON (sin texto antes ni después,
sin ```), con exactamente el mismo formato que este ejemplo:

{example}

Reglas:
- "mes": AAAA-MM del menú.
- {general} come el MENÚ GENERAL (la cuadrícula de la primera página). Para cada día con menú: "dia" (número),
  "primero" y "segundo" (los dos platos principales, tal cual, sin la información de alérgenos «(Al : …)»),
  "extras" (guarnición, postre y pan separados por « · »), y "nota" si el día pone algo como «Día de proteína vegetal».
{alternativo}- No incluyas fines de semana ni días sin menú (festivos).
- "_fuente": una frase con el nombre del fichero, la empresa{empresa} y el colegio{colegio}.
"""

ALTERNATIVO = """- {nino} come el menú {descripcion} ({pagina}). Si su segundo plato es distinto del general, ponlo en
  "alternativo"; si es igual, no pongas "alternativo". Si también cambia su primer plato, añade "alternativo_primero".
"""


def prompt(pdf: str, example: str) -> str:
    alt = MENU.get("alternativo") or {}
    reglas_alt = ALTERNATIVO.format(nino=alt.get("nino", "Uno de los niños"), descripcion=alt.get("descripcion", "alternativo"),
                                    pagina=alt.get("pagina", "su página propia")) if alt else \
        '- No hay menú alternativo: no pongas "alternativo" ni "alternativo_primero".\n'
    return PROMPT.format(pdf=pdf, example=example, general=MENU.get("general") or "El niño", alternativo=reglas_alt,
                         empresa=f" ({MENU['empresa']})" if MENU.get("empresa") else "",
                         colegio=f" ({MENU['colegio']})" if MENU.get("colegio") else "")


def check_menu(data: dict) -> list[str]:
    errs = []
    m = re.fullmatch(r"(\d{4})-(\d{2})", str(data.get("mes", "")))
    if not m:
        return ["falta «mes» (AAAA-MM)"]
    year, month = int(m[1]), int(m[2])
    first = date(year, month, 1)
    if not (date.today().replace(day=1) - timedelta(days=62) <= first <= date.today() + timedelta(days=62)):
        errs.append(f"el mes {data['mes']} no es reciente")
    dias = data.get("dias") or []
    if not 12 <= len(dias) <= 23:
        errs.append(f"{len(dias)} días (se esperan entre 12 y 23)")
    seen = set()
    for d in dias:
        try:
            day = date(year, month, int(d["dia"]))
        except Exception:  # noqa: BLE001
            errs.append(f"día no válido: {d.get('dia')}")
            continue
        if day.weekday() >= 5:
            errs.append(f"el {day.day} es fin de semana")
        if day in seen:
            errs.append(f"el {day.day} está repetido")
        seen.add(day)
        for k in ("primero", "segundo"):
            if not str(d.get(k, "")).strip() or "(Al" in str(d.get(k, "")):
                errs.append(f"el {day.day}: «{k}» vacío o con alérgenos")
    return errs


def handle_menu(path: str, dry: bool) -> str:
    example = open(EXAMPLE, encoding="utf-8").read()
    r = subprocess.run([MENU.get("claude", "claude"), "-p", prompt(path, example), "--allowedTools", "Read",
                        "--output-format", "text"], cwd=os.path.dirname(path), capture_output=True, text=True, timeout=900)
    out = r.stdout.strip()
    m = re.search(r"\{.*\}", out, re.S)
    if r.returncode != 0 or not m:
        raise RuntimeError(f"Claude no devolvió un JSON ({(r.stderr or out)[-200:]})")
    data = json.loads(m.group(0))
    errs = check_menu(data)
    if errs:
        raise RuntimeError("el menú no cuadra: " + "; ".join(errs[:5]))
    # En simulación se guarda aparte (….prueba.json) para poder compararlo sin tocar el bueno
    dest = os.path.join(MENUS, f"menu-{data['mes']}{'.prueba' if dry else ''}.json")
    with open(dest, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    res = run(os.path.join(SCRIPTS, "menu_sync.py"), dest, *(["--dry-run"] if dry else []))
    return f"{res} · {data['mes']} ({len(data['dias'])} días)"


# --- Bucle ---

def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    folder = (CFG.get("drive", {}).get("carpeta") or "").strip()
    if not folder or "<" in folder:
        folder = gcal.props().get("drive.marco", "").strip()
    if not folder:
        return
    tok = gcal.token("https://www.googleapis.com/auth/drive.readonly")
    state = json.load(open(STATE)) if os.path.exists(STATE) else {}
    nombres = {familia.clave_texto(v): k for k, v in SUBCARPETAS.items()}
    subs = {nombres[familia.clave_texto(f["name"])]: f["id"] for f in children(tok, folder, folders=True)
            if familia.clave_texto(f["name"]) in nombres}
    changed = False
    for kind, sub in (("turnos", subs.get("turnos")), ("menus", subs.get("menus"))):
        if not sub:
            continue
        for f in sorted(children(tok, sub), key=lambda x: x["modifiedTime"]):
            if state.get(f["id"]) == f["modifiedTime"]:
                continue
            name = f["name"]
            try:
                if kind == "turnos" and name.lower().endswith(".shifter"):
                    dest = os.path.join(BACKUPS, f"turnos-{f['modifiedTime'][:10]}-{f['id'][:6]}.Shifter")
                    download(tok, f["id"], dest)
                    res = handle_shifter(dest, a.dry_run)
                    log(f"Turnos «{name}»: {res}")
                    if not a.dry_run:
                        notify(f"✅ Turnos{' de ' + PERSONA if PERSONA else ''} actualizados en el calendario", f"{name}\n{res}")
                elif kind == "menus" and name.lower().endswith(".pdf"):
                    dest = os.path.join(MENUS, f"menu-{f['modifiedTime'][:10]}-{f['id'][:6]}.pdf")
                    download(tok, f["id"], dest)
                    res = handle_menu(dest, a.dry_run)
                    log(f"Menú «{name}»: {res}")
                    mes = re.search(r"(\d{4})-(\d{2}) \(", res)
                    if not a.dry_run:
                        notify(f"✅ Menú del cole de {MESES[int(mes[2]) - 1] if mes else 'este mes'} cargado", f"{name}\n{res}")
                else:
                    log(f"Ignorado «{name}» en {kind}")
            except Exception as e:  # noqa: BLE001
                log(f"ERROR con «{name}» ({kind}): {e}")
                if not a.dry_run:
                    notify(f"⚠️ No he podido cargar «{name}» en el marco", str(e)[:500])
            if not a.dry_run:
                state[f["id"]] = f["modifiedTime"]
                changed = True
    if changed:
        with open(STATE, "w") as fh:
            json.dump(state, fh, indent=1)


if __name__ == "__main__":
    main()

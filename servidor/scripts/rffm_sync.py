#!/usr/bin/env python3
"""Partidos de fútbol de los niños (RFFM, Real Federación de Fútbol de Madrid) → calendario de fútbol de Google.

La página de calendario de la RFFM (Next.js) trae todas las jornadas del grupo en `__NEXT_DATA__`
(props.pageProps.calendar.rounds), aunque en pantalla solo enseñe la actual. Para cada equipo de «futbol.equipos» en
config/familia.json:

- Se saltan las jornadas de descanso (rival «No asignado»).
- Con hora: evento de 1 h; sin hora: de día completo, con «hora por confirmar».
- Campo: el de la RFFM; si está «Pendiente de asignar», en casa el de «futbol.campo_casa» y fuera el más repetido
  del rival cuando juega de local en ese grupo, marcados como «probable».
- Resultado (ya jugado) desde el lado del niño: Ganado / Perdido / Empate.
- ID fijo por partido (`rffm` + hash del equipo y el acta): repetir actualiza fechas, horas, campos y resultados sin duplicar,
  y se borran los partidos de ese equipo que ya no estén. Solo toca eventos creados por este script.

La URL de cada equipo es la de su grupo en rffm.es → Competición → Calendario (con temporada, competición y grupo).

Uso: python3 -I scripts/rffm_sync.py --print        (solo lee la RFFM y enseña la tabla)
     python3 -I scripts/rffm_sync.py [--dry-run]    (sincroniza con el calendario de fútbol)
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import urllib.parse
import urllib.request
from collections import Counter
from datetime import datetime, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import familia  # noqa: E402
import gcal  # noqa: E402

TZ = familia.zona()
PREFIX = "rffm"
PENDING = {"", "pendiente de asignar"}
CFG = familia.config().get("futbol", {})
CAMPO_CASA = CFG.get("campo_casa", "")


def fetch(url: str) -> dict:
    if "<" in url:
        raise SystemExit(f"ERROR: la URL de la RFFM aún tiene marcadores sin rellenar: {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (marco-familiar)"})
    html = urllib.request.urlopen(req, timeout=30).read().decode("utf-8")
    m = re.search(r'<script id="__NEXT_DATA__"[^>]*>(.*?)</script>', html, re.S)
    if not m:
        raise SystemExit(f"ERROR: la RFFM ha cambiado la página (sin __NEXT_DATA__): {url}")
    return json.loads(m.group(1))["props"]["pageProps"]["calendar"]


def clean_field(name: str) -> str:
    """«CIUDAD - CAMPO MUNICIPAL (HA)(HA)» → «CIUDAD - CAMPO MUNICIPAL» (HA = hierba artificial)."""
    return re.sub(r"\s*\((H\.?A\.?)\)", "", name or "").strip()


def short(team: str) -> str:
    """Nombre corto del rival para el título: sin siglas del club delante."""
    t = re.sub(r"^(C\.D\.B\.|C\.D\.|C\.F\.|A\.D\.|AD|E\.F\.|ESC\.\s*FUT\.|ESC\.\s*MPAL\.\s*DE\s*FUT\.|ESCUELA DE FUTBOL DE|AGRUPACION)\s+", "", team.strip(), flags=re.I)
    return t.title().replace("'", "").replace("De ", "de ").replace("Del ", "del ").strip()


def matches(team: dict, cfg: dict) -> list[dict]:
    cal = fetch(team["url"])
    name = team["equipo"]
    home_fields: dict[str, Counter] = {}
    for r in cal["rounds"]:
        for m in r["equipos"]:
            f = clean_field(m.get("campo", ""))
            if f.lower() not in PENDING and "no asignado" not in m["equipo_local"].lower():
                home_fields.setdefault(m["equipo_local"], Counter())[f] += 1
    out = []
    for r in cal["rounds"]:
        for m in r["equipos"]:
            if name not in (m["equipo_local"], m["equipo_visitante"]):
                continue
            home = m["equipo_local"] == name
            rival = m["equipo_visitante"] if home else m["equipo_local"]
            if "no asignado" in rival.lower() or not m.get("fecha"):
                continue  # jornada de descanso
            day = datetime.strptime(m["fecha"], "%d-%m-%Y").date()
            field, guessed = clean_field(m.get("campo", "")), False
            if field.lower() in PENDING:
                guessed = True
                field = cfg["campo_casa"] if home else (home_fields.get(rival) or Counter({"": 1})).most_common(1)[0][0]
            result = None
            if m.get("goles_casa", "") != "" and m.get("goles_visitante", "") != "":
                ours, theirs = (int(m["goles_casa"]), int(m["goles_visitante"])) if home else (int(m["goles_visitante"]), int(m["goles_casa"]))
                result = (ours, theirs, "Ganado" if ours > theirs else "Perdido" if ours < theirs else "Empate")
            out.append(dict(nino=team["nino"], equipo=name, acta=m["codacta"], jornada=r["codjornada"], day=day,
                            hora=m.get("hora") or None, home=home, rival=rival, local=m["equipo_local"],
                            visitante=m["equipo_visitante"], field=field, guessed=guessed, result=result,
                            competicion=cal["competicion"], grupo=cal["grupo"], color=team.get("color_google")))
    return out


def event_id(p: dict) -> str:
    """`rffm` + hash del equipo y el acta en hexadecimal (Google admite 0-9 y a-v). Con `rffm<acta>` a secas, Google
    respondía «ya existe» con IDs que no estaban en el calendario."""
    return PREFIX + hashlib.sha1(f"{p['equipo']}|{p['acta']}".encode()).hexdigest()[:24]


def body(p: dict, url: str) -> dict:
    # Dónde, solo si no es el campo de casa (hay grupos que juegan todo allí, también de visitante, y equipos que a
    # veces juegan «en casa» en otro campo): «(Barrio Norte)», «(Colegio Ejemplo)»…
    where = p["field"].split(" - ", 1)[-1].title() if p["field"] and p["field"] != CAMPO_CASA else ""
    title = f"⚽ {p['nino']} vs {short(p['rival'])}" + (f" ({where}{'?' if p['guessed'] else ''})" if where else "")
    if p["result"]:
        title += f" · {p['result'][0]}-{p['result'][1]}"
    elif not p["hora"]:
        title += " · hora por confirmar"
    field = (p["field"] + (" (probable)" if p["guessed"] else "")) if p["field"] else "Campo por confirmar"
    desc = [f"{p['competicion']} · {p['grupo']} · Jornada {p['jornada']}",
            f"{p['local']} – {p['visitante']}", f"{'En casa' if p['home'] else 'Fuera'} · {field}"]
    if p["result"]:
        desc.append(f"Resultado: {p['result'][2]} {p['result'][0]}-{p['result'][1]}")
    desc.append(url)
    if p["hora"]:
        start = datetime.combine(p["day"], datetime.strptime(p["hora"], "%H:%M").time())
        s = {"dateTime": start.isoformat(timespec="seconds"), "timeZone": TZ}
        e = {"dateTime": (start + timedelta(hours=1)).isoformat(timespec="seconds"), "timeZone": TZ}
    else:
        s, e = {"date": p["day"].isoformat()}, {"date": (p["day"] + timedelta(days=1)).isoformat()}
    b = {"id": event_id(p), "summary": title, "description": "\n".join(desc), "location": field,
         "start": s, "end": e, "transparency": "opaque" if p["hora"] else "transparent",
         "extendedProperties": {"private": {"origen": "rffm", "equipo": p["equipo"]}}}
    if p["color"]:
        b["colorId"] = p["color"]
    return b


def same(e: dict, b: dict) -> bool:
    keys = ("summary", "description", "location", "colorId")
    return (e.get("status") != "cancelled" and all(e.get(k) == b.get(k) for k in keys)
            and e["start"].get("date") == b["start"].get("date") and e["end"].get("date") == b["end"].get("date")
            and (e["start"].get("dateTime") or "")[:19] == (b["start"].get("dateTime") or "")[:19])


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--print", action="store_true", help="solo enseña los partidos, sin tocar Google")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    cfg = CFG

    teams = [(t, matches(t, cfg)) for t in cfg.get("equipos", [])]
    if a.print:
        for t, ms in teams:
            print(f"== {t['nino']} · {t['equipo']} · {ms[0]['competicion'] if ms else ''}")
            for p in ms:
                res = f"{p['result'][2]} {p['result'][0]}-{p['result'][1]}" if p["result"] else ""
                print(f"  J{p['jornada']:>2} {p['day']:%a %d/%m} {p['hora'] or '--:--'} {'casa ' if p['home'] else 'fuera'} "
                      f"vs {short(p['rival'])[:32]:32} | {p['field'][:40]}{' (probable)' if p['guessed'] else ''} {res}")
        return

    cal = gcal.calendario(cfg.get("calendario", "futbol"))
    tok = gcal.token()
    for t, ms in teams:
        wanted = {b["id"]: b for b in (body(p, t["url"]) for p in ms)}
        q = {"privateExtendedProperty": ["origen=rffm", f"equipo={t['equipo']}"], "maxResults": 2500,
             "showDeleted": "true", "singleEvents": "true"}
        code, res = gcal.api(tok, f"/calendars/{cal}/events?" + urllib.parse.urlencode(q, doseq=True))
        if code != 200:
            sys.exit(f"ERROR listando eventos: HTTP {code} {res}")
        existing = {e["id"]: e for e in res.get("items", [])}
        created = updated = unchanged = deleted = 0
        for eid, b in wanted.items():
            e = existing.get(eid)
            if e and same(e, b):
                unchanged += 1
                continue
            if not a.dry_run:
                if e is None:
                    code, res = gcal.api(tok, f"/calendars/{cal}/events", "POST", b)
                    if code == 409:
                        code, res = gcal.api(tok, f"/calendars/{cal}/events/{eid}", "PUT", {**b, "status": "confirmed"})
                else:
                    code, res = gcal.api(tok, f"/calendars/{cal}/events/{eid}", "PUT", {**b, "status": "confirmed"})
                if code not in (200, 201):
                    sys.exit(f"ERROR en {eid}: HTTP {code} {res}")
            created += e is None
            updated += e is not None
        for eid, e in existing.items():
            if eid in wanted or e.get("status") == "cancelled" or not eid.startswith(PREFIX):
                continue
            if not a.dry_run:
                code, res = gcal.api(tok, f"/calendars/{cal}/events/{eid}", "DELETE")
                if code not in (200, 204, 410):
                    sys.exit(f"ERROR borrando {eid}: HTTP {code} {res}")
            deleted += 1
        tag = " (simulación)" if a.dry_run else ""
        print(f"{t['nino']} · {t['equipo']}{tag}: {created} creados, {updated} actualizados, {unchanged} sin cambios, "
              f"{deleted} borrados ({len(wanted)} partidos)")


if __name__ == "__main__":
    main()

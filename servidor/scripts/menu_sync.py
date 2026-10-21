#!/usr/bin/env python3
"""Carga el menú del cole de un mes (menus/menu-AAAA-MM.json) en el calendario del menú de Google.

Un evento de día completo por día: título = 1.º · 2.º plato (menú general) y, si el menú alternativo de uno de los
niños (una dieta especial) cambia, «(<niño>: …)»; descripción = guarnición, postre, pan y notas. ID fijo
(`menu` + AAAAMMDD): repetir la carga actualiza en vez de duplicar, y en ese mes se borran los días que ya no están.
Solo toca eventos creados por este script.

Formato del JSON: ver menus/menu-ejemplo.json («alternativo» y «alternativo_primero» solo cuando cambian).
De config/familia.json: «menu.calendario» (clave en calendarios.properties), «menu.nombre_calendario» y
«menu.alternativo» (niño y descripción del menú alternativo; null si no hay).

Uso: python3 -I scripts/menu_sync.py menus/menu-AAAA-MM.json [--dry-run]
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.parse
from datetime import date, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import familia  # noqa: E402
import gcal  # noqa: E402

PREFIX = "menu"
_MENU = familia.config().get("menu", {})
_ALT = _MENU.get("alternativo") or {}


def bodies(path: str) -> tuple[date, date, dict]:
    data = json.load(open(path, encoding="utf-8"))
    year, month = map(int, data["mes"].split("-"))
    first = date(year, month, 1)
    after = date(year + month // 12, month % 12 + 1, 1)
    quien = _ALT.get("nino", "Alternativo")
    out = {}
    for d in data["dias"]:
        day = date(year, month, d["dia"])
        # Lo que cambia en el menú alternativo: segundo plato y, a veces, también el primero
        alt = " · ".join(x for x in (d.get("alternativo_primero"), d.get("alternativo")) if x)
        title = f"{d['primero']} · {d['segundo']}" + (f" ({quien}: {alt})" if alt else "")
        desc = [f"1.º {d['primero']}", f"2.º {d['segundo']}"]
        if alt:
            desc.append(f"{quien}" + (f" ({_ALT['descripcion']})" if _ALT.get("descripcion") else "") + f": {alt}")
        if d.get("extras"):
            desc.append(d["extras"])
        if d.get("nota"):
            desc.append(d["nota"])
        eid = f"{PREFIX}{day:%Y%m%d}"
        out[eid] = {"id": eid, "summary": title, "description": "\n".join(desc),
                    "start": {"date": day.isoformat()}, "end": {"date": (day + timedelta(days=1)).isoformat()},
                    "transparency": "transparent", "extendedProperties": {"private": {"origen": "menu"}}}
    return first, after, out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("entrada")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    first, after, wanted = bodies(a.entrada)
    cal = gcal.calendario(_MENU.get("calendario", "menu_cole"))
    tok = gcal.token()
    q = {"privateExtendedProperty": "origen=menu", "timeMin": f"{first}T00:00:00Z", "timeMax": f"{after}T00:00:00Z",
         "maxResults": 2500, "showDeleted": "true", "singleEvents": "true"}
    code, res = gcal.api(tok, f"/calendars/{cal}/events?" + urllib.parse.urlencode(q))
    if code != 200:
        sys.exit(f"ERROR listando eventos: HTTP {code} {res}")
    existing = {e["id"]: e for e in res.get("items", [])}

    created = updated = unchanged = deleted = 0
    for eid, b in wanted.items():
        e = existing.get(eid)
        if e and e.get("status") != "cancelled" and e.get("summary") == b["summary"] \
                and e.get("description") == b["description"] and e["start"].get("date") == b["start"]["date"]:
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
    print(f"{_MENU.get('nombre_calendario', 'Menú del cole')} {first:%Y-%m}{tag}: {created} creados, {updated} actualizados, "
          f"{unchanged} sin cambios, {deleted} borrados ({len(wanted)} días en el fichero)")


if __name__ == "__main__":
    main()

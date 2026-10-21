#!/usr/bin/env python3
"""Sincroniza los turnos de un fichero .Shifter con el calendario de turnos de Google (el de la persona con turnos).

Cada turno lleva un ID de evento fijo (`shifter` + AAAAMMDD + franja), así que repetir la carga actualiza en vez de
duplicar. Desde `--desde`, los turnos que ya no están en el fichero se borran del calendario; solo se tocan
eventos creados por este script (los que empiezan por `shifter`), nunca los que se añadan a mano.

De config/familia.json: «turnos.calendario» (clave en calendarios.properties), «turnos.nombre_calendario» y
«turnos.color_id_google».

Uso: python3 -I scripts/shifter_sync.py <fichero.Shifter> [--desde AAAA-MM-DD] [--dry-run]
"""
from __future__ import annotations

import argparse
import os
import sys
import urllib.parse
from datetime import date, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import familia  # noqa: E402
import gcal  # noqa: E402
from shifter2ics import TZ, turnos  # noqa: E402

PREFIX = "shifter"
_TURNOS = familia.config().get("turnos", {})

# Google solo admite 11 colores fijos por evento (colorId): el más parecido al de cada turno en Shifter
# (1 Lavanda, 2 Salvia, 3 Uva, 4 Flamenco, 5 Plátano, 6 Mandarina, 7 Pavo real, 8 Grafito, 9 Arándano, 10 Albahaca,
# 11 Tomate). El marco no depende de esto: pinta el color exacto de Shifter.
COLOR_ID = {k: v for k, v in _TURNOS.get("color_id_google", {}).items() if not k.startswith("_")}


def body(t: dict) -> dict:
    if t["all_day"]:
        start, end = {"date": t["start"].isoformat()}, {"date": t["end"].isoformat()}
    else:
        start = {"dateTime": t["start"].isoformat(timespec="seconds"), "timeZone": TZ}
        end = {"dateTime": t["end"].isoformat(timespec="seconds"), "timeZone": TZ}
    b = {"id": f"{PREFIX}{t['dia']:%Y%m%d}s{t['slot']}", "summary": t["texto"], "start": start, "end": end,
            "transparency": "transparent" if t["all_day"] else "opaque",
            "extendedProperties": {"private": {"origen": "shifter"}}}
    if t["texto"] in COLOR_ID:
        b["colorId"] = COLOR_ID[t["texto"]]
    return b


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("entrada")
    ap.add_argument("--desde", type=date.fromisoformat, default=date.today() - timedelta(days=31))
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()

    wanted = {b["id"]: b for b in map(body, turnos(a.entrada, a.desde))}
    cal = gcal.calendario(_TURNOS.get("calendario", "turnos"))
    tok = gcal.token()

    # Eventos del script ya existentes desde --desde (también los cancelados, para poder reutilizar su ID).
    existing, page = {}, None
    while True:
        q = {"privateExtendedProperty": "origen=shifter", "timeMin": f"{a.desde}T00:00:00Z", "maxResults": 2500,
             "showDeleted": "true", "singleEvents": "true"}
        if page:
            q["pageToken"] = page
        code, res = gcal.api(tok, f"/calendars/{cal}/events?" + urllib.parse.urlencode(q))
        if code != 200:
            sys.exit(f"ERROR listando eventos: HTTP {code} {res}")
        for e in res.get("items", []):
            existing[e["id"]] = e
        page = res.get("nextPageToken")
        if not page:
            break

    def same(e: dict, b: dict) -> bool:
        return (e.get("status") != "cancelled" and e.get("summary") == b["summary"]
                and e.get("colorId") == b.get("colorId")
                and e["start"].get("date") == b["start"].get("date")
                and e["end"].get("date") == b["end"].get("date")
                and (e["start"].get("dateTime") or "")[:19] == (b["start"].get("dateTime") or "")[:19]
                and (e["end"].get("dateTime") or "")[:19] == (b["end"].get("dateTime") or "")[:19])

    created = updated = unchanged = deleted = 0
    for eid, b in wanted.items():
        e = existing.get(eid)
        if e and same(e, b):
            unchanged += 1
            continue
        if a.dry_run:
            created += e is None
            updated += e is not None
            continue
        if e is None:
            code, res = gcal.api(tok, f"/calendars/{cal}/events", "POST", b)
            if code == 409:  # existía fuera del rango listado: se actualiza
                code, res = gcal.api(tok, f"/calendars/{cal}/events/{eid}", "PUT", {**b, "status": "confirmed"})
                updated += 1
            else:
                created += 1
        else:
            code, res = gcal.api(tok, f"/calendars/{cal}/events/{eid}", "PUT", {**b, "status": "confirmed"})
            updated += 1
        if code not in (200, 201):
            sys.exit(f"ERROR en {eid}: HTTP {code} {res}")

    for eid, e in existing.items():
        if eid in wanted or e.get("status") == "cancelled" or not eid.startswith(PREFIX):
            continue
        if not a.dry_run:
            code, res = gcal.api(tok, f"/calendars/{cal}/events/{eid}", "DELETE")
            if code not in (200, 204, 410):
                sys.exit(f"ERROR borrando {eid}: HTTP {code} {res}")
        deleted += 1

    tag = " (simulación)" if a.dry_run else ""
    print(f"{_TURNOS.get('nombre_calendario', 'Turnos')} desde {a.desde}{tag}: {created} creados, {updated} actualizados, "
          f"{unchanged} sin cambios, {deleted} borrados ({len(wanted)} en el fichero)")


if __name__ == "__main__":
    main()

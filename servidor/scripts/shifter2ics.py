#!/usr/bin/env python3
"""Convierte un fichero .Shifter (export de «Calendario de Turnos (Shifter)», versión gratuita) en un .ics.

El .Shifter es una base SQLite: `tablaTurnos` (tipos de turno con horario) y `dias` (un día por fila:
`fecha` = AAAA*10000 + MES0*100 + DÍA, con el mes empezando en 0, y `turno1`/`turno2` = id de tablaTurnos).
Las notas de cada día no se exportan.

De config/familia.json: «zona_horaria», «turnos.nombre_calendario» y «turnos.dia_completo».

Uso: python3 -I scripts/shifter2ics.py <fichero.Shifter> <salida.ics> [--desde AAAA-MM-DD]
"""
from __future__ import annotations

import argparse
import hashlib
import os
import sqlite3
import sys
from datetime import date, datetime, timedelta

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import familia  # noqa: E402

TZ = familia.zona()
_TURNOS = familia.config().get("turnos", {})
# Turnos que no son horas de trabajo: se exportan como evento de día completo.
ALL_DAY = set(_TURNOS.get("dia_completo", ["Saliente", "Día libre", "Cumpleaños", "Vacaciones"]))


def decode(fecha: int) -> date:
    return date(fecha // 10000, (fecha // 100) % 100 + 1, fecha % 100)


def hm(s: str) -> tuple[int, int]:
    h, m = s.split(":")
    return int(h), int(m)


def esc(s: str) -> str:
    return s.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")


def turnos(path: str, desde: date) -> list[dict]:
    """Turnos desde `desde`: {dia, slot, texto, color, all_day, start, end} (start/end: date si all_day, si no
    datetime local; color: el de Shifter en #RRGGBB)."""
    db = sqlite3.connect(f"file:{path}?mode=ro&immutable=1", uri=True)
    tipos = {r[0]: r[1:] for r in db.execute("select _id, texto, horaInicio1, horaFinal1 from tablaTurnos")}
    colores = {r[0]: "#%06X" % (r[1] & 0xFFFFFF) for r in db.execute("select _id, color from tablaTurnos")}
    out = []
    for fecha, t1, t2 in db.execute("select fecha, turno1, turno2 from dias order by fecha"):
        dia = decode(fecha)
        if dia < desde:
            continue
        for slot, tid in ((1, t1), (2, t2)):
            if not tid or tid not in tipos:
                continue
            texto, ini, fin = tipos[tid]
            color = colores[tid]
            if texto in ALL_DAY or ini == fin:
                out.append(dict(dia=dia, slot=slot, texto=texto, color=color, all_day=True, start=dia,
                                end=dia + timedelta(days=1)))
                continue
            start = datetime.combine(dia, datetime.min.time()).replace(hour=hm(ini)[0], minute=hm(ini)[1])
            end = datetime.combine(dia, datetime.min.time()).replace(hour=hm(fin)[0], minute=hm(fin)[1])
            if end <= start:  # noches y turnos de 24 h acaban al día siguiente
                end += timedelta(days=1)
            out.append(dict(dia=dia, slot=slot, texto=texto, color=color, all_day=False, start=start, end=end))
    db.close()
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("entrada")
    ap.add_argument("salida")
    ap.add_argument("--desde", type=date.fromisoformat, default=date.today() - timedelta(days=31))
    a = ap.parse_args()

    stamp = datetime.utcnow().strftime("%Y%m%dT%H%M%SZ")
    out = ["BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//marco-familiar//shifter2ics//ES",
           "CALSCALE:GREGORIAN", f"X-WR-CALNAME:{esc(_TURNOS.get('nombre_calendario', 'Turnos'))}",
           f"X-WR-TIMEZONE:{TZ}"]
    ts = turnos(a.entrada, a.desde)
    for t in ts:
        uid = hashlib.sha1(f"{t['dia']}-{t['slot']}".encode()).hexdigest() + "@marco-familiar"
        ev = ["BEGIN:VEVENT", f"UID:{uid}", f"DTSTAMP:{stamp}", f"SUMMARY:{esc(t['texto'])}"]
        if t["all_day"]:
            ev += [f"DTSTART;VALUE=DATE:{t['start']:%Y%m%d}", f"DTEND;VALUE=DATE:{t['end']:%Y%m%d}", "TRANSP:TRANSPARENT"]
        else:
            ev += [f"DTSTART;TZID={TZ}:{t['start']:%Y%m%dT%H%M%S}", f"DTEND;TZID={TZ}:{t['end']:%Y%m%dT%H%M%S}"]
        out += ev + ["END:VEVENT"]
    out.append("END:VCALENDAR")
    with open(a.salida, "w", newline="") as f:
        f.write("\r\n".join(out) + "\r\n")
    print(f"{len(ts)} turnos desde {a.desde} → {a.salida}")


if __name__ == "__main__":
    main()

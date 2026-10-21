#!/usr/bin/env python3
"""Acceso a los calendarios de la familia con la cuenta de servicio de secrets/ (solo biblioteca estándar + openssl).

En la carpeta «rutas.secretos» de config/familia.json tiene que haber:
- la clave JSON de la cuenta de servicio de Google (el único *.json de la carpeta);
- `calendarios.properties`, con `<clave>.ics` (dirección secreta iCal) y `<clave>.id` (ID del calendario) de cada
  calendario de «calendarios» en la configuración (ver calendarios.properties.example en el README).

Uso: python3 -I scripts/gcal.py check   → comprueba iCal y acceso de la cuenta de servicio, sin mostrar secretos.
"""
from __future__ import annotations

import base64
import glob
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import familia  # noqa: E402


def secretos() -> str:
    return familia.ruta("secretos", "secrets")


def nombres() -> list[str]:
    """Claves de los calendarios de la configuración (sin las «_nota»)."""
    return [k for k in familia.config().get("calendarios", {}) if not k.startswith("_")]


def props() -> dict:
    out = {}
    with open(os.path.join(secretos(), "calendarios.properties")) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                out[k.strip()] = v.strip()
    return out


def b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def token(scope: str = "https://www.googleapis.com/auth/calendar.events") -> str:
    key = json.load(open(glob.glob(os.path.join(secretos(), "*.json"))[0]))
    now = int(time.time())
    head = b64(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    claim = b64(json.dumps({"iss": key["client_email"], "scope": scope, "aud": key["token_uri"],
                            "iat": now, "exp": now + 3600}).encode())
    unsigned = f"{head}.{claim}".encode()
    # La clave privada pasa a openssl por un descriptor, nunca a disco ni a la línea de comandos.
    r, w = os.pipe()
    os.write(w, key["private_key"].encode())
    os.close(w)
    sig = subprocess.run(["openssl", "dgst", "-sha256", "-sign", f"/dev/fd/{r}"], input=unsigned,
                         capture_output=True, check=True, pass_fds=(r,)).stdout
    os.close(r)
    body = urllib.parse.urlencode({"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                                   "assertion": f"{unsigned.decode()}.{b64(sig)}"}).encode()
    return json.load(urllib.request.urlopen(key["token_uri"], body, timeout=20))["access_token"]


def api(tok: str, path: str, method: str = "GET", data: dict | None = None) -> tuple[int, dict]:
    req = urllib.request.Request("https://www.googleapis.com/calendar/v3" + path, method=method,
                                 data=json.dumps(data).encode() if data is not None else None,
                                 headers={"Authorization": f"Bearer {tok}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            raw = resp.read()
            return resp.status, json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        return e.code, {"error": e.read().decode()[:200]}


def calendario(clave: str) -> str:
    """ID del calendario <clave> (de calendarios.properties), ya escapado para la URL de la API."""
    p = props()
    if not p.get(f"{clave}.id"):
        raise SystemExit(f"ERROR: falta «{clave}.id» en calendarios.properties")
    return urllib.parse.quote(p[f"{clave}.id"], safe="")


def check() -> None:
    p = props()
    for n in nombres():
        if not p.get(f"{n}.ics"):
            continue  # calendario opcional sin dirección todavía
        try:
            with urllib.request.urlopen(p[f"{n}.ics"], timeout=20) as resp:
                ics = resp.read().decode("utf-8", "replace")
            print(f"iCal {n:13} HTTP {resp.status}, {ics.count('BEGIN:VEVENT')} eventos")
        except Exception as e:  # noqa: BLE001
            print(f"iCal {n:13} ERROR {type(e).__name__}")
    tok = token()
    for n in nombres():
        if not p.get(f"{n}.id"):
            continue  # solo lectura por iCal: los scripts no escriben en él
        cid = urllib.parse.quote(p[f"{n}.id"], safe="")
        code, body = api(tok, f"/calendars/{cid}/events?maxResults=1")
        print(f"API  {n:13} HTTP {code}" + ("" if code == 200 else " → no compartido con la cuenta de servicio o sin permiso"))


if __name__ == "__main__":
    {"check": check}[sys.argv[1] if len(sys.argv) > 1 else "check"]()

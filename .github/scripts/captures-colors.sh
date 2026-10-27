#!/usr/bin/env bash
# Captures de calendaris ficticis exclusivament; mai cap URL de l'usuari.
set -euo pipefail
mkdir -p captures

adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell wm size 1920x1200
adb shell wm density 160
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1

capture() {
  local name="$1"
  adb shell screencap -p /sdcard/marc-familiar-preview.png
  adb pull /sdcard/marc-familiar-preview.png "captures/$name" >/dev/null
  test -s "captures/$name"
  file "captures/$name"
}

# Prova visual dels colors AMB DADES FICTÍCIES. No s'hi posa cap URL privada real.
# Només es modifica la configuració de l'emulador (APK debug) amb run-as.
adb shell am force-stop com.lito.planify.dashboard
python3 - <<'PY'
import json
from xml.sax.saxutils import escape
calendars = [
    {"id": "demo-rai", "name": "Rai", "url": "https://calendar.google.com/calendar/ical/demo-rai/private-1/basic.ics", "color": "#36A070"},
    {"id": "demo-familia", "name": "Família", "url": "https://calendar.google.com/calendar/ical/demo-familia/private-2/basic.ics", "color": "#3977CB"}
]
data = escape(json.dumps(calendars))
with open("/tmp/marc-calendar-colors.xml", "w", encoding="utf-8") as f:
    f.write('<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n')
    f.write('<string name="calendar_feeds">' + data + '</string>\n</map>\n')
PY
adb shell run-as com.lito.planify.dashboard mkdir -p /data/user/0/com.lito.planify.dashboard/shared_prefs
# Copiem primer a una ruta temporal llegible i després al directori privat
# amb cp sota run-as (evita la redirecció de l'intèrpret del sistema).
adb push /tmp/marc-calendar-colors.xml /data/local/tmp/marc-calendar-colors.xml >/dev/null
adb shell run-as com.lito.planify.dashboard cp /data/local/tmp/marc-calendar-colors.xml /data/user/0/com.lito.planify.dashboard/shared_prefs/dashboard.xml
adb shell rm /data/local/tmp/marc-calendar-colors.xml
adb shell am start -W -n com.lito.planify.dashboard/.MainActivity
sleep 12
capture "14-llegenda-colors-avui.png"
adb shell input tap 1850 302
sleep 3
capture "15-llegenda-colors-calendari.png"
adb shell input tap 1850 718
sleep 3
adb shell uiautomator dump /sdcard/marc-colors-options.xml >/dev/null
adb pull /sdcard/marc-colors-options.xml /tmp/marc-colors-options.xml >/dev/null
calendar_coords=$(python3 - <<'PY'
import re
import xml.etree.ElementTree as ET
root = ET.parse("/tmp/marc-colors-options.xml").getroot()
for node in root.iter("node"):
    if node.attrib.get("text") == "Calendaris de Google":
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
        print((x1+x2)//2, (y1+y2)//2)
        break
PY
)
if [ -z "$calendar_coords" ]; then
  echo "No s'ha trobat Calendaris de Google" >&2
  exit 1
fi
adb shell input tap $calendar_coords
sleep 3
capture "16-calendaris-amb-colors.png"
adb shell uiautomator dump /sdcard/marc-color-buttons.xml >/dev/null
adb pull /sdcard/marc-color-buttons.xml /tmp/marc-color-buttons.xml >/dev/null
color_coords=$(python3 - <<'PY'
import re
import xml.etree.ElementTree as ET
root = ET.parse("/tmp/marc-color-buttons.xml").getroot()
for node in root.iter("node"):
    if node.attrib.get("text") == "Color":
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
        print((x1+x2)//2, (y1+y2)//2)
        break
PY
)
if [ -z "$color_coords" ]; then
  echo "No s'ha trobat el selector de color" >&2
  exit 1
fi
adb shell input tap $color_coords
sleep 3
capture "17-selector-color-calendari.png"

# Canvia el color de «Rai» per «Taronja» i comprova que queda guardat al mateix ID.
adb shell uiautomator dump /sdcard/marc-palette.xml >/dev/null
adb pull /sdcard/marc-palette.xml /tmp/marc-palette.xml >/dev/null
orange_coords=$(python3 - <<'PY'
import re
import xml.etree.ElementTree as ET
root = ET.parse("/tmp/marc-palette.xml").getroot()
for node in root.iter("node"):
    if node.attrib.get("text") == "Taronja":
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.attrib["bounds"]))
        print((x1+x2)//2, (y1+y2)//2)
        break
PY
)
if [ -z "$orange_coords" ]; then
  echo "No s'ha trobat el color Taronja" >&2
  exit 1
fi
adb shell input tap $orange_coords
sleep 4
adb shell run-as com.lito.planify.dashboard cat /data/user/0/com.lito.planify.dashboard/shared_prefs/dashboard.xml > /tmp/marc-colors-after.xml
grep -q '#E09132' /tmp/marc-colors-after.xml
grep -q '#3977CB' /tmp/marc-colors-after.xml
capture "18-color-calendari-actualitzat.png"

# Després de reiniciar, el canvi ha de persistir i la llegenda l'ha de reflectir.
adb shell am force-stop com.lito.planify.dashboard
adb shell am start -W -n com.lito.planify.dashboard/.MainActivity
sleep 10
capture "19-color-persistent-portada.png"

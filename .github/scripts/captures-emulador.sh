#!/usr/bin/env bash
# Captures REALS de la versió Android sense dades personals, dins d'un emulador.
set -euo pipefail
mkdir -p captures

adb install -r android/app/build/outputs/apk/debug/app-debug.apk
# L'aplicació està dissenyada per al panell horitzontal del marc XORO.
adb shell wm size 1920x1200
adb shell wm density 160
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1

adb shell am start -W -n com.lito.planify.dashboard/.MainActivity
sleep 12
# La primera obertura mostra un avís nadiu de l'emulador sobre la pantalla completa.
# El tanquem abans de capturar la UI. Si no hi és, aquesta pulsació no canvia la portada.
adb shell input tap 1065 202
sleep 3

capture() {
  local name="$1"
  adb shell screencap -p /sdcard/marc-familiar-preview.png
  adb pull /sdcard/marc-familiar-preview.png "captures/$name" >/dev/null
  test -s "captures/$name"
  file "captures/$name"
}

# Els botons del rail estan a la dreta del panell (1920 x 1200):
# Avui, Fotos, Calendari, El temps, Tasques, Actualitzar, Opcions.
capture "01-avui.png"
adb shell input tap 1850 198
sleep 3
capture "02-fotografies.png"
adb shell input tap 1850 302
sleep 3
capture "03-calendari.png"
adb shell input tap 1850 406
sleep 3
capture "04-el-temps.png"
adb shell input tap 1850 718
sleep 3
capture "05-opcions.png"
# Torna a «Avui» i obre l'editor de sopars de la targeta inferior dreta.
adb shell input tap 1850 90
sleep 2
adb shell input tap 1140 1000
sleep 2
capture "06-editor-sopar.png"

# Vista de la compra, les feines i les recompenses (sense cap dada familiar real).
adb shell input keyevent KEYCODE_BACK
sleep 2
adb shell input tap 1850 504
sleep 4
capture "07-familia-compra-tasques.png"

# Prova funcional sense dades privades: afegir un producte de mostra.
adb shell input tap 95 309
sleep 3
capture "08-compra-amb-producte.png"

# Comprovem que continua guardat en reiniciar completament l'aplicació.
adb shell am force-stop com.lito.planify.dashboard
adb shell am start -W -n com.lito.planify.dashboard/.MainActivity
sleep 9
adb shell input tap 1850 500
sleep 3
capture "09-compra-despres-reinici.png"

# Una feina d'exemple obre la primera configuració del PIN dels adults.
adb shell input tap 950 336
sleep 3
capture "10-pin-dels-adults.png"

# Configuració del temps inicial i pantalla nova de Google Calendar.
adb shell input keyevent KEYCODE_BACK
sleep 2
adb shell input tap 1850 726
sleep 3
capture "11-opcions-malgrat.png"

# Busquem l'etiqueta tàctil per accessibilitat, sense dependre de la posició dels botons.
adb shell uiautomator dump /sdcard/marc-calendars.xml >/dev/null
adb pull /sdcard/marc-calendars.xml /tmp/marc-calendars.xml >/dev/null
coords=$(python3 - <<'PY'
import re
import xml.etree.ElementTree as ET
root = ET.parse("/tmp/marc-calendars.xml").getroot()
for node in root.iter("node"):
    if node.attrib.get("text") == "Calendaris de Google":
        x1,y1,x2,y2 = map(int,re.findall(r"\d+",node.attrib["bounds"]))
        print((x1+x2)//2, (y1+y2)//2)
        break
PY
)
if [ -z "$coords" ]; then
  echo "No s'ha trobat el botó de Calendaris de Google a l'UI" >&2
  exit 1
fi
adb shell input tap $coords
sleep 3
capture "12-configuracio-calendaris.png"

# Configuració compartida, sense clau ni servidor real en aquest emulador.
adb shell input tap 385 1127
sleep 3
adb shell uiautomator dump /sdcard/marc-family-sync.xml >/dev/null
adb pull /sdcard/marc-family-sync.xml /tmp/marc-family-sync.xml >/dev/null
sync_coords=$(python3 - <<'PY'
import re
import xml.etree.ElementTree as ET
root=ET.parse("/tmp/marc-family-sync.xml").getroot()
for node in root.iter("node"):
    if node.attrib.get("text") == "Sincronització familiar":
        x1,y1,x2,y2 = map(int,re.findall(r"\d+",node.attrib["bounds"]))
        print((x1+x2)//2,(y1+y2)//2)
        break
PY
)
if [ -z "$sync_coords" ]; then
  echo "No hi ha el botó de Sincronització familiar" >&2
  exit 1
fi
adb shell input tap $sync_coords
sleep 3
capture "13-sincronitzacio-familiar.png"

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
adb shell -T run-as com.lito.planify.dashboard sh -c 'cat > /data/user/0/com.lito.planify.dashboard/shared_prefs/dashboard.xml' < /tmp/marc-calendar-colors.xml
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

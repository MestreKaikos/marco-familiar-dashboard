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

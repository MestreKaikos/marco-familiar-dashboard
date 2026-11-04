#!/usr/bin/env bash
# Només emulador CI: preferències fictícies, cap dada privada ni xarxa nova.
set -euo pipefail
package=com.lito.planify.dashboard.testqr
activity=com.lito.planify.dashboard.MainActivity
adb shell am force-stop "$package"
adb shell run-as "$package" cat "/data/user/0/$package/shared_prefs/dashboard.xml" > /tmp/night-dashboard-original.xml
python3 - <<'PY'
import json,xml.etree.ElementTree as ET
p='/tmp/night-dashboard-original.xml'
tree=ET.parse(p); root=tree.getroot()
for n in list(root):
    if n.attrib.get('name')=='schedule': root.remove(n)
n=ET.SubElement(root,'string',name='schedule')
n.text=json.dumps(dict(enabled=True,screenOff=False,weekdayOn=0,weekdayOff=1439,weekendOn=0,weekendOff=1439))
tree.write('/tmp/night-dashboard.xml',encoding='unicode')
PY
adb push /tmp/night-dashboard.xml /data/local/tmp/night-dashboard.xml >/dev/null
adb shell run-as "$package" cp /data/local/tmp/night-dashboard.xml "/data/user/0/$package/shared_prefs/dashboard.xml"
tap_text() {
  local label="$1" coords
  for attempt in 1 2 3 4; do
    adb shell uiautomator dump /sdcard/night-ui.xml >/dev/null
    adb pull /sdcard/night-ui.xml /tmp/night-ui.xml >/dev/null
    coords=$(python3 - "$label" <<'PY'
import sys,re,xml.etree.ElementTree as ET
for n in ET.parse('/tmp/night-ui.xml').getroot().iter('node'):
    if n.attrib.get('text')==sys.argv[1]:
        x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
        if x2>x1 and y2>y1:
            print((x1+x2)//2,(y1+y2)//2); break
PY
)
    if [ -n "$coords" ]; then adb shell input tap $coords; sleep 2; return; fi
    adb shell input swipe 900 1050 900 380 500
    sleep 1
  done
  echo "No es troba: $label" >&2; return 1
}
capture_night() {
  adb shell screencap -p /sdcard/night.png
  adb pull /sdcard/night.png "captures/$1" >/dev/null
}
adb shell am start -W -n "$package/$activity"
sleep 8
tap_text 'Opcions'
tap_text 'Mode nocturn'
capture_night '25-mode-nocturn-opcions.png'
tap_text 'Apilat'
tap_text 'Clàssica'
tap_text 'Negreta'
# Comprova els valors que s'han desat des de controls reals de la pantalla.
adb shell run-as "$package" cat "/data/user/0/$package/shared_prefs/marc_night_clock.xml" > /tmp/night-saved.xml
python3 - <<'PY'
import xml.etree.ElementTree as ET
prefs={n.attrib['name']:n.text for n in ET.parse('/tmp/night-saved.xml').getroot()}
assert prefs['layout']=='stacked' and prefs['font']=='classic' and prefs['weight']=='bold', prefs
PY
# Tornar a dalt i provar la pantalla completa.
adb shell input swipe 900 300 900 1050 500
sleep 1
tap_text 'Provar a pantalla completa'
capture_night '26-rellotge-apilat.png'
adb shell input tap 960 600
sleep 2
# El toc de sortida retorna als ajustos, sense activar res al darrere.
adb shell uiautomator dump /sdcard/night-return.xml >/dev/null
adb pull /sdcard/night-return.xml /tmp/night-return.xml >/dev/null
python3 - <<'PY'
import xml.etree.ElementTree as ET
text='\n'.join(n.attrib.get('text','') for n in ET.parse('/tmp/night-return.xml').getroot().iter('node'))
assert 'Mode nocturn' in text and 'Provar a pantalla completa' in text
PY
tap_text 'Analògic'
tap_text 'Provar a pantalla completa'
capture_night '27-rellotge-analogic.png'
adb shell input keyevent KEYCODE_BACK
sleep 2
tap_text 'Digital'
tap_text 'Moderna'
tap_text 'Fi'
tap_text '← Tornar a Opcions'
# Activació real amb la lluna i comprovació del toc de retorn a Avui.
tap_text 'Dormir'
capture_night '28-rellotge-nocturn-real.png'
adb shell uiautomator dump /sdcard/night-real.xml >/dev/null
adb pull /sdcard/night-real.xml /tmp/night-real.xml >/dev/null
python3 - <<'PYTEST'
import re,xml.etree.ElementTree as ET
text='\n'.join(n.attrib.get('text','') for n in ET.parse('/tmp/night-real.xml').getroot().iter('node'))
assert re.search(r'\d{2}:\d{2}', text), 'El rellotge digital ha de mostrar l’hora'
assert 'Opcions' not in text and 'Compra' not in text, 'La nit ha d’ocultar les targetes i el menú'
PYTEST
adb shell input tap 300 1000
sleep 3
adb shell uiautomator dump /sdcard/night-wake.xml >/dev/null
adb pull /sdcard/night-wake.xml /tmp/night-wake.xml >/dev/null
python3 - <<'PY'
import xml.etree.ElementTree as ET
text='\n'.join(n.attrib.get('text','') for n in ET.parse('/tmp/night-wake.xml').getroot().iter('node'))
assert 'Compra' in text and 'Opcions' in text, 'El toc nocturn ha de retornar a Avui'
print('OK · Rellotges digital, apilat i analògic; preferències persistents, prova completa i retorn a Avui.')
PY
adb shell am force-stop "$package"
adb push /tmp/night-dashboard-original.xml /data/local/tmp/night-dashboard-original.xml >/dev/null
adb shell run-as "$package" cp /data/local/tmp/night-dashboard-original.xml "/data/user/0/$package/shared_prefs/dashboard.xml"

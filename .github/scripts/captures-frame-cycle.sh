#!/usr/bin/env bash
# Només l'emulador de CI: paisatge generat i preferències fictícies, sense xarxa ni claus.
set -euo pipefail
package=com.lito.planify.dashboard.testqr
activity=com.lito.planify.dashboard.MainActivity
adb shell am force-stop "$package"
adb shell run-as "$package" mkdir -p "/data/user/0/$package/files/marc_fotos_compartides"
python3 - <<'PY'
import json,struct,zlib,time
from xml.sax.saxutils import escape
# Una imatge de prova generada, llegible per BitmapFactory, sense fotos de la família.
w,h=960,600
raw=b''.join(b'\0'+bytes((40+y*80//h,90+y*80//h,130+y*70//h))*w for y in range(h))
def chunk(tag,data):
    return struct.pack('!I',len(data))+tag+data+struct.pack('!I',zlib.crc32(tag+data)&0xffffffff)
with open('/tmp/frame-photo.png','wb') as f:
    f.write(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',w,h,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b''))
index=escape(json.dumps([{'id':'demo-cycle','name':'Paisatge de prova'}]))
with open('/tmp/frame-photo-cache.xml','w') as f:
    f.write('<map><string name="index">'+index+'</string><long name="last_photo_check_epoch" value="'+str(int(time.time()*1000))+'"/></map>')
with open('/tmp/frame-mode.xml','w') as f:
    f.write('<map><boolean name="enabled" value="true"/><int name="idle_minutes" value="1"/><int name="photo_minutes" value="5"/><int name="agenda_seconds" value="15"/><int name="slide_seconds" value="10"/></map>')
PY
adb push /tmp/frame-photo.png /data/local/tmp/frame-photo.png >/dev/null
adb shell run-as "$package" cp /data/local/tmp/frame-photo.png "/data/user/0/$package/files/marc_fotos_compartides/demo-cycle.jpg"
for entry in 'frame-photo-cache.xml:marc_cached_photos.xml' 'frame-mode.xml:marc_frame_mode.xml'; do
  source_file=${entry%%:*}
  destination_file=${entry#*:}
  adb push "/tmp/$source_file" "/data/local/tmp/$source_file" >/dev/null
  adb shell run-as "$package" cp "/data/local/tmp/$source_file" "/data/user/0/$package/shared_prefs/$destination_file"
done
adb shell am start -W -n "$package/$activity"
sleep 8
# Obre els ajustos utilitzant les etiquetes accessibles.
tap_text() {
  local label="$1"
  adb shell uiautomator dump /sdcard/frame-cycle-ui.xml >/dev/null
  adb pull /sdcard/frame-cycle-ui.xml /tmp/frame-cycle-ui.xml >/dev/null
  local coords
  coords=$(python3 - "$label" <<'PY'
import sys,re,xml.etree.ElementTree as ET
for n in ET.parse('/tmp/frame-cycle-ui.xml').getroot().iter('node'):
    if n.attrib.get('text')==sys.argv[1]:
        x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
        print((x1+x2)//2,(y1+y2)//2);break
PY
)
  test -n "$coords"
  adb shell input tap $coords
  sleep 2
}
tap_text 'Opcions'
tap_text 'Fotos i agenda'
adb shell screencap -p /sdcard/frame-settings.png
adb pull /sdcard/frame-settings.png captures/22-fotos-agenda-opcions.png >/dev/null
# Tornar a Avui i deixar passar el minut real d'inactivitat.
adb shell input keyevent KEYCODE_BACK
sleep 65
adb shell screencap -p /sdcard/frame-auto.png
adb pull /sdcard/frame-auto.png captures/23-fotos-automatiques.png >/dev/null
adb shell uiautomator dump /sdcard/frame-auto.xml >/dev/null
adb pull /sdcard/frame-auto.xml /tmp/frame-auto.xml >/dev/null
python3 - <<'PY'
import xml.etree.ElementTree as ET
text='\n'.join(n.attrib.get('text','') for n in ET.parse('/tmp/frame-auto.xml').getroot().iter('node'))
assert 'Toca per veure Avui' in text, 'No s’ha activat la presentació automàtica'
assert 'Opcions' not in text, 'La barra lateral ha de quedar oculta durant les fotos automàtiques'
PY
# El toc cau damunt d'una targeta de la portada: l'up no ha d'obrir-la accidentalment.
adb shell input tap 300 1000
sleep 3
adb shell uiautomator dump /sdcard/frame-touch.xml >/dev/null
adb pull /sdcard/frame-touch.xml /tmp/frame-touch.xml >/dev/null
python3 - <<'PY'
import xml.etree.ElementTree as ET
text='\n'.join(n.attrib.get('text','') for n in ET.parse('/tmp/frame-touch.xml').getroot().iter('node'))
assert 'Calendaris per configurar' in text and 'Compra' in text, 'El toc ha de tornar a Avui sense obrir cap targeta'
assert 'Toca per veure Avui' not in text
print('OK · Fotos automàtiques a pantalla completa i retorn a Avui amb un toc consumit.')
PY
adb shell screencap -p /sdcard/frame-today.png
adb pull /sdcard/frame-today.png captures/24-toc-retorna-avui.png >/dev/null
# Restaurar el mode desactivat abans de les altres proves de la mateixa sessió.
adb shell am force-stop "$package"
printf '<map><boolean name="enabled" value="false"/></map>' > /tmp/frame-mode-off.xml
adb push /tmp/frame-mode-off.xml /data/local/tmp/frame-mode-off.xml >/dev/null
adb shell run-as "$package" cp /data/local/tmp/frame-mode-off.xml "/data/user/0/$package/shared_prefs/marc_frame_mode.xml"

#!/usr/bin/env bash
# Només emulador de CI amb dades inventades: valida capacitat i captures de les targetes.
set -euo pipefail
package=com.lito.planify.dashboard.testqr
activity=com.lito.planify.dashboard.MainActivity
adb shell am force-stop "$package"
adb shell run-as "$package" mkdir -p "/data/user/0/$package/shared_prefs"
python3 - <<'PY'
import json,time
from xml.sax.saxutils import escape

def prefs(file,key,value,extra=''):
    with open('/tmp/'+file,'w',encoding='utf-8') as f:
        f.write('<map><string name="'+key+'">'+escape(json.dumps(value,ensure_ascii=False))+'</string>'+extra+'</map>')
family={
    'groceries':[{'id':'demo-product-'+str(i),'name':n,'bought':False} for i,n in enumerate(['Llet','Pomes','Pa','Ous'])],
    'chores':[{'id':'demo-task-'+str(i),'title':n,'owner':'Rai','stars':2,'daily':True,'frequency':'daily','status':'todo'} for i,n in enumerate(['Preparar la motxilla','Endreçar els llibres','Parar taula','Regar les plantes','Recollir les joguines'])],
    'stars':12
}
prefs('home-family.xml','data',family)
prefs('home-reminders.xml','items',[{'id':'demo-reminder-'+str(i),'title':n,'date':'','done':False} for i,n in enumerate(['Revisar la motxilla','Retornar llibres','Preparar material','Comprar paper','Recollir comanda','Revisar agenda'])])
prefs('home-dashboard.xml','calendar_feeds',[], '<long name="last_valid_time" value="'+str(int(time.time()*1000))+'"/>')
PY
for entry in 'home-family.xml:marc_familiar_local.xml' 'home-reminders.xml:marc_familiar_recordatoris.xml' 'home-dashboard.xml:dashboard.xml'; do
  source_file=${entry%%:*}
  destination_file=${entry#*:}
  adb push "/tmp/$source_file" "/data/local/tmp/$source_file" >/dev/null
  adb shell run-as "$package" cp "/data/local/tmp/$source_file" "/data/user/0/$package/shared_prefs/$destination_file"
done
adb shell am start -W -n "$package/$activity"
sleep 10
# Tancar l'avís d'Android 10 amb la seva etiqueta, si apareix.
adb shell uiautomator dump /sdcard/home-start.xml >/dev/null
adb pull /sdcard/home-start.xml /tmp/home-start.xml >/dev/null
coords=$(python3 - <<'PY'
import re,xml.etree.ElementTree as ET
for n in ET.parse('/tmp/home-start.xml').getroot().iter('node'):
    if n.attrib.get('text','').lower() in ('got it','entendido','d’acord','ok'):
        x1,y1,x2,y2=map(int,re.findall(r'\d+',n.attrib['bounds']))
        print((x1+x2)//2,(y1+y2)//2);break
PY
)
if [ -n "$coords" ]; then adb shell input tap $coords; sleep 2; fi
mkdir -p captures
adb shell screencap -p /sdcard/home-cards.png
adb pull /sdcard/home-cards.png captures/20-inici-targetes-completes.png >/dev/null
adb shell uiautomator dump /sdcard/home-cards.xml >/dev/null
adb pull /sdcard/home-cards.xml /tmp/home-cards.xml >/dev/null
# En el panell de 1920x1200 han de cabre els quatre productes complets.
python3 - <<'PY'
import re,xml.etree.ElementTree as ET
texts=[n.attrib.get('text','') for n in ET.parse('/tmp/home-cards.xml').getroot().iter('node')]
all_text='\n'.join(texts)
for product in ['Llet','Pomes','Pa','Ous']:
    assert re.search(r'\b'+re.escape(product)+r'\b',all_text), 'No es veu el producte de prova: '+product
print('OK · Els quatre productes es mostren a la portada.')
PY
adb shell settings put system font_scale 1.3
sleep 4
adb shell screencap -p /sdcard/home-cards-large.png
adb pull /sdcard/home-cards-large.png captures/21-inici-targetes-lletra-gran.png >/dev/null
adb shell settings put system font_scale 1.0

import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { Script } from 'node:vm';
import icon from '../api/icon.js';

const root=new URL('../',import.meta.url);
const html=fs.readFileSync(new URL('index.html',root),'utf8');
const manifest=JSON.parse(fs.readFileSync(new URL('manifest.webmanifest',root),'utf8'));
test('Manifest instal·lable i service worker sense cache de dades',()=>{
  assert.equal(manifest.display,'standalone');
  assert.equal(manifest.start_url,'/');
  assert.equal(manifest.icons.length,2);
  assert.ok(html.includes('window.MARC_PWA_MODE = true'));
  assert.ok(html.includes('rel="manifest"'));
  const sw=fs.readFileSync(new URL('sw.js',root),'utf8');
  assert.match(sw,/\/api\//);
  assert.match(sw,/request\.method!=='GET'/);
});
test('Conserva les seccions i formularis de producció',()=>{
  for(const tab of ['avui','compra','recordatoris','fotos','rai'])
    assert.ok(html.includes('data-tab="'+tab+'"'));
  for(const id of ['addProduct','addReminder','raiAddTask','raiAddReward','uploadPhotos','overviewReminders'])
    assert.ok(html.includes('id="'+id+'"'));
  assert.equal((html.match(/<section class="panel" data-tab="compra">/g)||[]).length,1);
  assert.equal((html.match(/<section class="panel" data-tab="recordatoris">/g)||[]).length,1);
  assert.ok(html.includes('data-open="compra"'));
  assert.ok(html.includes('data-open="recordatoris"'));
  assert.ok(html.includes('class="summary-card grocery-summary" type="button" data-go="compra"'));
  assert.ok(!html.includes('class="subpanel shopping-section"'));
  assert.equal((html.match(/id="addProduct"/g)||[]).length,1);
  const js=[...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].map(x=>x[1]);
  js.forEach(source=>assert.doesNotThrow(()=>new Script(source)));
});
test('Pujada de fotos grans i reintent sense perdre la selecció',()=>{
  assert.ok(html.includes('await uploadOriginal(file,await compressedPhotoForPwa(file),id)'));
  assert.ok(html.includes('pendingPhotoItems=files.map(file=>({file,id:newId()}))'));
  assert.ok(html.includes('pendingPhotoItems=failed'));
  assert.ok(html.includes('failed.push(item)'));
  assert.ok(html.includes('Reintentar '+ '${failed.length}' + ' pendents') ||
    html.includes("'Reintentar '+failed.length+' pendents'"));
  assert.ok(!html.includes('f.size>5*1024*1024'));
  assert.ok(!html.includes('Les fotos han de ser JPG, PNG o WebP de fins a 5 MB'));
});
test('Ajustos de punts dels pares amb PIN i botons petits',()=>{
  for(const id of ['raiAdultPoints','raiAdultPin','raiAdjustReason','raiAdjustStatus',
    'raiPointsHistory','raiResetPoints']) assert.ok(html.includes('id="'+id+'"'));
  for(const delta of ['1','2','5','10','-1','-2','-5','-10'])
    assert.ok(html.includes('data-points-delta="'+delta+'"'));
  assert.ok(html.includes("raiAction('points_adjust',payload"));
  assert.ok(html.includes("payload.reset=true"));
  assert.ok(html.includes("if(!/^[0-9]{4,8}$/.test(pin))"));
  assert.ok(html.includes("$('raiAdultPin').value=''"));
  const proxy=fs.readFileSync(new URL('api/relay.js',root),'utf8');
  assert.ok(proxy.includes("'points_adjust'"),'El proxy ha de permetre l\'acció a Tasques.gs');
});
test('Les icones PNG són imatges reals per 192 i 512',()=>{
  for(const size of [192,512]){
    const res={
      headers:{},setHeader(k,v){this.headers[k]=v;},
      status(n){this.code=n;return this;},
      end(value){this.body=value;return this;}
    };
    icon({method:'GET',query:{size:String(size)}},res);
    assert.equal(res.code,200);
    assert.equal(res.body.readUInt32BE(16),size);
    assert.equal(res.body.toString('hex',0,8),'89504e470d0a1a0a');
    assert.equal(res.headers['Content-Type'],'image/png');
  }
});
test('El proxy nega claus invàlides i només accepta POST',async()=>{
  process.env.MARC_APPS_SCRIPT_URL='https://script.google.com/macros/s/testdeployment/exec';
  const {default:relay}=await import('../api/relay.js?localtest=1');
  const response=()=>({
    headers:{},setHeader(k,v){this.headers[k]=v;return this;},
    status(n){this.code=n;return this;},
    json(value){this.data=value;return this;}
  });
  const unauthorized=response();
  await relay({method:'POST',headers:{origin:'https://marc.example',host:'marc.example'},body:{method:'apiAction',args:['wrong','sync',{}]}},unauthorized);
  assert.equal(unauthorized.code,401);
  const get=response();
  await relay({method:'GET',headers:{}},get);
  assert.equal(get.code,405);
});
test('El proxy només reenvia la clau en el cos a Apps Script',async()=>{
  process.env.MARC_APPS_SCRIPT_URL='https://script.google.com/macros/s/testdeployment/exec';
  const {default:relay}=await import('../api/relay.js?localtest=2');
  const response=()=>({
    headers:{},setHeader(k,v){this.headers[k]=v;return this;},
    status(n){this.code=n;return this;},
    json(value){this.data=value;return this;}
  });
  const previous=globalThis.fetch;let forwarded;
  globalThis.fetch=async(url,options)=>{
    forwarded={url,body:JSON.parse(options.body)};
    return {ok:true,text:async()=>JSON.stringify({ok:true,data:{groceries:[]}})};
  };
  try{
    const answer=response();
    await relay({method:'POST',headers:{origin:'https://marc.example',host:'marc.example'},
      body:{method:'apiAction',args:['a'.repeat(64),'sync',{ops:[]}]}},answer);
    assert.equal(answer.code,200);
    assert.equal(answer.data.ok,true);
    assert.equal(forwarded.url,'https://script.google.com/macros/s/testdeployment/exec');
    assert.equal(forwarded.body.key,'a'.repeat(64));
    assert.equal(forwarded.body.action,'sync');
    assert.ok(!forwarded.url.includes('a'.repeat(64)));
  }finally{globalThis.fetch=previous;}
});

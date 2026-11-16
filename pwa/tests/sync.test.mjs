import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import {webcrypto} from 'node:crypto';
const mobile=process.env.MARC_MOBILE_HTML||new URL('../../sync/google-apps-script/Mobile.html',import.meta.url);
const paths=[new URL('../index.html',import.meta.url),...(fs.existsSync(mobile)?[mobile]:[])];
function harness(path,stored=new Map(),renderRaiUi=false){
 const html=fs.readFileSync(path,'utf8');
 let source=[...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].at(-1)[1];
 const nodes=new Map(),events={},timers=[],requests=[];let now=10_000_000;
 const storage={getItem:k=>stored.get(k)||null,setItem:(k,v)=>stored.set(k,v),removeItem:k=>stored.delete(k)};
 const get=id=>{if(!nodes.has(id))nodes.set(id,{value:'',addEventListener:(name,fn)=>events[id+':'+name]=fn,classList:{toggle(){}},setAttribute(){},children:[],replaceChildren(){this.children=[];},appendChild(node){this.children.push(node);},append(...nodes){this.children.push(...nodes);},style:{}});return nodes.get(id);};
 source=source.replace(/\}\)\(\);\s*$/,`render=renderPhotos=()=>{};
 ${renderRaiUi?'':'renderRai=renderOverview=()=>{};'}
 serverCall=(method,...args)=>new Promise((resolve,reject)=>requests.push({method,args,resolve,reject}));
 globalThis.api={calendarPayload,renderCalendars,setData:value=>{data=value;},call,refreshPhotos,refreshAll,raiAction,prepareQueue,stale,intervals,
 start:async()=>{key='a'.repeat(64);await prepareQueue();},
 state:()=>({pendingOps,busy,lastDataAt,lastPhotosAt,lastRaiAt}),
 stamp:()=>{lastDataAt=lastRaiAt=lastPhotosAt=Date.now();}};
 })();`);
 const FakeDate=class extends Date{static now(){return now;}};
 const context={requests,document:{hidden:false,getElementById:get,querySelectorAll:()=>[],querySelector:selector=>get(selector),createElement:tag=>get('created-'+nodes.size),addEventListener:(n,f)=>events[n]=f},window:{scrollTo(){},addEventListener:(n,f)=>events[n]=f},sessionStorage:storage,localStorage:storage,crypto:webcrypto,TextEncoder,self:{crypto:webcrypto},Date:FakeDate,setInterval:fn=>timers.push(fn),console};
 vm.runInNewContext(source,context);
 return {api:context.api,requests,events,timers,stored,context,nodes,advance:ms=>now+=ms};
}
const tick=()=>new Promise(resolve=>setImmediate(resolve));
for(const path of paths){
 const label=String(path).includes('Mobile')?'Apps Script':'PWA';
 if(label==='PWA')test('PWA: el comptador de Rai es refresca en tornar i cada 30 s en primer pla',async()=>{
  const h=harness(path,new Map(),true);await h.api.start();h.api.stamp();
  const task={id:'walk_request',taskId:'walk',title:'Treure en Llamp',date:'2026-10-29',slot:'única',points:3,status:'pendent'};
  const data={today:task.date,points:0,tasks:[],done:[],rewards:[],requests:[],taskRequests:[task]};
  h.advance(6000);h.events.focus();
  assert.equal(h.requests.length,1);
  assert.equal(h.requests[0].args[1],'tasks_snapshot');
  h.requests[0].resolve({ok:true,data});await tick();
  assert.equal(h.nodes.get('raiNavBadge').textContent,'1');
  assert.equal(h.nodes.get('raiNavBadge').hidden,false);
  assert.equal(h.nodes.get('raiPendingTasks').children.length,1);
  h.advance(30000);h.context.document.hidden=true;h.timers[1]();
  assert.equal(h.requests.length,1);
  h.context.document.hidden=false;h.timers[1]();
  h.requests[1].resolve({ok:true,data:{...data,taskRequests:[],
    requests:[{rewardId:'reward',status:'pendent',kid:'Rai',cost:2}]}});
  await tick();
  assert.equal(h.nodes.get('raiNavBadge').hidden,true);
  assert.equal(h.nodes.get('raiPendingTasks').children.length,0);
  assert.equal(h.nodes.get('raiRewardApprovals').hidden,false);
 });
 test(label+': 15/30 min, pestanya oculta i represa de 5 min',async()=>{
  const h=harness(path);await h.api.start();h.api.stamp();h.timers[0]();assert.equal(h.requests.length,0);
  h.advance(5*60000-1);h.events.focus();assert.equal(h.requests.length,label==='PWA'?1:0);
  h.advance(1);h.events.focus();assert.equal(h.requests.length,3);
  h.events.pageshow();assert.equal(h.requests.length,3,'peticions en curs es comparteixen');
  h.requests.find(r=>r.args[1]==='sync').resolve({ok:true,data:{groceries:[]}});
  h.requests.find(r=>r.args[1]==='tasks_snapshot').resolve({ok:true,data:{}});
  h.requests.find(r=>r.args[1]==='photo_list').resolve({ok:true,photos:[]});await tick();
  h.api.stamp();h.advance(15*60000);h.context.document.hidden=true;h.timers[0]();assert.equal(h.requests.length,3);
  h.context.document.hidden=false;h.timers[0]();if(label==='PWA')h.timers[1]();assert.equal(h.requests.length,5,'fotos encara recents');
  h.advance(15*60000);h.timers[0]();assert.equal(h.requests.length,6,'fotos als 30 min');
 });
 test(label+': canvi durant consulta i reintent sense perdre cua',async()=>{
  const h=harness(path);await h.api.start();const first=h.api.call([]);
  h.api.call([{type:'grocery_set',id:'one',name:'Pa'}]);h.api.call([{type:'reminder_done',id:'two',done:true}]);
  assert.equal(h.requests.length,1);assert.equal(h.api.state().pendingOps.length,2);
  h.requests[0].resolve({ok:true,data:{groceries:[]}});await tick();assert.equal(h.requests.length,2);assert.equal(h.requests[1].args[2].ops.length,2);
  h.requests[1].reject(new Error('offline'));await first;assert.equal(h.api.state().pendingOps.length,2);
  const reloaded=harness(path,h.stored);await reloaded.api.start();assert.equal(reloaded.api.state().pendingOps.length,2);
  const retry=reloaded.api.call([]);assert.equal(reloaded.requests[0].args[2].ops[0].mutationId,h.requests[1].args[2].ops[0].mutationId);
  reloaded.requests[0].resolve({ok:true,data:{groceries:[]}});await retry;assert.equal(reloaded.api.state().pendingOps.length,0);
 });
 test(label+': refresc manual ignora edat i fotos concurrents es comparteixen',async()=>{
  const h=harness(path);await h.api.start();h.api.stamp();h.api.refreshAll();h.api.refreshAll();assert.equal(h.requests.length,3);
 });
 test(label+': recàrrega recent conserva antiguitat i evita consultes',async()=>{
  const h=harness(path);await h.api.start();h.api.refreshAll();
  h.requests.find(r=>r.args[1]==='sync').resolve({ok:true,data:{groceries:[]}});
  h.requests.find(r=>r.args[1]==='tasks_snapshot').resolve({ok:true,data:{}});
  h.requests.find(r=>r.args[1]==='photo_list').resolve({ok:true,photos:[]});await tick();
  const reloaded=harness(path,h.stored);await reloaded.api.start();reloaded.api.refreshAll(5);
  assert.equal(reloaded.requests.length,0);
  reloaded.advance(5*60000);reloaded.api.refreshAll(5);assert.equal(reloaded.requests.length,3);
 });
 test(label+': ajustos persistents i validació de valors',async()=>{
  const h=harness(path);h.nodes.get('dataInterval').value='60';h.events['dataInterval:change']();
  const next=harness(path,h.stored);assert.equal(next.api.intervals.data,60);assert.equal(next.api.intervals.photos,30);
  h.stored.set('marc-sync-intervals',JSON.stringify({data:-1,photos:2}));const invalid=harness(path,h.stored);assert.equal(invalid.api.intervals.data,15);assert.equal(invalid.api.intervals.photos,30);
 });
 test(label+': calendaris validats, enllaç ocult i servei antic compatible',async()=>{
  const h=harness(path);await h.api.start();
  const values={name:'Família',url:'https://calendar.google.com/calendar/ical/fixture/private-test/basic.ics',color:'#36a070',enabled:true};
  const op=h.api.calendarPayload(values);assert.equal(op.color,'#36A070');assert.equal(op.enabled,true);
  for(const url of ['http://localhost/basic.ics','https://calendar.google.com.evil.test/calendar/ical/a/basic.ics',values.url+'?x=1'])
    assert.throws(()=>h.api.calendarPayload({...values,url}));
  h.api.renderCalendars();assert.equal(h.nodes.get('calendarSave').disabled,true);
  h.events['calendarForm:submit']({preventDefault(){}});assert.equal(h.requests.length,0,'el backend antic no rep accions noves');
  h.api.setData({calendars:[op]});h.api.renderCalendars();assert.equal(h.nodes.get('calendarSave').disabled,false);
  const text=node=>(node.textContent||'')+(node.children||[]).map(text).join('');
  assert.ok(!text(h.nodes.get('calendarItems')).includes(values.url));
  const first=h.api.call([op]);h.requests[0].reject(new Error('offline'));await first;
  const reloaded=harness(path,h.stored);await reloaded.api.start();assert.equal(reloaded.api.state().pendingOps[0].type,'calendar_set');
 });
 test(label+': accions Rai simultànies s’envien en ordre',async()=>{
  const h=harness(path);await h.api.start();const a=h.api.raiAction('tasks_done',{id:'a'});const b=h.api.raiAction('tasks_done',{id:'b'});
  assert.equal(h.requests.length,1);h.requests[0].resolve({ok:true,data:{}});await a;await tick();
  const second=h.requests.find(r=>r.args[1]==='tasks_done'&&r.args[2].id==='b');assert.ok(second);second.resolve({ok:true,data:{}});await b;
 });
}

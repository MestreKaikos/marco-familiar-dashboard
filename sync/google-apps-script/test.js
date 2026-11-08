// Prova sense Google Drive ni secrets reals: simulació de fulls de càlcul en memòria.
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");

const logs = [];
const store = new Map();
const tabs = new Map();
let locked = false;
let currentDay = "2026-10-28";
const driveFiles = new Map();
const driveFolders = new Map();
function iterator(items) { let i=0; return {hasNext:()=>i<items.length, next:()=>items[i++]}; }
let driveSequence = 0;
class FakeBlob {
  constructor(bytes, type) { this.bytes = Buffer.from(bytes); this.type = type; }
  getContentType() { return this.type; }
  getSize() { return this.bytes.length; }
  getBytes() { return Array.from(this.bytes); }
}
class FakeDriveFile {
  constructor(blob,name) {
    this.id = `f-${++driveSequence}`; this.blob = blob; this.trashed = false;
    this.name=name||this.id+".jpg"; driveFiles.set(this.id, this);
  }
  getId() { return this.id; }
  getBlob() { if(this.trashed) throw new Error("File deleted"); return this.blob; }
  getThumbnail() { if(this.trashed) throw new Error("File deleted"); return this.thumbnail || null; }
  getSize() { return this.blob.getSize(); }
  getName() { return this.name; }
  getMimeType() { return this.blob.getContentType(); }
  getDateCreated() { return new Date("2026-10-28T09:00:00Z"); }
  getLastUpdated() { return new Date("2026-10-28T09:00:00Z"); }
  setTrashed(value) { this.trashed = value; }
}


class FakeSheet {
  constructor(name) { this.name = name; this.rows = []; }
  getLastRow() { return this.rows.length; }
  setFrozenRows() {}
  getRange(startRow, startCol, numRows, numCols) {
    if (typeof startRow === "string") {
      return { setNumberFormat: format => { assert.equal(format, "@"); return this; } };
    }
    const sheet = this;
    return {
      getValues() {
        return Array.from({ length: numRows }, (_, i) =>
          Array.from({ length: numCols }, (_, j) => (sheet.rows[startRow - 1 + i] || [])[startCol - 1 + j] ?? ""));
      },
      setValues(values) {
        values.forEach((row, i) => {
          const index = startRow - 1 + i;
          if (!sheet.rows[index]) sheet.rows[index] = [];
          row.forEach((cell, j) => { sheet.rows[index][startCol - 1 + j] = cell; });
        });
      },
      setValue(value) {
        if (!sheet.rows[startRow - 1]) sheet.rows[startRow - 1] = [];
        sheet.rows[startRow - 1][startCol - 1] = value;
      }
    };
  }
  appendRow(row) { this.rows.push(row.slice()); }
  deleteRow(index) { this.rows.splice(index - 1, 1); }
}

const scriptProps = {
  getProperty: key => store.get(key),
  setProperty: (key, value) => store.set(key, value)
};
const context = vm.createContext({
  console,
  Logger: { log: msg => logs.push(String(msg)) },
  PropertiesService: { getScriptProperties: () => scriptProps },
  SpreadsheetApp: {
    getActiveSpreadsheet: () => ({
      getSheetByName: name => tabs.get(name) || null,
      insertSheet: name => { const sh = new FakeSheet(name); tabs.set(name, sh); return sh; },
      getSheets: () => Array.from(tabs.values())
    }),
    flush: () => {}
  },
  Utilities: {
    DigestAlgorithm: { SHA_256: "sha256" },
    Charset: { UTF_8: "UTF-8" },
    getUuid: () => crypto.randomUUID(),
    formatDate: () => currentDay,
    computeDigest: (alg, value) => Array.from(crypto.createHash(alg).update(value).digest()).map(x => x >= 128 ? x - 256 : x),
    base64Decode: value => Array.from(Buffer.from(value, "base64")),
    base64Encode: bytes => Buffer.from(bytes).toString("base64"),
    newBlob: (bytes, type, name) => { const blob = new FakeBlob(bytes, type); blob.name = name; return blob; }
  },
  Session: {getScriptTimeZone: () => "Europe/Madrid"},
  DriveApp: {
    createFolder: name => {
      const files=[];
      const folder = {
        id:`d-${++driveSequence}`, name,
        getId() { return this.id; },
        createFile(blob) { const file=new FakeDriveFile(blob,blob.name); files.push(file); return file; },
        getFiles() { return iterator(files.filter(f=>!f.trashed)); }
      };
      driveFolders.set(folder.id, folder);
      return folder;
    },
    getFoldersByName: name => iterator(Array.from(driveFolders.values()).filter(f=>f.name===name)),
    getFolderById: id => { const folder=driveFolders.get(id); if(!folder)throw new Error("Folder missing"); return folder; },
    getFileById: id => { const file=driveFiles.get(id); if(!file||file.trashed)throw new Error("File missing"); return file; }
  },
  LockService: {
    getScriptLock: () => ({
      waitLock: () => { assert.equal(locked, false); locked = true; },
      hasLock: () => locked,
      releaseLock: () => { locked = false; }
    })
  },
  ContentService: {
    MimeType: { JSON: "application/json" },
    createTextOutput: str => ({ text: str, setMimeType(type) { this.mimeType = type; return this; } })
  },
  HtmlService: {
    createHtmlOutputFromFile: name => ({ name, setTitle() { return this; }, addMetaTag() { return this; } })
  }
});

vm.runInContext(fs.readFileSync(path.join(__dirname, "Code.gs"), "utf8"), context);
vm.runInContext(fs.readFileSync(path.join(__dirname, "Tasques.gs"), "utf8"), context);
context.setup_();
const secret = logs.join(" ").match(/[a-f0-9]{64}/)[0];
assert.match(secret, /^[a-f0-9]{64}$/);
assert.ok(!Array.from(store.values()).includes(secret), "No s'ha de desar mai el secret en clar");
assert.equal(context.apiAction("0".repeat(64), "sync", {}).ok, false);
assert.equal(context.apiAction(secret, "other", {}).ok, false);

const first = context.apiAction(secret, "sync", {});
assert.equal(first.ok, true);
assert.equal(first.data.groceries.length, 0);

const response = context.apiAction(secret, "sync", { ops: [
  {type:"grocery_set", id:"p1", name:"Llet", bought:false},
  {type:"grocery_set", id:"p2", name:"Pa", bought:false},
  {type:"dinner_set", date:"2026-10-23", value:"Truita i amanida"}
]});
assert.equal(response.ok, true);
assert.equal(response.data.groceries.length, 2);
assert.equal(response.data.dinners["2026-10-23"], "Truita i amanida");

context.apiAction(secret, "sync", {ops:[{type:"grocery_set", id:"p1", name:"Llet", bought:true}]});
const twice = context.apiAction(secret, "sync", {ops:[{type:"grocery_set", id:"p1", name:"Llet", bought:true}]});
assert.equal(twice.data.groceries.find(x => x.id === "p1").bought, true);
assert.equal(twice.data.groceries.length, 2, "Les mutacions idempotents no dupliquen productes");

context.apiAction(secret, "sync", {ops:[{type:"grocery_delete", id:"p2"}]});
assert.equal(context.apiAction(secret, "sync", {}).data.groceries.length, 1);
context.apiAction(secret, "sync", {ops:[{type:"dinner_set", date:"2026-10-23", value:""}]});
assert.equal(Object.keys(context.apiAction(secret, "sync", {}).data.dinners).length, 0);

const formula = context.apiAction(secret, "sync", {ops:[{type:"grocery_set", id:"f1", name:"=SUM(1,2)", bought:false}]});
assert.equal(formula.ok, true);
assert.ok(tabs.get("Compra").rows.some(row => row[1] === "'=SUM(1,2)"), "Una fórmula no ha de ser executada");
assert.equal(context.apiAction(secret, "sync", {ops:[{type:"unknown"}]}).ok, false);
assert.equal(locked, false, "El bloqueig sempre s'allibera després d'un error");

// El fitxer real Tasques.gs manté 7 columnes, 5è camp = franja i PIN en servidor.
context.setupFamilyTasks();
tabs.get("Tasques").appendRow(["old_task","Treure en Llamp",5,true,"once","","única"]);
tabs.get("TasquesFetes").appendRow(["2026-10-28","old_task","Rai",5,"única"]);
tabs.get("Recompenses").appendRow(["old_reward","Triar pel·lícula",10,true]);
let family=context.apiAction(secret,"sync",{ops:[]}).data;
assert.equal(family.tasks.find(t=>t.id==="old_task").frequency,"once");
assert.equal(family.stars,5);
assert.equal(family.rewards.length,1);
assert.equal(family.taskDone[0].slot,"única");

// L'API antiga Tasques.gs funciona també: estava rebutjant tota freqüència
// diferent de slots per un error de validació d'«única».
const legacyAdd=context.familyTasksAction(secret,"tasks_set",{
  id:"legacy_weekly",title:"Endreçar habitació",points:4,
  frequency:"weekly",days:"3"
});
assert.equal(legacyAdd.ok,true,legacyAdd.error);
const weekly=legacyAdd.data.tasks.find(t=>t.id==="legacy_weekly");
assert.equal(weekly.frequency,"weekly");
assert.equal(weekly.days,"3");
assert.equal(weekly.dueToday,true,"28/10/2026 és dimecres");
const legacySlot=context.familyTasksAction(secret,"tasks_set",{
  id:"legacy_slots",title:"Passejar en Llamp",points:2,
  frequency:"slots",slots:"matí,vespre"
});
assert.equal(legacySlot.ok,true,legacySlot.error);

let result=context.apiAction(secret,"sync",{ops:[
  {type:"task_set",id:"daily_task",title:"Posar la taula",stars:3,daily:true,owner:"Rai"}
]});
assert.equal(result.ok,true,result.error);
assert.equal(result.data.tasks.find(t=>t.id==="daily_task").frequency,"daily");
result=context.apiAction(secret,"sync",{ops:[{type:"task_complete",id:"daily_task",date:"2026-10-28"}]});
assert.equal(result.ok,true,result.error);
assert.equal(result.data.stars,8);
result=context.apiAction(secret,"sync",{ops:[{type:"task_complete",id:"daily_task",date:"2026-10-28"}]});
assert.equal(result.data.stars,8,"Repetir la mateixa feina i data no duplica punts");
assert.equal(context.apiAction(secret,"sync",{ops:[
  {type:"task_complete",id:"legacy_slots",date:"2026-10-28"}
]}).ok,false,"Cal escollir franja al mòbil, no atorgar punts automàtics");
assert.equal(context.apiAction(secret,"sync",{ops:[
  {type:"task_complete",id:"daily_task",date:"2026-10-29"}
]}).ok,false,"Rebutja una data futura");
currentDay="2026-10-29";
result=context.apiAction(secret,"sync",{ops:[{type:"task_complete",id:"daily_task",date:"2026-10-29"}]});
assert.equal(result.ok,true,result.error);
assert.equal(result.data.stars,11);
assert.equal(result.data.tasks.find(t=>t.id==="legacy_weekly").dueToday,false);
assert.equal(context.apiAction(secret,"sync",{ops:[
  {type:"task_complete",id:"legacy_weekly",date:"2026-10-29"}
]}).ok,false,"Una feina de dimecres no toca dijous");
// Modificar una tasca ja programada no pot esborrar les seves franges ni dies.
result=context.apiAction(secret,"sync",{ops:[{
  type:"task_set",id:"legacy_weekly",title:"Endreçar bé",stars:4,daily:true,owner:"Rai"
}]});
assert.equal(result.ok,true,result.error);
assert.equal(result.data.tasks.find(t=>t.id==="legacy_weekly").frequency,"weekly");
assert.equal(result.data.tasks.find(t=>t.id==="legacy_weekly").days,"3");
// L'Android actual no pot donar punts a membres sense identitat al full antic.
assert.equal(context.apiAction(secret,"sync",{ops:[{
  type:"task_set",id:"family_1",title:"Feina família",stars:2,daily:true,owner:"Família"
}]}).ok,false);

// Una sol·licitud de premi NO resta punts fins que l'adult l'aprova amb PIN.
const request={type:"reward_request",id:"old_reward",mutationId:"redemption_1"};
result=context.apiAction(secret,"sync",{ops:[request]});
assert.equal(result.ok,true,result.error);
assert.equal(result.data.stars,11);
assert.equal(result.data.pointsReserved,10);
assert.equal(result.data.pendingReward,"old_reward");
assert.equal(result.data.pendingRequestId,"redemption_1");
result=context.apiAction(secret,"sync",{ops:[request]});
assert.equal(result.data.pointsReserved,10,"Un reintent no reserva dues vegades");
assert.equal(tabs.get("PeticionsPremis").rows.length,2,"Una sola petició pendent");
assert.equal(context.apiAction(secret,"sync",{ops:[{
  type:"reward_request",id:"old_reward",mutationId:"redemption_2"
}]}).ok,false,"No permet reservar punts ja compromesos");
assert.equal(context.apiAction(secret,"sync",{ops:[{
  type:"reward_redeem",id:"old_reward",mutationId:"redemption_3"
}]}).ok,false,"No permet restar punts esquivant el PIN");
context.configurarPinAdult("3479");
assert.equal(context.familyTasksAction(secret,"rewards_decide",{
  id:"redemption_1",pin:"0000",approve:true
}).ok,false,"PIN incorrecte");
const approved=context.familyTasksAction(secret,"rewards_decide",{
  id:"redemption_1",pin:"3479",approve:true
});
assert.equal(approved.ok,true,approved.error);
family=context.apiAction(secret,"sync",{ops:[]}).data;
assert.equal(family.stars,1);
assert.equal(family.pointsReserved,0);
assert.equal(family.pendingReward,"");
const deleted=context.apiAction(secret,"sync",{ops:[{type:"task_delete",id:"old_task"}]});
assert.equal(deleted.ok,true);
assert.ok(!deleted.data.tasks.some(t=>t.id==="old_task"));
assert.ok(tabs.get("TasquesFetes").rows.some(r=>r[1]==="old_task"),"Històric preservat");

// Fotografies: Fotos.gs continua sent el propietari de la carpeta i de la clau de lectura.
const existingFolder=context.DriveApp.createFolder("Marc Familiar · Fotos compartides");
store.set('PHOTO_FOLDER_ID',existingFolder.getId());
const oldReaderKey='1'.repeat(64);
store.set('PHOTO_READ_KEY_HASH',crypto.createHash('sha256').update(oldReaderKey).digest('hex'));
context.photoFolder_ = () => context.DriveApp.getFolderById(store.get('PHOTO_FOLDER_ID'));
assert.equal(context.apiAction('0'.repeat(64),'photo_list',{}).ok,false);
assert.equal(context.apiAction(secret,'photo_list',{}).photos.length,0);
// La carpeta existent ha de seguir intacta; NO es crea una nova carpeta ni un full Fotos.
const folderCount=driveFolders.size;
const png=Buffer.from([137,80,78,71,13,10,26,10,1,2,3]);
const existingPng=existingFolder.createFile(new FakeBlob(png,"image/png"));
existingPng.name="Rai_car.png";
context.prepararFotosFamiliars();
assert.equal(driveFolders.size,folderCount,'No s’ha creat una carpeta nova');
assert.equal(tabs.has("Fotos"),false,'No es necessita una pestanya Fotos duplicada');
assert.equal(store.get('PHOTO_FOLDER_ID'),existingFolder.getId());
assert.equal(store.get('PHOTO_READ_KEY_HASH'),crypto.createHash('sha256').update(oldReaderKey).digest('hex'));
assert.equal(context.apiAction(secret,'photo_list',{}).photos.length,1);
assert.equal(context.apiAction(secret,'photo_get',{id:existingPng.getId()}).photo.mimeType,"image/png");
// Els originals grans copiats manualment al Drive han d'aparèixer a la llista,
// però s'envia al marc només la miniatura sense modificar ni duplicar l'original.
const largeOriginal = Buffer.alloc(6 * 1024 * 1024, 42);
const largePhoto = existingFolder.createFile(new FakeBlob(largeOriginal, "image/jpeg"));
largePhoto.name = "Vacances-original-6MB.jpg";
const smallJpeg = Buffer.from([255,216,1,2,3,255,217]);
largePhoto.thumbnail = new FakeBlob(smallJpeg, "image/jpeg");
const photoListWithLarge = context.apiAction(secret,'photo_list',{}).photos;
assert.equal(photoListWithLarge.length,2, "El Drive ha d'incloure les fotos de més de 5 MB");
const largeResponse = context.apiAction(secret,'photo_get',{id:largePhoto.getId()});
assert.equal(largeResponse.ok,true,largeResponse.error);
assert.equal(largeResponse.photo.mimeType,"image/jpeg");
assert.equal(largeResponse.photo.base64,smallJpeg.toString("base64"));
assert.equal(largePhoto.getSize(),6*1024*1024,"L'original no s'ha de modificar");
assert.equal(context.apiAction(secret,'photo_delete',{id:largePhoto.getId()}).ok,false,
  "Cap fotografia original gran no es pot esborrar per l'API del marc");

assert.equal(context.apiAction(secret,'photo_delete',{id:existingPng.getId()}).ok,false,
  'Les fotos originals de Drive no s’han de poder eliminar amb la nova API');
assert.equal(context.apiAction(secret,'photo_list',{}).photos.length,2);
const jpeg=Buffer.from([255,216,1,2,3,255,217]).toString('base64');
const addPhoto=context.apiAction(secret,'photo_upload',{id:'photo_1',name:'Platja.jpg',base64:jpeg});
assert.equal(addPhoto.ok,true,addPhoto.error);
assert.equal(addPhoto.photos.length,3);
assert.ok(addPhoto.photos.some(p=>p.name.includes('Platja')));
const newPhoto=addPhoto.photo;
assert.ok(newPhoto.id !== 'photo_1','La llista utilitza els IDs reals de Drive');
assert.equal(addPhoto.photos[0].base64,undefined,'No enviem base64 a la llista');
assert.ok(store.get('PHOTO_FOLDER_ID'),'La carpeta privada és al Drive');
assert.equal(context.apiAction(secret,'photo_get',{id:newPhoto.id}).photo.base64,jpeg);
assert.equal(context.apiAction(secret,'photo_upload',{id:'photo_1',name:'Platja.jpg',base64:jpeg}).photos.length,3);
assert.equal(context.apiAction(secret,'photo_get',{id:'../../x'}).ok,false);
assert.equal(context.apiAction(secret,'photo_upload',{id:'photo_2',name:'Engany',base64:'AAAAAA=='}).ok,false);
assert.equal(context.apiAction(secret,'photo_get',{id:'photo_2'}).ok,false);
const invalidRequest=context.doPost({postData:{contents:JSON.stringify({key:secret,action:'photo_upload',payload:{id:'photo_3',base64:'AAA'}})}});
assert.equal(JSON.parse(invalidRequest.text).ok,false);
assert.equal(context.apiAction(secret,'photo_delete',{id:newPhoto.id}).ok,true);
assert.equal(context.apiAction(secret,'photo_list',{}).photos.length,2);
assert.equal(context.apiAction(secret,'photo_get',{id:newPhoto.id}).ok,false);
assert.equal(locked,false);

const http = context.doPost({postData:{contents:JSON.stringify({key:secret,action:"sync",payload:{ops:[]}})}});
assert.equal(JSON.parse(http.text).ok, true);
const page = context.doGet();
assert.equal(page.name, "Mobile");

// L'API HTTP familiar antigua continua funcionant amb Tasques.gs real.
const invokeLegacy = request => JSON.parse(context.doPost({
  postData: {contents:JSON.stringify(request)}
}).text);
const tasksLegacy=invokeLegacy({key:secret,action:"family_tasks",
  payload:{action:"tasks_snapshot",data:{}}});
assert.equal(tasksLegacy.ok,true,tasksLegacy.error);
assert.equal(tasksLegacy.data.points,1);
assert.equal(invokeLegacy({key:"0".repeat(64),action:"family_tasks",
  payload:{action:"tasks_snapshot"}}).ok,false);
// Fotos.gs utilitza una altra clau per a la lectura; simulem aquesta validació.
let oldCalls=0;
context.photoApiAction_=(key,action,payload)=>{
  oldCalls++;
  if(key!==oldReaderKey)return {ok:false,error:"Clau de lectura incorrecta"};
  return {ok:true,legacy:true,action,payload};
};
assert.equal(invokeLegacy({key:secret,action:"photos_list",payload:{}}).ok,false);
assert.equal(invokeLegacy({key:oldReaderKey,action:"photos_list",payload:{}}).ok,true);
assert.equal(invokeLegacy({key:oldReaderKey,action:"photos_get",
  payload:{id:"arxiu_imatge_01"}}).ok,true);
assert.equal(oldCalls,3);
// No s'han escrit mai claus ni PIN en el Sheets.
assert.ok(!Array.from(tabs.values()).some(t=>t.rows.flat().includes(secret)));


// Compatibilitat amb les interaccions de la pàgina Mobile.html REAL.
const pointsBeforeSlots=context.familyTasksSnapshot_().points;
assert.equal(context.familyTasksAction(secret,'tasks_done',{id:'legacy_slots',slot:'matí'}).ok,true);
assert.equal(context.familyTasksSnapshot_().points,pointsBeforeSlots+2);
assert.equal(context.familyTasksAction(secret,'tasks_done',{id:'legacy_slots',slot:'matí'}).ok,true);
assert.equal(context.familyTasksSnapshot_().points,pointsBeforeSlots+2,
  'Una franja repetida avui no ha de donar més punts');
assert.equal(context.familyTasksAction(secret,'tasks_done',{id:'legacy_slots',slot:'vespre'}).ok,true);
assert.equal(context.familyTasksSnapshot_().points,pointsBeforeSlots+4);
assert.equal(context.familyTasksAction(secret,'tasks_undo',{id:'legacy_slots',slot:'matí'}).ok,true);
assert.equal(context.familyTasksSnapshot_().points,pointsBeforeSlots+2);
assert.equal(context.familyTasksAction(secret,'tasks_undo',{id:'legacy_slots',slot:'vespre'}).ok,true);
assert.equal(context.familyTasksSnapshot_().points,pointsBeforeSlots);

assert.equal(context.familyTasksAction(secret,'rewards_set',{
  id:'cheap_reward',title:'Premi senzill',cost:1
}).ok,true);
const legacyClaim=context.familyTasksAction(secret,'rewards_claim',{id:'cheap_reward'});
assert.equal(legacyClaim.ok,true,legacyClaim.error);
assert.ok(legacyClaim.data.requests.some(x=>x.rewardId==='cheap_reward'&&x.status==='pendent'));
const req=legacyClaim.data.requests.find(x=>x.rewardId==='cheap_reward'&&x.status==='pendent');
assert.equal(context.familyTasksAction(secret,'rewards_decide',{
  id:req.id,approve:false,pin:'3479'
}).ok,true);
assert.equal(context.familyTasksSnapshot_().points,pointsBeforeSlots,
  'Denegar una petició no descompta punts');

// Recordatoris nous (no esborren ni reutilitzen la pestanya Sopars).
const dinner = context.apiAction(secret,'sync',{ops:[
  {type:'dinner_set',date:'2026-11-03',value:'Arròs amb verdures'}
]});
assert.equal(dinner.ok,true);
let rem=context.apiAction(secret,'sync',{ops:[
  {type:'reminder_set',id:'rem_01',title:"Vacunació d'en Llamp",
   date:'2026-11-12',notes:'Trucar al veterinari'}
]});
assert.equal(rem.ok,true,rem.error);
assert.equal(rem.data.reminders.length,1);
assert.equal(rem.data.reminders[0].done,false);
assert.equal(rem.data.reminders[0].date,'2026-11-12');
assert.ok(tabs.get('Recordatoris'));
assert.equal(rem.data.dinners['2026-11-03'],'Arròs amb verdures');
assert.equal(context.apiAction(secret,'sync',{ops:[
  {type:'reminder_set',id:'rem_01',title:"Vacunació d'en Llamp",
   date:'2026-11-12',notes:'Trucar al veterinari'}
]}).data.reminders.length,1,'No duplicar recordatoris pel mateix id');
assert.equal(context.apiAction(secret,'sync',{ops:[
  {type:'reminder_set',id:'bad',title:'Data impossible',date:'2026-02-30'}
]}).ok,false);
assert.equal(context.apiAction(secret,'sync',{ops:[
  {type:'reminder_set',id:'rem_02',title:'Pagar quota',date:'2026-10-30'}
]}).ok,true);
rem=context.apiAction(secret,'sync',{ops:[{type:'reminder_done',id:'rem_02',done:true}]});
assert.equal(rem.ok,true);
assert.equal(rem.data.reminders.at(-1).id,'rem_02');
assert.equal(rem.data.reminders.find(r=>r.id==='rem_02').done,true);
rem=context.apiAction(secret,'sync',{ops:[{type:'reminder_done',id:'rem_02',done:false}]});
assert.equal(rem.data.reminders.find(r=>r.id==='rem_02').done,false);
rem=context.apiAction(secret,'sync',{ops:[{
  type:'reminder_set',id:'rem_02',title:'Pagar quota escola',date:'2026-10-31',notes:''
}]});
assert.equal(rem.data.reminders.find(r=>r.id==='rem_02').title,'Pagar quota escola');
assert.equal(rem.data.reminders.find(r=>r.id==='rem_02').date,'2026-10-31');
rem=context.apiAction(secret,'sync',{ops:[{type:'reminder_delete',id:'rem_02'}]});
assert.equal(rem.data.reminders.length,1);
assert.equal(rem.data.dinners['2026-11-03'],'Arròs amb verdures',
  'Els sopars històrics no s’esborren');

const mobileSource=fs.readFileSync(path.join(__dirname,'Mobile.html'),'utf8');
for(const section of ['avui','compra','recordatoris','fotos','rai']){
  assert.match(mobileSource,new RegExp('data-tab="'+section+'"'),'Falta la pestanya '+section);
  assert.match(mobileSource,new RegExp('data-open="'+section+'"'),'Falta navegació a '+section);
}
// La nova pàgina de portada uneix les dues llistes sense alterar els IDs ni les crides.
assert.equal((mobileSource.match(/<section class="panel" data-tab="compra">/g)||[]).length,1,
  'Compra ha de tenir una pestanya pròpia');
assert.ok(!mobileSource.includes('class="subpanel shopping-section"'),
  'Compra no ha d’aparèixer dins Recordatoris');
assert.ok(mobileSource.includes('class="summary-card grocery-summary" type="button" data-go="compra"'),
  "La targeta Compra d’Avui ha d'obrir Compra");
assert.ok(mobileSource.includes('id="overviewReminders"'));
assert.ok(mobileSource.includes('id="overviewGroceries"'));
assert.ok(mobileSource.includes('id="overviewRai"'));
assert.ok(mobileSource.includes('id="overviewPhotos"'));
assert.ok(mobileSource.includes('function renderOverview()'));
assert.ok(mobileSource.includes('data-open="avui"'));
const inlineJs=mobileSource.match(/<script>([\s\S]*?)<\/script>/);
assert.ok(inlineJs, "El JavaScript de Mobile.html ha d'existir");
assert.doesNotThrow(()=>new vm.Script(inlineJs[1]), 'El JavaScript del mòbil ha de ser sintàcticament vàlid');

for(const id of ['addReminder','reminderTitle','reminderDate','reminderNotes','reminderItems','reminderCount',
  'raiTaskFrequency','raiDays','raiSlots','raiTaskList',
  'raiRewardList','raiPendingRewards','raiAddTask','raiAddReward','photoInput','photoPreview',
  'photoItems','photoNotice']){
  assert.ok(mobileSource.includes('id="'+id+'"'),'Falta element original: '+id);
}
assert.ok(mobileSource.includes("serverCall('familyTasksAction',key,action,payload)"),
  'Les tasques del mòbil han de conservar el servei real');
assert.ok(mobileSource.includes("serverCall('uploadFamilyPhoto',key,"),
  'La pujada de 5 MB ha de continuar passant per Fotos.gs');
assert.ok(mobileSource.includes('5*1024*1024'));
assert.ok(mobileSource.includes('files.length>20'));
for(const action of ['tasks_set','tasks_done','tasks_undo','rewards_set','rewards_claim','rewards_decide']){
  assert.ok(mobileSource.includes("'"+action+"'"),'Falta la capacitat del mòbil: '+action);
}
assert.ok(!mobileSource.includes('compactJpeg(file)'),
  'No substituir l’enviament original per JPEG comprimit');
assert.ok(mobileSource.includes("type:'reminder_done'"),'Marcar recordatori com a fet');
assert.ok(mobileSource.includes("type:'reminder_set'"),'Crear i editar recordatori');
assert.ok(mobileSource.includes("type:'reminder_delete'"),'Treure recordatori');
assert.ok(mobileSource.includes("type=\"date\""),'Selector de data límit');
assert.ok(!mobileSource.includes('data-tab="sopars"'),
  'Sopars deixa de ser una pestanya visible');
assert.ok(!mobileSource.includes("photoCall('photo_delete'"),
  'El Mobile original no permetia esborrar fotos: la galeria no ho ha de fer');


// Ajustos manuals dels pares: clau familiar, historial separat, reintents i saldo zero.
const basePoints=context.familyTasksSnapshot_().points;
const baselineDone=tabs.get("TasquesFetes").rows.length;
const baselineClaims=tabs.get("CanvisPunts").rows.length;
assert.equal(context.familyTasksAction("0".repeat(64),'points_adjust',{
  id:'manual_no_auth',delta:5
}).ok,false,'La clau familiar continua sent obligatòria');
assert.equal(context.familyTasksAction(secret,'points_adjust',{
  id:'manual_bad_value',delta:'desconegut'
}).ok,false,'Un canvi invàlid no altera els punts');
assert.equal(context.familyTasksAction(secret,'points_adjust',{
  id:'manual_bad_delta',delta:100
}).ok,false,'Només salts petits');
assert.equal(context.familyTasksAction(secret,'points_adjust',{
  id:'manual_bonus',delta:5,reason:'Bona iniciativa'
}).ok,true);
assert.equal(context.familyTasksSnapshot_().points,basePoints+5);
assert.equal(context.familyTasksAction(secret,'points_adjust',{
  id:'manual_bonus',delta:5
}).ok,true,'Repetir una petició no duplica punts');
assert.equal(context.familyTasksSnapshot_().points,basePoints+5);
assert.equal(context.familyTasksAction(secret,'points_adjust',{
  id:'manual_minus',delta:-2,reason:'Ajust'
}).ok,true);
assert.equal(context.familyTasksSnapshot_().points,basePoints+3);
assert.equal(context.familyTasksAction(secret,'points_adjust',{
  id:'manual_negative',delta:-10
}).ok,false,'No permet saldos negatius');
assert.equal(context.familyTasksSnapshot_().adjustments[0].delta,-2);
assert.equal(context.familyTasksAction(secret,'points_adjust',{
  id:'manual_reset',reset:true,reason:'Nou començament'
}).ok,true,'Els pares poden reiniciar el saldo');
assert.equal(context.familyTasksSnapshot_().points,0);
assert.equal(tabs.get("TasquesFetes").rows.length,baselineDone,'No esborra feines fetes');
assert.equal(tabs.get("CanvisPunts").rows.length,baselineClaims,'No esborra premis');
assert.equal(context.familyTasksAction(secret,'points_adjust',{
  id:'manual_post_reset',delta:1
}).ok,true);
assert.equal(context.familyTasksAction(secret,'points_adjust',{
  id:'manual_reset',reset:true
}).data.points,1,'Reintentar el reset no torna a posar a zero');
assert.equal(context.apiAction(secret,'sync',{}).data.stars,1,
  'L\'Android veu el saldo ajustat a través del mateix sync');
assert.equal(context.familyTasksAction(secret,'rewards_claim',{id:'cheap_reward'}).ok,true);
assert.equal(context.familyTasksAction(secret,'points_adjust',{
  id:'manual_reset_reserved',reset:true
}).ok,false,'No esborra punts reservats per recompenses');
const pendingAfterAdjustment=context.familyTasksSnapshot_().requests.find(
  x=>x.status==='pendent'&&x.rewardId==='cheap_reward');
assert.equal(context.familyTasksAction(secret,'rewards_decide',{
  id:pendingAfterAdjustment.id,approve:false,pin:'3479'
}).ok,true);
assert.equal(context.familyTasksAction(secret,'points_adjust',{
  id:'manual_reset_after_reject',reset:true
}).data.points,0);
assert.equal(tabs.get("AjustosPunts").rows.length,6,'Capçalera i cinc moviments manuals');
assert.equal(tabs.get("AjustosPunts").rows.filter(r=>r[0]==='manual_bonus').length,1,
  'El reintent no pot afegir el mateix bonus dues vegades');
assert.ok(!tabs.get("AjustosPunts").rows.some(r=>r.includes('3479')),
  'El PIN no apareix a l\'historial');
assert.equal(locked,false);

console.log("OK · Tasques.gs real: freqüències, dies, franges, PIN, recompenses pendents, dos secrets i fotos de Drive.");

// Calendaris: autenticar, fusionar, desactivar i conservar eliminacions entre dispositius.
const calUrl="https://calendar.google.com/calendar/ical/test%40example.com/private-fixture/basic.ics";
const calOp={type:"calendar_set",id:"calendar-one",name:"Família",url:calUrl,color:"#3977CB",enabled:true};
const syncCal=ops=>context.apiAction(secret,"sync",{ops});
assert.equal(syncCal([calOp]).ok,true);
assert.equal(syncCal([]).data.calendars.length,1);
assert.equal(context.apiAction("0".repeat(64),"sync",{}).ok,false);
assert.equal(syncCal([{...calOp,id:"duplicate"}]).ok,false);
for(const bad of ["http://calendar.google.com/calendar/ical/a/basic.ics","https://calendar.google.com.evil.test/calendar/ical/a/basic.ics","https://localhost/calendar/ical/a/basic.ics","https://calendar.google.com@evil.test/calendar/ical/a/basic.ics",calUrl+"?redirect=1"])
  assert.equal(syncCal([{...calOp,id:"bad",url:bad}]).ok,false);
assert.equal(syncCal([{...calOp,color:"red;injected"}]).ok,false);
assert.equal(syncCal([{type:"calendar_enabled",id:calOp.id,enabled:false}]).data.calendars[0].enabled,false);
assert.equal(syncCal([{type:"calendar_color",id:calOp.id,color:"#36A070"}]).data.calendars[0].color,"#36A070");
assert.equal(syncCal([{type:"calendar_import",calendars:[calOp]}]).data.calendars[0].enabled,false,"La importació no trepitja la configuració compartida");
assert.equal(syncCal([{type:"calendar_delete",id:calOp.id}]).data.calendars.length,0);
assert.equal(syncCal([{type:"calendar_import",calendars:[calOp,{...calOp,id:"another-device"}]}]).data.calendars.length,0,"Una importació antiga no ressuscita eliminats");
assert.equal(syncCal([]).data.calendarsConfigured,true,"Una llista buida explícita es conserva");
const secondCal={...calOp,id:"calendar-two",url:calUrl.replace('test%40','rai%40')};
assert.equal(syncCal([{type:"calendar_import",calendars:[secondCal]}]).data.calendars.length,1);
assert.equal(syncCal([{type:"calendar_import",calendars:[secondCal]}]).data.calendars.length,1);
assert.equal(syncCal([{...secondCal,name:"Rai",enabled:false}]).data.calendars[0].name,"Rai");
console.log("OK · Calendaris privats: validació, fusió, colors, activació i eliminacions persistents.");

assert.equal(syncCal([{...calOp,id:"re-added"}]).ok,true,"Es pot tornar a afegir un calendari eliminat expressament");
assert.equal(syncCal([{...calOp,id:"re-added-duplicate"}]).ok,false,"Una fila eliminada no ha d'amagar duplicats actius");

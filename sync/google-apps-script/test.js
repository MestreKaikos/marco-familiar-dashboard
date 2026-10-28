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
    computeDigest: (alg, value) => Array.from(crypto.createHash(alg).update(value).digest()).map(x => x >= 128 ? x - 256 : x),
    base64Decode: value => Array.from(Buffer.from(value, "base64")),
    base64Encode: bytes => Buffer.from(bytes).toString("base64"),
    newBlob: (bytes, type, name) => { const blob = new FakeBlob(bytes, type); blob.name = name; return blob; }
  },
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

// Migració no destructiva del full familiar que ja existia.
tabs.get("Tasques").appendRow(["old_task","Treure en Llamp",5,true,"única","Rai"]);
tabs.get("TasquesFetes").appendRow(["2026-10-28","old_task","Rai",5,"única"]);
tabs.get("Recompenses").appendRow(["old_reward","Triar pel·lícula",10,true]);
const existingFamily=context.apiAction(secret,"sync",{ops:[]}).data;
assert.equal(existingFamily.tasks.find(t=>t.id==="old_task").title,"Treure en Llamp");
assert.equal(existingFamily.stars,5,"L'esforç existent no es perd");
assert.equal(existingFamily.rewards.length,1);
let result=context.apiAction(secret,"sync",{ops:[
  {type:"task_set",id:"daily_task",title:"Posar la taula",stars:3,daily:true,owner:"Rai"}
]});
assert.equal(result.ok,true,result.error);
result=context.apiAction(secret,"sync",{ops:[{type:"task_complete",id:"daily_task",date:"2026-10-28"}]});
assert.equal(result.data.stars,8);
result=context.apiAction(secret,"sync",{ops:[{type:"task_complete",id:"daily_task",date:"2026-10-28"}]});
assert.equal(result.data.stars,8,"Repetir la mateixa feina i data no duplica punts");
result=context.apiAction(secret,"sync",{ops:[{type:"task_complete",id:"daily_task",date:"2026-10-29"}]});
assert.equal(result.data.stars,11,"Una tasca diària pot repetir-se l'endemà");
const redeem={type:"reward_redeem",id:"old_reward",mutationId:"redemption_1"};
result=context.apiAction(secret,"sync",{ops:[redeem]});
assert.equal(result.data.stars,1,"Descompta punts del full, sense perdre completades");
result=context.apiAction(secret,"sync",{ops:[redeem]});
assert.equal(result.data.stars,1,"Un reintent d'un premi no descompta dues vegades");
assert.equal(context.apiAction(secret,"sync",{ops:[{type:"reward_redeem",id:"old_reward",mutationId:"redemption_2"}]}).ok,false);
const deleted=context.apiAction(secret,"sync",{ops:[{type:"task_delete",id:"old_task"}]});
assert.ok(!deleted.data.tasks.some(t=>t.id==="old_task"));
assert.ok(tabs.get("TasquesFetes").rows.some(r=>r[1]==="old_task"),"El registre històric es manté");


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
const jpeg=Buffer.from([255,216,1,2,3,255,217]).toString('base64');
const addPhoto=context.apiAction(secret,'photo_upload',{id:'photo_1',name:'Platja.jpg',base64:jpeg});
assert.equal(addPhoto.ok,true,addPhoto.error);
assert.equal(addPhoto.photos.length,2);
assert.ok(addPhoto.photos.some(p=>p.name.includes('Platja')));
const newPhoto=addPhoto.photo;
assert.ok(newPhoto.id !== 'photo_1','La llista utilitza els IDs reals de Drive');
assert.equal(addPhoto.photos[0].base64,undefined,'No enviem base64 a la llista');
assert.ok(store.get('PHOTO_FOLDER_ID'),'La carpeta privada és al Drive');
assert.equal(context.apiAction(secret,'photo_get',{id:newPhoto.id}).photo.base64,jpeg);
assert.equal(context.apiAction(secret,'photo_upload',{id:'photo_1',name:'Platja.jpg',base64:jpeg}).photos.length,2);
assert.equal(context.apiAction(secret,'photo_get',{id:'../../x'}).ok,false);
assert.equal(context.apiAction(secret,'photo_upload',{id:'photo_2',name:'Engany',base64:'AAAAAA=='}).ok,false);
assert.equal(context.apiAction(secret,'photo_get',{id:'photo_2'}).ok,false);
const invalidRequest=context.doPost({postData:{contents:JSON.stringify({key:secret,action:'photo_upload',payload:{id:'photo_3',base64:'AAA'}})}});
assert.equal(JSON.parse(invalidRequest.text).ok,false);
assert.equal(context.apiAction(secret,'photo_delete',{id:newPhoto.id}).ok,true);
assert.equal(context.apiAction(secret,'photo_list',{}).photos.length,1);
assert.equal(context.apiAction(secret,'photo_get',{id:newPhoto.id}).ok,false);
assert.equal(locked,false);

const http = context.doPost({postData:{contents:JSON.stringify({key:secret,action:"sync",payload:{ops:[]}})}});
assert.equal(JSON.parse(http.text).ok, true);
const page = context.doGet();
assert.equal(page.name, "Mobile");

// Les rutes HTTP antigues de Fotos.gs i familyTasksAction no es trenquen.
const legacyCalls = [];
context.familyTasksAction = (key, action, data) => {
  legacyCalls.push({route:"family_tasks",key,action,data});
  return {ok: true, legacy:"tasks", action, data};
};
context.photoApiAction_ = (key, action, payload) => {
  legacyCalls.push({route:action,key,payload});
  if(key!==oldReaderKey)return {ok:false,error:'Clau de lectura incorrecta'};
  return {ok: true, legacy:"photos", action, payload};
};
const invokeLegacy = request => JSON.parse(context.doPost({
  postData: {contents:JSON.stringify(request)}
}).text);
const taskLegacy=invokeLegacy({key:secret,action:"family_tasks",
  payload:{action:"list",data:{persona:"Rai"}}});
assert.equal(taskLegacy.legacy,"tasks");
assert.equal(taskLegacy.action,"list");
assert.equal(legacyCalls[0].key,secret);
assert.equal(invokeLegacy({key:secret,action:"photos_list",payload:{page:1}}).ok,false);
const photosLegacy=invokeLegacy({key:oldReaderKey,action:"photos_list",payload:{page:1}});
assert.equal(photosLegacy.legacy,"photos");
assert.equal(photosLegacy.action,"photos_list");
const photoLegacy=invokeLegacy({key:oldReaderKey,action:"photos_get",payload:{id:"foto_01"}});
assert.equal(photoLegacy.payload.id,"foto_01");
assert.equal(legacyCalls.length,4);
delete context.familyTasksAction;
delete context.photoApiAction_;
assert.equal(invokeLegacy({key:secret,action:"family_tasks",payload:{action:"list"}}).ok,false);
assert.equal(invokeLegacy({key:secret,action:"photos_list",payload:{}}).ok,false);

console.log("OK · KEY_HASH i PHOTO_READ_KEY_HASH compatibles; mateix PHOTO_FOLDER_ID, fotos existents, compra, sopars, tasques i punts.");

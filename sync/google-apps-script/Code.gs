/**
 * Marc Familiar · sincronització via Google Sheets, sense servidor propi.
 *
 * Integració amb el projecte existent: conserva Fotos.gs i el fitxer de
 * familyTasksAction. No executis rotateSecret_() ni creïs cap clau nova.
 *
 * El Web App ha d'executar-se com el propietari, i accepta peticions externes
 * NÚMÉS amb la clau aleatòria, mai per l'URL. No compartir la clau al GitHub.
 * La clau no es publica mai en HTML ni a cap full de càlcul.
 */

const COMPRA = "Compra";
const SOPARS = "Sopars";
const MAX_PRODUCTS = 200;
const MAX_DINNERS = 366;
const MAX_PHOTOS = 300; // Mateix límit que Fotos.gs.
const PHOTO_MAX_BYTES = 5 * 1024 * 1024; // Fotos.gs accepta imatges existents de fins a 5 MB.
const PHOTO_UPLOAD_MAX_BYTES = 1100000; // Les pujades del panell unificat són JPEG comprimits.

/** Executa-la una sola vegada des de l'editor d'Apps Script; no des del mòbil. */
function setup_() {
  const props = PropertiesService.getScriptProperties();
  if (props.getProperty("KEY_HASH")) {
    Logger.log("La clau ja està creada. No es mostrarà de nou. Per rotar-la, executa rotateSecret_().");
    return;
  }
  rotateSecret_();
}

/** Rotar la clau desconnecta tots els dispositius fins que se'n posi la nova. */
function rotateSecret_() {
  const key = Utilities.getUuid().replace(/-/g, "") + Utilities.getUuid().replace(/-/g, "");
  PropertiesService.getScriptProperties().setProperty("KEY_HASH", digest_(key));
  setupSheets_();
  Logger.log("CLAU PRIVADA, MOSTRA-LA NOMÉS ALS DISPOSITIUS FAMILIARS: " + key);
}

function digest_(text) {
  return Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, text, Utilities.Charset.UTF_8)
    .map(function (b) { return ("0" + (b & 255).toString(16)).slice(-2); })
    .join("");
}

function authenticated_(key) {
  const expected = PropertiesService.getScriptProperties().getProperty("KEY_HASH");
  if (!expected || typeof key !== "string" || key.length !== 64) return false;
  const actual = digest_(key);
  if (expected.length !== actual.length) return false;
  let mismatch = 0;
  for (let i = 0; i < expected.length; i++) mismatch |= expected.charCodeAt(i) ^ actual.charCodeAt(i);
  return mismatch === 0;
}

function sheet_(name, header) {
  const book = SpreadsheetApp.getActiveSpreadsheet();
  if (!book) throw new Error("Cal crear el projecte des de Google Sheets (vinculat al document).");
  let tab = book.getSheetByName(name);
  if (!tab) tab = book.insertSheet(name);
  if (tab.getLastRow() === 0) {
    tab.getRange(1, 1, 1, header.length).setValues([header]);
    tab.setFrozenRows(1);
  }
  return tab;
}

function setupSheets_() {
  const groceries = sheet_(COMPRA, ["id", "producte", "comprat"]);
  const dinners = sheet_(SOPARS, ["data", "sopar"]);
  // Conservar el nom literal dels productes i les dates YYYY-MM-DD,
  // sense que Google Sheets les converteixi en fórmules o dates numèriques.
  groceries.getRange("B:B").setNumberFormat("@");
  dinners.getRange("A:A").setNumberFormat("@");
}

function rows_(tab, count) {
  const last = tab.getLastRow();
  return last < 2 ? [] : tab.getRange(2, 1, last - 1, count).getValues();
}

/** Escape de fórmules: tot allò que entra a un full s'escriu com a text. */
function safeText_(value, max) {
  const text = String(value == null ? "" : value).trim().slice(0, max);
  return /^[=+\-@\t\r]/.test(text) ? "'" + text : text;
}

function readSnapshot_() {
  const groceries = rows_(sheet_(COMPRA, ["id", "producte", "comprat"]), 3)
    .filter(function (r) { return r[0] && r[1]; })
    .map(function (r) { return { id: String(r[0]), name: String(r[1]), bought: r[2] === true }; });
  const dinners = {};
  rows_(sheet_(SOPARS, ["data", "sopar"]), 2).forEach(function (r) {
    if (typeof r[0] === "string" && /^\d{4}-\d{2}-\d{2}$/.test(r[0]) && r[1]) {
      dinners[r[0]] = String(r[1]);
    }
  });
  return Object.assign({ groceries: groceries, dinners: dinners }, readFamily_());
}

function validateId_(id) {
  if (typeof id !== "string" || !/^[a-zA-Z0-9_-]{1,80}$/.test(id)) throw new Error("Identificador no vàlid");
  return id;
}

function upsertProduct_(tab, op) {
  const id = validateId_(op.id);
  const name = safeText_(op.name, 80);
  if (!name) throw new Error("Falta el nom del producte");
  const bought = op.bought === true;
  const rows = rows_(tab, 3);
  const index = rows.findIndex(function (row) { return row[0] === id; });
  if (index < 0) {
    if (rows.length >= MAX_PRODUCTS) throw new Error("Límit de productes");
    tab.appendRow([id, name, bought]);
  } else {
    tab.getRange(index + 2, 1, 1, 3).setValues([[id, name, bought]]);
  }
}

function deleteProduct_(tab, op) {
  const id = validateId_(op.id);
  const rows = rows_(tab, 3);
  const index = rows.findIndex(function (row) { return row[0] === id; });
  if (index >= 0) tab.deleteRow(index + 2);
}

function setDinner_(tab, op) {
  const date = String(op.date || "");
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || isNaN(Date.parse(date + "T00:00:00Z"))) {
    throw new Error("Data no vàlida");
  }
  const dinner = safeText_(op.value, 180);
  const rows = rows_(tab, 2);
  const index = rows.findIndex(function (row) { return row[0] === date; });
  if (index >= 0) {
    if (!dinner) tab.deleteRow(index + 2);
    else tab.getRange(index + 2, 1, 1, 2).setValues([[date, dinner]]);
  } else if (dinner) {
    if (rows.length >= MAX_DINNERS) throw new Error("Límit de sopars");
    tab.appendRow([date, dinner]);
  }
}

/**
 * Adaptador segur a Tasques.gs original. No redefineix cap de les seves funcions.
 * Aquest fitxer és la font de veritat de les freqüències, franges i del PIN.
 */
function familySheets_() {
  if (typeof familyTaskSheet_ !== "function" ||
      typeof familyTasksSnapshot_ !== "function" ||
      typeof familyPositive_ !== "function") {
    throw new Error("Cal conservar Tasques.gs del projecte familiar");
  }
  return {
    tasks: familyTaskSheet_("Tasques",["id","titol","punts","activa","frequencia","dies","franges"]),
    done: familyTaskSheet_("TasquesFetes",["data","tascaId","persona","punts","franja"]),
    rewards: familyTaskSheet_("Recompenses",["id","recompensa","cost","activa"]),
    changes: familyTaskSheet_("CanvisPunts",["id","data","persona","recompensaId","cost"]),
    requests: familyTaskSheet_("PeticionsPremis",["id","data","persona","recompensaId","cost","estat"])
  };
}
function active_(value) {
  return value === true || ["true","1","si","sí"].includes(String(value).trim().toLowerCase());
}
function readFamily_() {
  const original = familyTasksSnapshot_();
  const tasks = original.tasks.filter(t => t.active).map(t => ({
    id: t.id, title: t.title, stars: t.points, owner: "Rai",
    // La versió Android actual coneix una completada per dia per tasca.
    // Altres franges es mostren sense permetre completar-les incorrectament.
    daily: t.frequency !== "once",
    frequency: t.frequency, days: t.days, slots: t.slots,
    dueToday: t.dueToday === true
  }));
  const done = original.done.filter(d => d.kid === "Rai").map(d => ({
    date: d.date, taskId: d.taskId, owner: "Rai", points: d.points, slot: d.slot
  }));
  const rewards = original.rewards.filter(r => r.active).map(r => ({
    id: r.id, title: r.title, cost: r.cost
  }));
  const pending = original.requests.find(r =>
    r.kid === "Rai" && r.status === "pendent");
  const reserved = original.requests.filter(r =>
    r.kid === "Rai" && r.status === "pendent")
    .reduce((sum, r) => sum + r.cost, 0);
  return {
    tasks: tasks, taskDone: done, rewards: rewards,
    stars: Math.max(0, original.points), pointsReserved: reserved,
    pendingReward: pending ? pending.rewardId : "",
    pendingRequestId: pending ? pending.id : ""
  };
}
/** L'Android v1 només crea feines de Rai, diàries o d'una sola vegada.
 * Les altres programacions es conserven, sense modificar-les.
 */
function taskSet_(op) {
  if (op.owner && op.owner !== "Rai")
    throw new Error("Les feines familiars no tenen aquest format al full antic");
  const tabs = familySheets_();
  const id = validateId_(op.id), title = safeText_(op.title,100);
  if (!title) throw new Error("Tasca sense títol");
  const points = familyPositive_(op.stars,100);
  const schedule = {
    frequency: op.daily === true ? "daily" : "once", days: "", slots: "única"
  };
  const rows = rows_(tabs.tasks,7);
  const idx = rows.findIndex(r => String(r[0]) === id);
  if (idx < 0) {
    if (rows.length >= MAX_FAMILY_TASKS_) throw new Error("Massa tasques");
    tabs.tasks.appendRow([id,title,points,true,schedule.frequency,schedule.days,schedule.slots]);
  } else {
    // No perdre dies i franges de feines creades abans des del mòbil.
    const was = rows[idx];
    const preserve = {
      frequency: String(was[4] || "daily"),
      days: String(was[5] || ""),
      slots: String(was[6] || "única")
    };
    tabs.tasks.getRange(idx+2,1,1,7).setValues([[
      id,title,points,true,preserve.frequency,preserve.days,preserve.slots
    ]]);
  }
}
function taskDelete_(op) {
  const tab = familySheets_().tasks;
  const id = validateId_(op.id), rows = rows_(tab,7);
  const idx = rows.findIndex(r => String(r[0]) === id);
  if (idx >= 0) tab.getRange(idx+2,4,1,1).setValues([[false]]);
}
function taskComplete_(op) {
  const id = validateId_(op.id);
  if (typeof op.date !== "string" || op.date !== taskDate_())
    throw new Error("Confirma la feina durant el dia actual");
  const tabs = familySheets_();
  const snapshot = familyTasksSnapshot_();
  const task = snapshot.tasks.find(t => t.id === id && t.active && t.dueToday);
  if (!task) throw new Error("Aquesta feina no toca avui");
  const slot = String(op.slot || "única");
  if (task.frequency === "slots")
    throw new Error("Cal triar la franja al gestor familiar");
  if (!task.slots.split(",").includes(slot))
    throw new Error("Franja no vàlida");
  const already = snapshot.done.some(d =>
    d.taskId === id && d.kid === "Rai" && d.date === op.date && d.slot === slot);
  if (!already) {
    tabs.done.appendRow([op.date,id,"Rai",task.points,slot]);
  }
}
function rewardSet_(op) {
  const tab = familySheets_().rewards;
  const id = validateId_(op.id), title = safeText_(op.title,100);
  if (!title) throw new Error("Premi sense títol");
  const cost = familyPositive_(op.cost,10000);
  const rows = rows_(tab,4), idx = rows.findIndex(r => String(r[0]) === id);
  if (idx < 0) {
    if (rows.length >= MAX_FAMILY_REWARDS_) throw new Error("Massa recompenses");
    tab.appendRow([id,title,cost,true]);
  } else {
    tab.getRange(idx+2,1,1,4).setValues([[id,title,cost,true]]);
  }
}
function rewardDelete_(op) {
  const tab = familySheets_().rewards, id = validateId_(op.id);
  const rows = rows_(tab,4), idx = rows.findIndex(r => String(r[0]) === id);
  if (idx >= 0) tab.getRange(idx+2,4,1,1).setValues([[false]]);
}
/** Una petició de recompensa MAI no descompta punts sense el PIN adult.
 * El mateix mutationId s'utilitza com a ID per a reintents sense duplicats.
 */
function rewardRequest_(op) {
  const id = validateId_(op.id), mutationId = validateId_(op.mutationId);
  const tabs = familySheets_();
  const snapshot = familyTasksSnapshot_();
  const existing = snapshot.requests.find(r => r.id === mutationId);
  if (existing) {
    if (existing.rewardId !== id) throw new Error("ID de petició en conflicte");
    return;
  }
  const reward = snapshot.rewards.find(r => r.id === id && r.active);
  if (!reward) throw new Error("Recompensa no disponible");
  const reserved = snapshot.requests.filter(r =>
    r.kid === "Rai" && r.status === "pendent")
    .reduce((sum,r) => sum + r.cost,0);
  if (snapshot.points - reserved < reward.cost)
    throw new Error("Punts insuficients o reservats per altres peticions");
  tabs.requests.appendRow([
    mutationId,taskDate_(),"Rai",id,reward.cost,"pendent"
  ]);
}

function perform_(ops) {
  if (!Array.isArray(ops) || ops.length > 80) throw new Error("Lot de canvis massa gran");
  const shopping = sheet_(COMPRA, ["id", "producte", "comprat"]);
  const dinnerTab = sheet_(SOPARS, ["data", "sopar"]);
  ops.forEach(function (op) {
    if (!op || typeof op !== "object") throw new Error("Canvi incorrecte");
    switch (op.type) {
      case "grocery_set": upsertProduct_(shopping, op); break;
      case "grocery_delete": deleteProduct_(shopping, op); break;
      case "dinner_set": setDinner_(dinnerTab, op); break;
      case "task_set": taskSet_(op); break;
      case "task_delete": taskDelete_(op); break;
      case "task_complete": taskComplete_(op); break;
      case "reward_set": rewardSet_(op); break;
      case "reward_delete": rewardDelete_(op); break;
      case "reward_request": rewardRequest_(op); break;
      case "reward_redeem": throw new Error("Cal aprovar el premi amb el PIN a través de Tasques.gs");
      default: throw new Error("Acció no admesa");
    }
  });
}

/**
 * Galeria familiar integrada amb Fotos.gs, SENSE una segona carpeta ni pestanya Fotos.
 * photoFolder_() és la funció existent de Fotos.gs i fa servir PHOTO_FOLDER_ID.
 * Les rutes heretades photos_list/photos_get conserven PHOTO_READ_KEY_HASH;
 * les noves photo_list/photo_get fan servir la clau familiar KEY_HASH.
 */
function unifiedPhotoFormat_(mime) {
  return ["image/jpeg", "image/png", "image/webp"].includes(mime);
}

function familyDrivePhotos_() {
  if (typeof photoFolder_ !== "function") throw new Error("Falta el fitxer Fotos.gs original");
  const folder = photoFolder_();
  const files = folder.getFiles(), photos = [];
  let seen = 0;
  while (files.hasNext() && seen < MAX_PHOTOS) {
    const file = files.next(); seen++;
    if (!unifiedPhotoFormat_(file.getMimeType()) || file.getSize() < 6 ||
        file.getSize() > PHOTO_MAX_BYTES) continue;
    photos.push({
      id: file.getId(), name: file.getName(),
      createdAt: file.getLastUpdated().toISOString(),
      mimeType: file.getMimeType(), size: file.getSize()
    });
  }
  return photos.sort(function(a,b) { return b.createdAt.localeCompare(a.createdAt); });
}

/** Comprovació no destructiva: no crea cap carpeta ni toca cap foto o Sheets. */
function prepararFotosFamiliars() {
  const photos = familyDrivePhotos_();
  Logger.log("Carpeta de Fotos.gs accessible. Fotos compatibles trobades: " +
    photos.length + ". Clau familiar i clau antiga de lectura intactes.");
}

/** Pujada JPEG reduïda, amb identificador estable en el nom per evitar duplicats. */
function photoUpload_(payload) {
  const id = validateId_(payload && payload.id);
  const folder = photoFolder_();
  const prefix = "marc_" + id + "_";
  const existing = familyDrivePhotos_().find(p => p.name.startsWith(prefix));
  if (existing) return existing;
  if (familyDrivePhotos_().length >= MAX_PHOTOS)
    throw new Error("La carpeta de fotos ha arribat al límit de 300 elements");

  const encoded = payload && payload.base64;
  if (typeof encoded !== "string" || encoded.length > 1480000 ||
      !/^[A-Za-z0-9+/]+={0,2}$/.test(encoded))
    throw new Error("JPEG massa gran o invàlid");
  const bytes = Utilities.base64Decode(encoded);
  if (bytes.length < 4 || bytes.length > PHOTO_UPLOAD_MAX_BYTES ||
      (bytes[0] & 255) !== 255 || (bytes[1] & 255) !== 216 ||
      (bytes[bytes.length - 2] & 255) !== 255 ||
      (bytes[bytes.length - 1] & 255) !== 217)
    throw new Error("Cal una imatge JPEG de fins a 1,1 MB");

  const originalName = String(payload && payload.name || "fotografia")
    .replace(/[^A-Za-z0-9._ -]/g, "_").slice(0, 60)
    .replace(/\.(jpe?g|png|webp)$/i,"");
  const name = prefix + (originalName || "fotografia") + ".jpg";
  const file = folder.createFile(Utilities.newBlob(bytes,"image/jpeg",name));
  return { id: file.getId(), name: file.getName(), mimeType: "image/jpeg",
    createdAt: file.getLastUpdated().toISOString(), size: file.getSize() };
}

/** Només permet llegir un fitxer que sigui dins de la carpeta familiar. */
function photoGet_(payload) {
  const id = validateId_(payload && payload.id);
  const photo = familyDrivePhotos_().find(p => p.id === id);
  if (!photo) throw new Error("Fotografia no disponible a la carpeta familiar");
  const file = DriveApp.getFileById(id);
  return { id: id, name: photo.name, mimeType: photo.mimeType,
    base64: Utilities.base64Encode(file.getBlob().getBytes()) };
}

/** Com a Fotos.gs, la baixa només afecta un fitxer real de la carpeta. */
function photoDelete_(payload) {
  const id = validateId_(payload && payload.id);
  if (!familyDrivePhotos_().some(p => p.id === id)) return;
  DriveApp.getFileById(id).setTrashed(true);
}

function photoAction_(action,payload) {
  if (action === "photo_list") return {ok:true,photos:familyDrivePhotos_()};
  if (action === "photo_get") return {ok:true,photo:photoGet_(payload)};
  if (action !== "photo_upload" && action !== "photo_delete")
    return {ok:false,error:"Acció de fotografia no permesa"};
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(20000);
    const uploaded = action === "photo_upload" ? photoUpload_(payload) :
      (photoDelete_(payload),null);
    return {ok:true,photo:uploaded,photos:familyDrivePhotos_()};
  } finally {
    if(lock.hasLock()) lock.releaseLock();
  }
}

/** Únic punt d'entrada per al panell mòbil i per a les peticions Android. */
function apiAction(key, action, payload) {
  if (!authenticated_(key)) return { ok: false, error: "Clau de sincronització incorrecta" };
  if (action !== "sync") {
    try { return photoAction_(action, payload || {}); }
    catch (ex) { return { ok: false, error: String(ex.message || "Error de fotografies").slice(0, 160) }; }
  }
  const ops = (payload && payload.ops) || [];
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(20000);
    perform_(ops);
    SpreadsheetApp.flush();
    return { ok: true, data: readSnapshot_() };
  } catch (ex) {
    return { ok: false, error: String(ex.message || "Error intern").slice(0, 160) };
  } finally {
    if (lock.hasLock()) lock.releaseLock();
  }
}

/** POST JSON des de l'APK. El token va al cos, mai als paràmetres de l'URL. */
function doPost(e) {
  let response;
  try {
    const raw = e && e.postData && e.postData.contents;
    if (!raw || raw.length > 1600000) throw new Error("Petició buida o massa gran");
    const request = JSON.parse(raw);
    if (request.action !== "photo_upload" && raw.length > 60000) throw new Error("Petició massa gran");
    // Compatibilitat amb el servei real:
    // el projecte existent exposa familyTasksAction i photoApiAction_ en altres fitxers.
    // Cal conservar les rutes mentre hi hagi mòbils amb la versió anterior.
    if (request.action === "family_tasks") {
      if (typeof familyTasksAction !== "function") {
        throw new Error("Cal conservar el fitxer de tasques del projecte existent");
      }
      const inner = request.payload || {};
      response = familyTasksAction(request.key, inner.action, inner.data || {});
    } else if (request.action === "photos_list" || request.action === "photos_get") {
      if (typeof photoApiAction_ !== "function") {
        throw new Error("Cal conservar Fotos.gs del projecte existent");
      }
      response = photoApiAction_(request.key, request.action, request.payload || {});
    } else {
      response = apiAction(request.key, request.action, request.payload || {});
    }
  } catch (ex) {
    response = { ok: false, error: String(ex.message || "Petició incorrecta").slice(0, 160) };
  }
  return ContentService.createTextOutput(JSON.stringify(response)).setMimeType(ContentService.MimeType.JSON);
}

/** Interfície adaptada al mòbil; sense accedir a dades fins a validar la clau. */
function doGet() {
  return HtmlService.createHtmlOutputFromFile("Mobile")
    .setTitle("Marc Familiar · Compra, sopars, tasques i fotos")
    .addMetaTag("viewport", "width=device-width, initial-scale=1");
}

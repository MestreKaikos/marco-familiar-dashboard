/**
 * Marc Familiar · sincronització via Google Sheets, sense servidor propi.
 *
 * Instal·lació: crea un full de càlcul privat -> Extensions -> Apps Script ->
 * enganxa Code.gs i Mobile.html -> executa setup_() a l'editor i copia
 * la clau mostrada al registre -> Implementa -> Aplicació web.
 *
 * El Web App ha d'executar-se com el propietari, i accepta peticions externes
 * NÚMÉS amb la clau aleatòria, mai per l'URL. No compartir la clau al GitHub.
 * La clau no es publica mai en HTML ni a cap full de càlcul.
 */

const COMPRA = "Compra";
const SOPARS = "Sopars";
const MAX_PRODUCTS = 200;
const MAX_DINNERS = 366;

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
  return { groceries: groceries, dinners: dinners };
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
      default: throw new Error("Acció no admesa");
    }
  });
}

/** Únic punt d'entrada per al panell mòbil i per a les peticions Android. */
function apiAction(key, action, payload) {
  if (!authenticated_(key)) return { ok: false, error: "Clau de sincronització incorrecta" };
  if (action !== "sync") return { ok: false, error: "Acció no permesa" };
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
    if (!raw || raw.length > 60000) throw new Error("Petició buida o massa gran");
    const request = JSON.parse(raw);
    response = apiAction(request.key, request.action, request.payload || {});
  } catch (ex) {
    response = { ok: false, error: String(ex.message || "Petició incorrecta").slice(0, 160) };
  }
  return ContentService.createTextOutput(JSON.stringify(response)).setMimeType(ContentService.MimeType.JSON);
}

/** Interfície adaptada al mòbil; sense accedir a dades fins a validar la clau. */
function doGet() {
  return HtmlService.createHtmlOutputFromFile("Mobile")
    .setTitle("Marc Familiar · Compra i sopars")
    .addMetaTag("viewport", "width=device-width, initial-scale=1");
}

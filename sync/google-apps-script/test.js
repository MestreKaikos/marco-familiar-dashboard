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

class FakeSheet {
  constructor(name) { this.name = name; this.rows = []; }
  getLastRow() { return this.rows.length; }
  setFrozenRows() {}
  getRange(startRow, startCol, numRows, numCols) {
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
    computeDigest: (alg, value) => Array.from(crypto.createHash(alg).update(value).digest()).map(x => x >= 128 ? x - 256 : x)
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

const http = context.doPost({postData:{contents:JSON.stringify({key:secret,action:"sync",payload:{ops:[]}})}});
assert.equal(JSON.parse(http.text).ok, true);
const page = context.doGet();
assert.equal(page.name, "Mobile");
console.log("OK · autenticació, compra, sopars, idempotència, bloqueig i API POST.");

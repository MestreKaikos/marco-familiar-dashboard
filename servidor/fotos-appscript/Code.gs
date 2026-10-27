/**
 * Marc Familiar · bústia de fotografies a Google Drive.
 * Desplega com a Web App (executar com a propietari, accés: qualsevol persona).
 * IMPORTANT: com que el web app és públic, la contrasenya forta PHOTO_UPLOAD_KEY
 * protegeix les crides; no és equivalent a una autenticació de compte.
 * Per a dades molt sensibles, fer servir un backend amb autenticació real.
 *
 * Propietats de script obligatòries:
 * PHOTO_FOLDER_ID: ID de la carpeta privada de Drive
 * PHOTO_UPLOAD_KEY: clau aleatòria llarga (mínim 32 caràcters)
 * PHOTO_READ_KEY: clau diferent, aleatòria i llarga (mínim 32 caràcters)
 */
const MAX_BYTES = 5 * 1024 * 1024;
const MAX_ITEMS = 300;
const MIME = Object.freeze({'image/jpeg':'.jpg','image/png':'.png','image/webp':'.webp'});

function doGet() {
  return HtmlService.createHtmlOutputFromFile('Index')
    .setTitle('Marc Familiar · Envia fotos')
    .addMetaTag('viewport','width=device-width, initial-scale=1');
}

function settings_() {
  const p = PropertiesService.getScriptProperties();
  const folderId = p.getProperty('PHOTO_FOLDER_ID');
  const uploadKey = p.getProperty('PHOTO_UPLOAD_KEY');
  const readKey = p.getProperty('PHOTO_READ_KEY');
  if (!folderId || !uploadKey || !readKey || uploadKey.length < 32 || readKey.length < 32 || uploadKey === readKey) {
    throw new Error('Configuració incompleta: revisa les propietats del projecte.');
  }
  return {folder: DriveApp.getFolderById(folderId), uploadKey, readKey};
}

function authorized_(supplied, expected) {
  if (typeof supplied !== 'string' || supplied.length !== expected.length) return false;
  // Comprovació sense sortida anticipada en el primer caràcter diferent.
  let diff = 0;
  for (let i=0; i<expected.length; i++) diff |= supplied.charCodeAt(i) ^ expected.charCodeAt(i);
  return diff === 0;
}

/** Cridat exclusivament des de la pàgina HtmlService amb google.script.run. */
function uploadPhoto(request) {
  const cfg = settings_();
  if (!request || !authorized_(request.key, cfg.uploadKey)) throw new Error('Clau de pujada incorrecta.');
  const name = String(request.name || 'fotografia').replace(/[^\p{L}\p{N}._ -]/gu,'_').slice(0,90);
  const mime = String(request.mime || '');
  if (!Object.prototype.hasOwnProperty.call(MIME, mime)) throw new Error('Format no permès. Fes servir JPG, PNG o WebP.');
  const data = String(request.base64 || '');
  if (!/^[A-Za-z0-9+/]*={0,2}$/.test(data) || data.length > Math.ceil(MAX_BYTES*4/3)+4) throw new Error('Fitxer massa gran o invàlid.');
  const bytes = Utilities.base64Decode(data);
  if (!bytes.length || bytes.length > MAX_BYTES) throw new Error('La foto ha de pesar entre 1 byte i 5 MB.');
  // Validació bàsica de signatura per no confiar només en el MIME enviat.
  const head = bytes.slice(0,12).map(b=>b&255);
  const jpg = head[0]===255 && head[1]===216 && head[2]===255;
  const png = head.slice(0,8).join(',')==='137,80,78,71,13,10,26,10';
  const webp = String.fromCharCode(...head.slice(0,4))==='RIFF' && String.fromCharCode(...head.slice(8,12))==='WEBP';
  if (!(mime==='image/jpeg'&&jpg || mime==='image/png'&&png || mime==='image/webp'&&webp)) throw new Error('El contingut no correspon al format indicat.');
  const lock = LockService.getScriptLock();
  lock.waitLock(15000);
  try {
    let count = 0;
    const files = cfg.folder.getFiles();
    while(files.hasNext() && count < MAX_ITEMS) { files.next(); count++; }
    if(count>=MAX_ITEMS) throw new Error('La carpeta ha arribat al límit de 300 fotografies. Revisa-la abans de continuar.');
    const safeName = name.replace(/\.(jpg|jpeg|png|webp)$/i,'') + MIME[mime];
    const stamp = Utilities.formatDate(new Date(),'UTC','yyyyMMdd_HHmmss');
    const blob = Utilities.newBlob(bytes, mime, stamp+'_'+Utilities.getUuid().slice(0,8)+'_'+safeName);
    const file = cfg.folder.createFile(blob);
    file.setDescription('Pujada des de Marc Familiar');
    // No usar setSharing: els fitxers hereten la privadesa de la carpeta.
    return {ok:true, id:file.getId(), name:file.getName()};
  } finally {lock.releaseLock();}
}

/** Manifest privat per a un futur client de sincronització Android. */
function listPhotos(key) {
  const cfg=settings_();
  if(!authorized_(key,cfg.readKey)) throw new Error('Clau de lectura incorrecta.');
  const items=[], iter=cfg.folder.getFiles();
  while(iter.hasNext() && items.length<MAX_ITEMS) {
    const file=iter.next();
    if(!Object.prototype.hasOwnProperty.call(MIME,file.getMimeType())) continue;
    items.push({id:file.getId(),name:file.getName(),updated:file.getLastUpdated().toISOString(),size:file.getSize()});
  }
  return items.sort((a,b)=>a.id.localeCompare(b.id));
}

/** Descarrega UNA imatge en base64; evitar retornar galeries senceres en una crida. */
function getPhoto(key,id) {
  const cfg=settings_();
  if(!authorized_(key,cfg.readKey)) throw new Error('Clau de lectura incorrecta.');
  if(!/^[A-Za-z0-9_-]{10,100}$/.test(String(id||''))) throw new Error('ID invàlid.');
  const file=DriveApp.getFileById(id);
  // No permetre consultar cap fitxer fora de la carpeta configurada.
  let belongs=false;
  const parents=file.getParents();
  while(parents.hasNext()) if(parents.next().getId()===cfg.folder.getId()) belongs=true;
  if(!belongs || !Object.prototype.hasOwnProperty.call(MIME,file.getMimeType()) || file.getSize()>MAX_BYTES) throw new Error('Foto no disponible.');
  return {mime:file.getMimeType(),base64:Utilities.base64Encode(file.getBlob().getBytes())};
}

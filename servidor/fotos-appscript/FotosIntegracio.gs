/**
 * Marc Familiar · Fotos.gs
 * Afegir aquest fitxer al projecte EXISTENT de Google Apps Script.
 * Reutilitza authenticated_() i digest_() del Code.gs existent.
 * No redefineix ni doGet(), ni doPost(), ni apiAction().
 * Propietats de script: PHOTO_FOLDER_ID i PHOTO_READ_KEY_HASH.
 */
const PHOTO_MAX_BYTES_ = 5 * 1024 * 1024;
const PHOTO_MAX_ITEMS_ = 300;
const PHOTO_TYPES_ = {'image/jpeg':'.jpg','image/png':'.png','image/webp':'.webp'};

function photoFolder_() {
  const id = PropertiesService.getScriptProperties().getProperty('PHOTO_FOLDER_ID');
  if (!id) throw new Error('Falta PHOTO_FOLDER_ID a Propietats de script.');
  return DriveApp.getFolderById(id);
}

/** Una sola vegada, des de l'editor; sense alterar KEY_HASH ni la clau familiar existent. */
function setupPhotos_() {
  photoFolder_(); // Valida els permisos de la carpeta de Drive.
  const props = PropertiesService.getScriptProperties();
  if (props.getProperty('PHOTO_READ_KEY_HASH')) {
    Logger.log('La clau de lectura de fotos ja existeix i no es mostra de nou.');
    return;
  }
  const key = Utilities.getUuid().replace(/-/g,'') + Utilities.getUuid().replace(/-/g,'');
  props.setProperty('PHOTO_READ_KEY_HASH',digest_(key));
  Logger.log('CLAU PRIVADA DE LECTURA DEL MARC (guardar fora de GitHub): ' + key);
}

function photoReaderAuthenticated_(key) {
  const expected = PropertiesService.getScriptProperties().getProperty('PHOTO_READ_KEY_HASH');
  if (!expected || typeof key !== 'string' || !/^[a-f0-9]{64}$/.test(key)) return false;
  const actual = digest_(key);
  let difference = 0;
  for (let i=0;i<expected.length;i++) difference |= expected.charCodeAt(i) ^ actual.charCodeAt(i);
  return difference === 0;
}

/** Pujades des de Mobile.html: empra exactament la clau familiar que ja fan servir compra i sopars. */
function uploadFamilyPhoto(key, request) {
  if (!authenticated_(key)) throw new Error('Clau familiar incorrecta.');
  if (!request || typeof request !== 'object') throw new Error('Fotografia invàlida.');
  const mime = String(request.mime || '');
  if (!Object.prototype.hasOwnProperty.call(PHOTO_TYPES_,mime)) throw new Error('Format no acceptat.');
  const b64 = String(request.base64 || '');
  if (!b64 || b64.length > Math.ceil(PHOTO_MAX_BYTES*4/3)+4 || !/^[A-Za-z0-9+/]*={0,2}$/.test(b64))
    throw new Error('Imatge massa gran o contingut invàlid.');
  const bytes = Utilities.base64Decode(b64);
  if (!bytes.length || bytes.length > PHOTO_MAX_BYTES_) throw new Error('Màxim 5 MB per foto.');
  const signature = bytes.slice(0,12).map(function(b){ return b & 255; });
  const jpg = signature[0]===255 && signature[1]===216 && signature[2]===255;
  const png = signature.slice(0,8).join(',')==='137,80,78,71,13,10,26,10';
  const webp = String.fromCharCode.apply(null,signature.slice(0,4))==='RIFF' &&
    String.fromCharCode.apply(null,signature.slice(8,12))==='WEBP';
  if (!(mime==='image/jpeg'&&jpg || mime==='image/png'&&png || mime==='image/webp'&&webp))
    throw new Error('El fitxer no coincideix amb el format declarat.');
  const name = String(request.name || 'foto').replace(/[^A-Za-z0-9._ -]/g,'_').slice(0,60)
    .replace(/\.(jpg|jpeg|png|webp)$/i,'');
  const lock=LockService.getScriptLock();
  lock.waitLock(20000);
  try {
    const folder=photoFolder_(), files=folder.getFiles();
    let count=0;while(files.hasNext() && count<PHOTO_MAX_ITEMS_) { files.next();count++; }
    if(count>=PHOTO_MAX_ITEMS_) throw new Error('Límit de 300 fotos; revisa la carpeta.');
    const filename=Utilities.formatDate(new Date(),'UTC','yyyyMMdd_HHmmss')+'_'+
      Utilities.getUuid().slice(0,8)+'_'+name+PHOTO_TYPES_[mime];
    const photo=folder.createFile(Utilities.newBlob(bytes,mime,filename));
    return {ok:true,name:photo.getName()};
  } finally { if(lock.hasLock())lock.releaseLock(); }
}

/**
 * API per a Android, cridada des del doPost EXISTENT:
 * if (request.action === 'photos_list' || request.action === 'photos_get')
 *     response = photoApiAction_(request.key,request.action,request.payload || {});
 */
function photoApiAction_(key,action,payload) {
  if (!photoReaderAuthenticated_(key)) return {ok:false,error:'Clau de lectura incorrecta'};
  try {
    const folder=photoFolder_();
    if(action==='photos_list') {
      const entries=[], files=folder.getFiles();
      while(files.hasNext() && entries.length<PHOTO_MAX_ITEMS_) {
        const f=files.next();
        if(!Object.prototype.hasOwnProperty.call(PHOTO_TYPES_,f.getMimeType()))continue;
        entries.push({id:f.getId(),name:f.getName(),size:f.getSize(),
          updated:f.getLastUpdated().toISOString()});
      }
      return {ok:true,data:entries.sort(function(a,b){return a.id.localeCompare(b.id);})};
    }
    if(action==='photos_get') {
      const id=String(payload && payload.id || '');
      if(!/^[A-Za-z0-9_-]{10,100}$/.test(id))throw new Error('ID invàlid');
      const f=DriveApp.getFileById(id);
      let inside=false, parents=f.getParents();
      while(parents.hasNext()) if(parents.next().getId()===folder.getId())inside=true;
      if(!inside || !Object.prototype.hasOwnProperty.call(PHOTO_TYPES_,f.getMimeType())
        || f.getSize()>PHOTO_MAX_BYTES_)throw new Error('Foto no disponible');
      return {ok:true,data:{mime:f.getMimeType(),
        base64:Utilities.base64Encode(f.getBlob().getBytes())}};
    }
    return {ok:false,error:'Acció no admesa'};
  } catch(_) {
    return {ok:false,error:'No s’ha pogut consultar la carpeta de fotos'};
  }
}

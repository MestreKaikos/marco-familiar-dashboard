/**
 * Transport segur PWA -> Apps Script. Sense Google cookies, CORS ni secrets al repositori.
 * La clau de 64 caràcters és la mateixa de la família i s'envia només en el cos HTTPS.
 */
const ALLOWED_METHODS=new Set(['apiAction','familyTasksAction','uploadFamilyPhoto']);
const TASK_ACTIONS=new Set(['tasks_snapshot','tasks_set','tasks_done','tasks_undo','rewards_set','rewards_claim','rewards_decide','points_adjust']);
const API_ACTIONS=new Set(['sync','photo_list','photo_get']);
const MAX_BODY=1_530_000;
const scriptUrl=process.env.MARC_APPS_SCRIPT_URL;

function reply(res,code,obj){
  res.setHeader('Cache-Control','private, no-store, max-age=0');
  res.setHeader('X-Content-Type-Options','nosniff');
  return res.status(code).json(obj);
}
function validUrl(raw){
  try{const u=new URL(raw);return u.protocol==='https:'&&u.hostname==='script.google.com'&&
    /^\/macros\/s\/[A-Za-z0-9_-]+\/exec$/.test(u.pathname)&&!u.search&&!u.hash;}
  catch{return false;}
}
export const config={api:{bodyParser:{sizeLimit:'2mb'}}};
export default async function handler(req,res){
  if(req.method!=='POST'){
    res.setHeader('Allow','POST');
    return reply(res,405,{ok:false,error:'Només peticions POST'});
  }
  if(!validUrl(scriptUrl))return reply(res,503,{ok:false,error:'Falta configurar el servei familiar'});
  const origin=req.headers.origin;
  // Protegeix d'enviaments des d'altres pàgines i evita facilitar CORS.
  if(origin){
    let allowed=false;
    try{
      const u=new URL(origin),host=(req.headers['x-forwarded-host']||req.headers.host||'').toLowerCase();
      allowed=u.protocol==='https:'&&u.host.toLowerCase()===host;
    }catch{}
    if(!allowed)return reply(res,403,{ok:false,error:'Origen no autoritzat'});
  }
  let input=req.body;
  if(typeof input==='string'){try{input=JSON.parse(input);}catch{return reply(res,400,{ok:false,error:'JSON invàlid'});}}
  if(!input||typeof input!=='object'||Array.isArray(input))return reply(res,400,{ok:false,error:'Petició invàlida'});
  const {method,args}=input;
  if(!ALLOWED_METHODS.has(method)||!Array.isArray(args)||args.length!==3&&method!=='uploadFamilyPhoto')
    return reply(res,400,{ok:false,error:'Acció no permesa'});
  const [key,action,payload]=args;
  if(typeof key!=='string'||!/^[0-9a-fA-F]{64}$/.test(key))return reply(res,401,{ok:false,error:'Clau familiar invàlida'});
  let request;
  if(method==='apiAction'){
    if(!API_ACTIONS.has(action))return reply(res,400,{ok:false,error:'Acció de dades no permesa'});
    request={key,action,payload:payload&&typeof payload==='object'?payload:{}};
  }else if(method==='familyTasksAction'){
    if(!TASK_ACTIONS.has(action))return reply(res,400,{ok:false,error:'Acció de tasques no permesa'});
    request={key,action:'family_tasks',payload:{action,data:payload&&typeof payload==='object'?payload:{}}};
  }else{
    // El navegador comprimeix a JPEG i el servidor original limita a 1,1 MB.
    const photo=args[1];
    if(!photo||typeof photo!=='object'||!photo.id||!photo.base64||
      String(photo.base64).length>1_400_000||!String(photo.id).match(/^[A-Za-z0-9_-]{1,80}$/))
      return reply(res,400,{ok:false,error:'Fotografia massa gran o invàlida'});
    request={key,action:'photo_upload',payload:{id:photo.id,name:String(photo.name||'fotografia').slice(0,80),base64:photo.base64}};
  }
  const serialized=JSON.stringify(request);
  if(Buffer.byteLength(serialized,'utf8')>MAX_BODY)return reply(res,413,{ok:false,error:'Petició massa gran'});
  try{
    const response=await fetch(scriptUrl,{
      method:'POST',headers:{'Content-Type':'application/json'},
      body:serialized,redirect:'follow',cache:'no-store',
      signal:AbortSignal.timeout(45000)
    });
    if(!response.ok)return reply(res,502,{ok:false,error:'El servei familiar no ha respost correctament'});
    const text=await response.text();
    let result;
    try{result=JSON.parse(text);}catch{return reply(res,502,{ok:false,error:'Resposta invàlida del servei familiar'});}
    if(!result||typeof result!=='object')return reply(res,502,{ok:false,error:'Resposta inesperada'});
    return reply(res,200,result);
  }catch{
    return reply(res,502,{ok:false,error:'No s’ha pogut connectar amb Google Apps Script'});
  }
}

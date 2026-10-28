/**
 * Marc Familiar · Tasques.gs
 * Complement compatible amb Code.gs, Mobile.html i Fotos.gs actuals.
 * No altera KEY_HASH ni redefineix doGet/doPost/apiAction.
 * MVP: tasques diàries de Rai, punts per tasques, recompenses sol·licitades.
 * L'adult valida o anul·la tasques via clau familiar (model de confiança compartida).
 */
const TASKS_TAB_='Tasques';
const TASK_DONE_TAB_='TasquesFetes';
const REWARDS_TAB_='Recompenses';
const REWARD_CLAIMS_TAB_='CanvisPunts';
const REWARD_REQUESTS_TAB_='PeticionsPremis';
const MAX_FAMILY_TASKS_=100;
const MAX_FAMILY_REWARDS_=50;

function familyTaskSheet_(name,header) {
  return sheet_(name,header);
}
function setupFamilyTasks() {
  familyTaskSheet_(TASKS_TAB_,['id','titol','punts','activa','frequencia','dies','franges']);
  familyTaskSheet_(TASK_DONE_TAB_,['data','tascaId','persona','punts','franja']);
  familyTaskSheet_(REWARDS_TAB_,['id','recompensa','cost','activa']);
  familyTaskSheet_(REWARD_CLAIMS_TAB_,['id','data','persona','recompensaId','cost']);
  familyTaskSheet_(REWARD_REQUESTS_TAB_,['id','data','persona','recompensaId','cost','estat']);
  Logger.log('Tasques familiars preparades; no s’ha modificat la clau de sincronització.');
}
function taskDate_(){
  return Utilities.formatDate(new Date(),Session.getScriptTimeZone()||'Europe/Madrid','yyyy-MM-dd');
}
function taskSchedule_(raw) {
  const frequency=String(raw.frequency||'daily');
  if(!['daily','weekdays','weekly','once','slots'].includes(frequency))throw Error('Repetició no vàlida');
  const days=String(raw.days||'').split(',').filter(Boolean);
  if(days.some(d=>!/^[1-7]$/.test(d)) || new Set(days).size!==days.length)throw Error('Dies no vàlids');
  if(['weekdays','weekly'].includes(frequency)&&!days.length)throw Error('Selecciona almenys un dia');
  const slots=frequency==='slots' ? String(raw.slots||'matí,vespre').split(',').map(x=>x.trim()) : ['única'];
  if(slots.length<1||slots.length>4||slots.some(x=>!['matí','migdia','tarda','vespre'].includes(x)) || new Set(slots).size!==slots.length)
    throw Error('Franges no vàlides');
  return {frequency,days:days.join(','),slots:slots.join(',')};
}
function taskOccursToday_(task,date) {
  if(task.frequency==='once') return !task.everDone;
  if(task.frequency==='daily'||task.frequency==='slots')return true;
  const day=(new Date(date+'T12:00:00Z').getUTCDay()+6)%7+1;
  return String(task.days||'').split(',').includes(String(day));
}
function familyTaskId_(id) {
  if(typeof id!=='string'||!/^[a-zA-Z0-9_-]{1,80}$/.test(id))throw Error('ID invàlid');
  return id;
}
function familyPositive_(n,max){
  n=Number(n);
  if(!Number.isInteger(n)||n<1||n>max)throw Error('Punts invàlids');
  return n;
}
function familyTasksSnapshot_(){
  const tasks=rows_(familyTaskSheet_(TASKS_TAB_,['id','titol','punts','activa','frequencia','dies','franges']),7)
    .filter(r=>r[0]&&r[1]).map(r=>({id:String(r[0]),title:String(r[1]),points:Number(r[2])||0,
      active:r[3]===true,frequency:String(r[4]||'daily'),days:String(r[5]||''),slots:String(r[6]||'única')}));
  const done=rows_(familyTaskSheet_(TASK_DONE_TAB_,['data','tascaId','persona','punts','franja']),5)
    .filter(r=>r[0]&&r[1]).map(r=>({date:String(r[0]),taskId:String(r[1]),kid:String(r[2]),points:Number(r[3])||0,slot:String(r[4]||'única')}));
  tasks.forEach(t=>{ t.everDone=done.some(d=>d.taskId===t.id); t.dueToday=t.active&&taskOccursToday_(t,taskDate_()); });
  const rewards=rows_(familyTaskSheet_(REWARDS_TAB_,['id','recompensa','cost','activa']),4)
    .filter(r=>r[0]&&r[1]).map(r=>({id:String(r[0]),title:String(r[1]),cost:Number(r[2])||0,active:r[3]===true}));
  const claims=rows_(familyTaskSheet_(REWARD_CLAIMS_TAB_,['id','data','persona','recompensaId','cost']),5)
    .filter(r=>r[0]).map(r=>({id:String(r[0]),date:String(r[1]),kid:String(r[2]),rewardId:String(r[3]),cost:Number(r[4])||0}));
  const requests=rows_(familyTaskSheet_(REWARD_REQUESTS_TAB_,['id','data','persona','recompensaId','cost','estat']),6)
    .filter(r=>r[0]).map(r=>({id:String(r[0]),date:String(r[1]),kid:String(r[2]),
      rewardId:String(r[3]),cost:Number(r[4])||0,status:String(r[5])}));
  const earned=done.filter(r=>r.kid==='Rai').reduce((s,r)=>s+r.points,0);
  const spent=claims.filter(r=>r.kid==='Rai').reduce((s,r)=>s+r.cost,0);
  return {kid:'Rai',today:taskDate_(),tasks:tasks,done:done,rewards:rewards,claims:claims,requests:requests,points:earned-spent};
}
/** Només per ús amb la clau familiar existent; no habilita canvis sense autenticació. */
function familyTasksAction(key,action,payload){
  if(!authenticated_(key))return {ok:false,error:'Clau familiar incorrecta'};
  const lock=LockService.getScriptLock();
  try{
    lock.waitLock(20000);
    setupFamilyTasks();
    payload=payload&&typeof payload==='object'?payload:{};
    const date=taskDate_();
    if(action==='tasks_set'){
      const id=familyTaskId_(payload.id);
      const title=safeText_(payload.title,100);
      const points=familyPositive_(payload.points,100);
      const schedule=taskSchedule_(payload);
      if(!title)throw Error('Falta el nom de la tasca');
      const tab=familyTaskSheet_(TASKS_TAB_,['id','titol','punts','activa']);
      const rows=rows_(tab,4),index=rows.findIndex(r=>r[0]===id);
      if(index<0){if(rows.length>=MAX_FAMILY_TASKS_)throw Error('Massa tasques');tab.appendRow([id,title,points,true,schedule.frequency,schedule.days,schedule.slots]);}
      else tab.getRange(index+2,1,1,7).setValues([[id,title,points,payload.active!==false,schedule.frequency,schedule.days,schedule.slots]]);
    }else if(action==='tasks_done'){
      const id=familyTaskId_(payload.id),snapshot=familyTasksSnapshot_();
      const task=snapshot.tasks.find(t=>t.id===id&&t.active);
      if(!task||!task.dueToday)throw Error('Aquesta tasca no toca avui');
      const slot=String(payload.slot||'única');
      if(!task.slots.split(',').includes(slot))throw Error('Franja no vàlida');
      if(!snapshot.done.some(d=>d.taskId===id&&d.date===date&&d.kid==='Rai'&&d.slot===slot))
        familyTaskSheet_(TASK_DONE_TAB_,['data','tascaId','persona','punts','franja']).appendRow([date,id,'Rai',task.points,slot]);
    }else if(action==='tasks_undo'){
      const id=familyTaskId_(payload.id),slot=String(payload.slot||'única'),tab=familyTaskSheet_(TASK_DONE_TAB_,['data','tascaId','persona','punts','franja']);
      const rows=rows_(tab,5),idx=rows.findIndex(r=>r[0]===date&&r[1]===id&&r[2]==='Rai'&&String(r[4]||'única')===slot);
      if(idx>=0)tab.deleteRow(idx+2);
    }else if(action==='rewards_set'){
      const id=familyTaskId_(payload.id),title=safeText_(payload.title,100),cost=familyPositive_(payload.cost,10000);
      if(!title)throw Error('Falta el nom de la recompensa');
      const tab=familyTaskSheet_(REWARDS_TAB_,['id','recompensa','cost','activa']);
      const rows=rows_(tab,4),idx=rows.findIndex(r=>r[0]===id);
      if(idx<0){if(rows.length>=MAX_FAMILY_REWARDS_)throw Error('Massa recompenses');tab.appendRow([id,title,cost,true]);}
      else tab.getRange(idx+2,1,1,4).setValues([[id,title,cost,payload.active!==false]]);
    }else if(action==='rewards_claim'){
      const id=familyTaskId_(payload.id),snapshot=familyTasksSnapshot_();
      const reward=snapshot.rewards.find(r=>r.id===id&&r.active);
      if(!reward)throw Error('Recompensa no disponible');
      if(snapshot.points<reward.cost)throw Error('No hi ha prou punts');
      const pending=snapshot.requests.filter(r=>r.status==='pendent'&&r.kid==='Rai')
        .reduce((s,r)=>s+r.cost,0);
      if(snapshot.points-pending<reward.cost)throw Error('Hi ha punts reservats per altres peticions');
      const token=Utilities.getUuid();
      familyTaskSheet_(REWARD_REQUESTS_TAB_,['id','data','persona','recompensaId','cost','estat'])
        .appendRow([token,date,'Rai',id,reward.cost,'pendent']);
    }else if(action==='rewards_decide'){
      const pin=String(payload.pin||'');
      const pinHash=PropertiesService.getScriptProperties().getProperty('FAMILY_ADULT_PIN_HASH');
      if(!pinHash || !/^[0-9]{4,8}$/.test(pin) || digest_(pin)!==pinHash)
        throw Error('PIN adult incorrecte');
      const id=familyTaskId_(payload.id);
      const tab=familyTaskSheet_(REWARD_REQUESTS_TAB_,['id','data','persona','recompensaId','cost','estat']);
      const requests=rows_(tab,6),idx=requests.findIndex(r=>r[0]===id);
      if(idx<0||requests[idx][5]!=='pendent')throw Error('Petició no disponible');
      const entry=requests[idx],approve=payload.approve===true;
      if(approve){
        const snapshot=familyTasksSnapshot_();
        if(snapshot.points<Number(entry[4]))throw Error('Ja no hi ha prou punts');
        familyTaskSheet_(REWARD_CLAIMS_TAB_,['id','data','persona','recompensaId','cost'])
          .appendRow([id,date,'Rai',entry[3],Number(entry[4])]);
      }
      tab.getRange(idx+2,6).setValue(approve?'aprovat':'rebutjat');
    }else if(action!=='tasks_snapshot'){
      throw Error('Acció no admesa');
    }
    SpreadsheetApp.flush();
    return {ok:true,data:familyTasksSnapshot_()};
  }catch(ex){return {ok:false,error:String(ex.message||'Error de tasques').slice(0,160)};}
  finally{if(lock.hasLock())lock.releaseLock();}
}

/** Executar manualment UNA vegada amb un PIN temporal només des de l'editor Apps Script.
 * Canvia el valor d'aquesta funció temporal per un PIN nou, executa-la i elimina-la.
 * No publiquis el PIN en el repositori ni al full.
 */
function configurarPinAdult(pin) {
  if(typeof pin!=='string'||!/^[0-9]{4,8}$/.test(pin))throw Error('PIN de 4 a 8 xifres');
  PropertiesService.getScriptProperties().setProperty('FAMILY_ADULT_PIN_HASH',digest_(pin));
  Logger.log('PIN adult configurat. No es mostra ni es desa en clar.');
}

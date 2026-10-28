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
const MAX_FAMILY_TASKS_=100;
const MAX_FAMILY_REWARDS_=50;

function familyTaskSheet_(name,header) {
  return sheet_(name,header);
}
function setupFamilyTasks() {
  familyTaskSheet_(TASKS_TAB_,['id','titol','punts','activa']);
  familyTaskSheet_(TASK_DONE_TAB_,['data','tascaId','persona','punts']);
  familyTaskSheet_(REWARDS_TAB_,['id','recompensa','cost','activa']);
  familyTaskSheet_(REWARD_CLAIMS_TAB_,['id','data','persona','recompensaId','cost']);
  Logger.log('Tasques familiars preparades; no s’ha modificat la clau de sincronització.');
}
function taskDate_(){
  return Utilities.formatDate(new Date(),Session.getScriptTimeZone()||'Europe/Madrid','yyyy-MM-dd');
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
  const tasks=rows_(familyTaskSheet_(TASKS_TAB_,['id','titol','punts','activa']),4)
    .filter(r=>r[0]&&r[1]).map(r=>({id:String(r[0]),title:String(r[1]),points:Number(r[2])||0,active:r[3]===true}));
  const done=rows_(familyTaskSheet_(TASK_DONE_TAB_,['data','tascaId','persona','punts']),4)
    .filter(r=>r[0]&&r[1]).map(r=>({date:String(r[0]),taskId:String(r[1]),kid:String(r[2]),points:Number(r[3])||0}));
  const rewards=rows_(familyTaskSheet_(REWARDS_TAB_,['id','recompensa','cost','activa']),4)
    .filter(r=>r[0]&&r[1]).map(r=>({id:String(r[0]),title:String(r[1]),cost:Number(r[2])||0,active:r[3]===true}));
  const claims=rows_(familyTaskSheet_(REWARD_CLAIMS_TAB_,['id','data','persona','recompensaId','cost']),5)
    .filter(r=>r[0]).map(r=>({id:String(r[0]),date:String(r[1]),kid:String(r[2]),rewardId:String(r[3]),cost:Number(r[4])||0}));
  const earned=done.filter(r=>r.kid==='Rai').reduce((s,r)=>s+r.points,0);
  const spent=claims.filter(r=>r.kid==='Rai').reduce((s,r)=>s+r.cost,0);
  return {kid:'Rai',today:taskDate_(),tasks:tasks,done:done,rewards:rewards,claims:claims,points:earned-spent};
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
      if(!title)throw Error('Falta el nom de la tasca');
      const tab=familyTaskSheet_(TASKS_TAB_,['id','titol','punts','activa']);
      const rows=rows_(tab,4),index=rows.findIndex(r=>r[0]===id);
      if(index<0){if(rows.length>=MAX_FAMILY_TASKS_)throw Error('Massa tasques');tab.appendRow([id,title,points,true]);}
      else tab.getRange(index+2,1,1,4).setValues([[id,title,points,payload.active!==false]]);
    }else if(action==='tasks_done'){
      const id=familyTaskId_(payload.id),snapshot=familyTasksSnapshot_();
      const task=snapshot.tasks.find(t=>t.id===id&&t.active);
      if(!task)throw Error('Tasca no disponible');
      if(!snapshot.done.some(d=>d.taskId===id&&d.date===date&&d.kid==='Rai'))
        familyTaskSheet_(TASK_DONE_TAB_,['data','tascaId','persona','punts']).appendRow([date,id,'Rai',task.points]);
    }else if(action==='tasks_undo'){
      const id=familyTaskId_(payload.id),tab=familyTaskSheet_(TASK_DONE_TAB_,['data','tascaId','persona','punts']);
      const rows=rows_(tab,4),idx=rows.findIndex(r=>r[0]===date&&r[1]===id&&r[2]==='Rai');
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
      const token=Utilities.getUuid();
      familyTaskSheet_(REWARD_CLAIMS_TAB_,['id','data','persona','recompensaId','cost'])
        .appendRow([token,date,'Rai',id,reward.cost]);
    }else if(action!=='tasks_snapshot'){
      throw Error('Acció no admesa');
    }
    SpreadsheetApp.flush();
    return {ok:true,data:familyTasksSnapshot_()};
  }catch(ex){return {ok:false,error:String(ex.message||'Error de tasques').slice(0,160)};}
  finally{if(lock.hasLock())lock.releaseLock();}
}

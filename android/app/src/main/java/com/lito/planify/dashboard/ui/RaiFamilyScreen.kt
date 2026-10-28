package com.lito.planify.dashboard.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private data class RaiChore(val id:String,val title:String,val points:Int,val slot:String,val done:Boolean)
private data class RaiPrize(val id:String,val title:String,val cost:Int,val pending:Boolean)
private data class RaiSnapshot(val points:Int,val chores:List<RaiChore>,val prizes:List<RaiPrize>)
private object RaiApi {
    private val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS)
        .readTimeout(45,TimeUnit.SECONDS).followRedirects(true).build()
    suspend fun call(url:String,key:String,action:String,payload:JSONObject=JSONObject()):JSONObject =
        withContext(Dispatchers.IO) {
            val u=url.trim().toHttpUrlOrNull()
                ?: error("URL incorrecta")
            require(u.isHttps && u.host=="script.google.com" && u.encodedPath.endsWith("/exec")) { "Cal l'URL /exec d'Apps Script" }
            require(key.matches(Regex("[a-fA-F0-9]{64}"))) { "Clau familiar incorrecta" }
            val body=JSONObject().put("action","family_tasks").put("key",key)
                .put("payload",JSONObject().put("action",action).put("data",payload))
            val request=Request.Builder().url(u).post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                require(response.isSuccessful && response.request.url.isHttps) { "No es pot consultar el servidor" }
                val raw=response.body?.string().orEmpty()
                require(raw.length<2_000_000) { "Resposta massa gran" }
                JSONObject(raw).also { require(it.optBoolean("ok")) { it.optString("error","Error de sincronització") } }
                    .getJSONObject("data")
            }
        }
    fun parse(data:JSONObject):RaiSnapshot {
        val date=data.getString("today")
        val done=data.getJSONArray("done")
        val completed=mutableSetOf<String>()
        for(i in 0 until done.length()) {
            val d=done.getJSONObject(i)
            if(d.optString("date")==date)completed.add(d.optString("taskId")+"|"+d.optString("slot","única"))
        }
        val chores=mutableListOf<RaiChore>()
        val tasks=data.getJSONArray("tasks")
        for(i in 0 until tasks.length()) {
            val task=tasks.getJSONObject(i)
            if(!task.optBoolean("active")||!task.optBoolean("dueToday"))continue
            for(slot in task.optString("slots","única").split(",")) {
                val id=task.getString("id")
                chores.add(RaiChore(id,task.getString("title"),task.optInt("points"),slot,completed.contains("$id|$slot")))
            }
        }
        val pending=mutableSetOf<String>()
        val requests=data.getJSONArray("requests")
        for(i in 0 until requests.length()){
            val r=requests.getJSONObject(i)
            if(r.optString("status")=="pendent")pending.add(r.optString("rewardId"))
        }
        val prizes=mutableListOf<RaiPrize>()
        val rewards=data.getJSONArray("rewards")
        for(i in 0 until rewards.length()) {
            val r=rewards.getJSONObject(i)
            if(r.optBoolean("active"))prizes.add(RaiPrize(r.getString("id"),r.getString("title"),r.optInt("cost"),pending.contains(r.getString("id"))))
        }
        return RaiSnapshot(data.optInt("points"),chores,prizes)
    }
}

/** Nova família compartida; no altera el magatzem local antic ni les fotos. */
@Composable
fun RaiFamilyScreen() {
    val context=LocalContext.current
    val prefs=remember(context){context.getSharedPreferences("marc_rai_sync",Context.MODE_PRIVATE)}
    var url by remember { mutableStateOf(prefs.getString("url","").orEmpty()) }
    var key by remember { mutableStateOf(prefs.getString("key","").orEmpty()) }
    var showQr by remember { mutableStateOf(false) }
    var showDirect by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<RaiSnapshot?>(null) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var seq by remember { mutableIntStateOf(0) }
    var operation by remember { mutableStateOf<Pair<String,JSONObject>?>(null) }

    LaunchedEffect(url,key,seq) {
        if(url.isBlank()||key.isBlank())return@LaunchedEffect
        while(true) {
            try {
                busy=true
                val pending=operation
                val json=RaiApi.call(url,key,pending?.first?:"tasks_snapshot",pending?.second?:JSONObject())
                snapshot=RaiApi.parse(json)
                operation=null
                error=""
            }catch(e:Exception) {
                error=e.message?:"No s'ha pogut sincronitzar"
                operation=null
            }finally{busy=false}
            delay(60_000L)
        }
    }
    fun action(name:String,payload:JSONObject) {
        if(busy)return
        operation=name to payload
        seq++
    }
    Column(Modifier.fillMaxSize().padding(28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Text("⭐ Tasques d'en Rai",style=com.lito.planify.dashboard.ui.theme.Type.Title)
            OutlinedButton(onClick={showQr=!showQr}){Text(if(showQr)"Tancar QR" else "Connectar amb QR")}
            OutlinedButton(onClick={showDirect=true}){Text("Configurar")}
            OutlinedButton(onClick={seq++}){Text("Actualitzar")}
        }
        if(showQr) {
            RaiPairingView { newUrl,newKey ->
                url=newUrl;key=newKey
                showQr=false;seq++
            }
        }
        if(showDirect) {
            RaiDirectSetup(
                onDismiss = { showDirect = false },
                onSaved = { newUrl,newKey ->
                    url = newUrl; key = newKey
                    showQr = false; showDirect = false
                    error = ""; seq++
                }
            )
        }
        if(error.isNotBlank())Text(error,color=androidx.compose.ui.graphics.Color.Red)
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        val current=snapshot
        if(current==null) {
            Text("Prem Configurar per connectar la família des d’aquest mateix mòbil, o Connectar amb QR per vincular un altre dispositiu.")
        } else {
            Text("⭐ ${current.points} punts",style=com.lito.planify.dashboard.ui.theme.Type.Large)
            Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Avui",style=com.lito.planify.dashboard.ui.theme.Type.Large)
                    LazyColumn {
                        items(current.chores){ chore ->
                            Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text(chore.title+(if(chore.slot=="única")"" else " · ${chore.slot}")+" (+${chore.points})",modifier=Modifier.weight(1f))
                                Button(enabled=!busy,onClick={
                                    action(if(chore.done)"tasks_undo" else "tasks_done",JSONObject().put("id",chore.id).put("slot",chore.slot))
                                }) { Text(if(chore.done)"↩ Desfer" else "✓ Feta") }
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Recompenses",style=com.lito.planify.dashboard.ui.theme.Type.Large)
                    LazyColumn {
                        items(current.prizes){prize->
                            Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically) {
                                Text("${prize.title} · ${prize.cost} punts",modifier=Modifier.weight(1f))
                                Button(enabled=!busy&&!prize.pending&&current.points>=prize.cost,onClick={
                                    action("rewards_claim",JSONObject().put("id",prize.id))
                                }){Text(if(prize.pending)"Pendent" else "Demanar")}
                            }
                        }
                    }
                }
            }
        }
    }
}

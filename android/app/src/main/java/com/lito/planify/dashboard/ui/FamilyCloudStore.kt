package com.lito.planify.dashboard.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Única font de dades del marc: mateix Apps Script / Sheets que la web mòbil.
 * Viu a DashboardApp, no a les pantalles: no es reinicia en canviar de pestanya.
 * Les últimes dades vàlides es conserven al mòbil per a ús sense xarxa.
 */
internal data class FamilyGrocery(val id:String, val name:String, val bought:Boolean)
internal data class FamilyCloudData(
    val groceries:List<FamilyGrocery> = emptyList(),
    val dinners:Map<String,String> = emptyMap()
)
internal data class FamilyCloudChore(
    val id:String,val title:String,val points:Int,val slot:String,val done:Boolean
)
internal data class FamilyCloudReward(
    val id:String,val title:String,val cost:Int,val pending:Boolean
)
internal data class FamilyCloudTasks(
    val points:Int,val chores:List<FamilyCloudChore>,val rewards:List<FamilyCloudReward>,
    val today:String
)

internal class FamilyCloudStore(context:Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("marc_rai_sync",Context.MODE_PRIVATE)
    private val cache = app.getSharedPreferences("marc_cloud_cache",Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12,TimeUnit.SECONDS)
        .readTimeout(40,TimeUnit.SECONDS)
        .followRedirects(true).build()
    var data by mutableStateOf<FamilyCloudData?>(null)
        private set
    var tasks by mutableStateOf<FamilyCloudTasks?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var working by mutableStateOf(false)
        private set
    var error by mutableStateOf("")
        private set
    var lastSync by mutableStateOf(0L)
        private set

    init {
        runCatching {
            cache.getString("family_data",null)?.let { data = parseFamily(JSONObject(it)) }
            cache.getString("family_tasks",null)?.let { tasks = parseTasks(JSONObject(it)) }
        }
    }
    val connected:Boolean
        get() = prefs.getString("url","").orEmpty().isNotBlank() &&
            prefs.getString("key","").orEmpty().isNotBlank()

    private suspend fun call(action:String,payload:JSONObject):JSONObject = withContext(Dispatchers.IO) {
        val url = prefs.getString("url","").orEmpty().trim()
        val key = prefs.getString("key","").orEmpty().trim()
        val endpoint = url.toHttpUrlOrNull() ?: error("Configura la URL d'Apps Script a Família")
        require(endpoint.isHttps && endpoint.host=="script.google.com" &&
            endpoint.encodedPath.endsWith("/exec")) { "La URL d'Apps Script no és correcta" }
        require(key.matches(Regex("[a-fA-F0-9]{64}"))) { "La clau familiar no és correcta" }
        val body = JSONObject().put("action",action).put("key",key).put("payload",payload)
        val request = Request.Builder().url(endpoint)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        client.newCall(request).execute().use { result ->
            require(result.isSuccessful && result.request.url.isHttps) {
                "No s'ha pogut contactar amb Apps Script"
            }
            val raw = result.body?.string().orEmpty()
            require(raw.length < 2_000_000) { "Resposta del servidor massa gran" }
            val decoded = JSONObject(raw)
            require(decoded.optBoolean("ok")) {
                decoded.optString("error","Error de sincronització")
            }
            decoded.getJSONObject("data")
        }
    }
    private fun parseFamily(json:JSONObject):FamilyCloudData {
        val shopping = json.optJSONArray("groceries") ?: JSONArray()
        val items = (0 until shopping.length()).mapNotNull { index ->
            val row=shopping.optJSONObject(index) ?: return@mapNotNull null
            val id=row.optString("id")
            val name=row.optString("name")
            if(id.isBlank() || name.isBlank())null
            else FamilyGrocery(id,name,row.optBoolean("bought",false))
        }
        val dinners = json.optJSONObject("dinners") ?: JSONObject()
        val meals = mutableMapOf<String,String>()
        val iterator=dinners.keys()
        while(iterator.hasNext()) {
            val date=iterator.next()
            val value=dinners.optString(date).trim()
            if(value.isNotBlank()) meals[date]=value
        }
        return FamilyCloudData(items,meals)
    }
    private fun parseTasks(json:JSONObject):FamilyCloudTasks {
        val today=json.optString("today",LocalDate.now().toString())
        val completed=mutableSetOf<String>()
        val done=json.optJSONArray("done") ?: JSONArray()
        for(i in 0 until done.length()) {
            val row=done.optJSONObject(i) ?: continue
            if(row.optString("date")==today && row.optString("kid","Rai")=="Rai") {
                completed.add(row.optString("taskId")+"|"+row.optString("slot","única"))
            }
        }
        val chores=mutableListOf<FamilyCloudChore>()
        val list=json.optJSONArray("tasks") ?: JSONArray()
        for(i in 0 until list.length()) {
            val task=list.optJSONObject(i) ?: continue
            if(!task.optBoolean("active") || !task.optBoolean("dueToday")) continue
            val id=task.optString("id")
            val title=task.optString("title")
            if(id.isBlank() || title.isBlank())continue
            task.optString("slots","única").split(",").filter { it.isNotBlank() }.forEach { slot ->
                chores.add(FamilyCloudChore(id,title,task.optInt("points"),slot,
                    completed.contains("$id|$slot")))
            }
        }
        val pending=mutableSetOf<String>()
        val req=json.optJSONArray("requests") ?: JSONArray()
        for(i in 0 until req.length()) {
            val r=req.optJSONObject(i) ?: continue
            if(r.optString("status")=="pendent")pending.add(r.optString("rewardId"))
        }
        val rewards=mutableListOf<FamilyCloudReward>()
        val prizes=json.optJSONArray("rewards") ?: JSONArray()
        for(i in 0 until prizes.length()) {
            val prize=prizes.optJSONObject(i) ?: continue
            if(!prize.optBoolean("active"))continue
            val id=prize.optString("id")
            if(id.isNotBlank()) rewards.add(FamilyCloudReward(id,prize.optString("title"),
                prize.optInt("cost"),pending.contains(id)))
        }
        return FamilyCloudTasks(json.optInt("points"),chores,rewards,today)
    }
    private fun updateFamily(json:JSONObject) {
        data=parseFamily(json)
        cache.edit().putString("family_data",json.toString()).apply()
        lastSync=System.currentTimeMillis()
        error=""
    }
    private fun updateTasks(json:JSONObject) {
        tasks=parseTasks(json)
        cache.edit().putString("family_tasks",json.toString()).apply()
        lastSync=System.currentTimeMillis()
        error=""
    }

    suspend fun refresh() = mutex.withLock {
        if(!connected)return@withLock
        loading = data==null || tasks==null
        try {
            // Els dos endpoints són independents: si en falla un, l'altre segueix funcionant.
            var problem=""
            try { updateFamily(call("sync", JSONObject().put("ops",JSONArray()))) }
            catch(e:Exception) { problem="Compra i sopars: ${e.message ?: "sense connexió"}" }
            try { updateTasks(call("family_tasks", JSONObject()
                .put("action","tasks_snapshot").put("data",JSONObject()))) }
            catch(e:Exception) { problem = listOf(problem,"Tasques: ${e.message ?: "sense connexió"}")
                .filter{it.isNotBlank()}.joinToString(" · ") }
            error=problem
        } finally { loading=false }
    }
    suspend fun grocerySet(id:String?,name:String,bought:Boolean) =
        mutateFamily(JSONObject().put("type","grocery_set")
            .put("id",id ?: UUID.randomUUID().toString())
            .put("name",name.trim().take(80))
            .put("bought",bought))
    suspend fun groceryDelete(id:String) =
        mutateFamily(JSONObject().put("type","grocery_delete").put("id",id))
    suspend fun dinnerSet(date:String,meal:String) =
        mutateFamily(JSONObject().put("type","dinner_set").put("date",date)
            .put("value",meal.take(180)))
    private suspend fun mutateFamily(op:JSONObject) = mutex.withLock {
        if(!connected)return@withLock
        working=true
        try { updateFamily(call("sync",JSONObject().put("ops",JSONArray().put(op)))) }
        catch(e:Exception) { error=e.message ?: "No s'ha pogut desar la compra" }
        finally { working=false }
    }
    suspend fun actionTask(action:String,id:String,slot:String="única") = mutex.withLock {
        if(!connected)return@withLock
        working=true
        try {
            val payload=JSONObject().put("id",id)
            if(action!="rewards_claim")payload.put("slot",slot)
            updateTasks(call("family_tasks",JSONObject().put("action",action).put("data",payload)))
        } catch(e:Exception) { error=e.message ?: "No s'ha pogut desar la tasca" }
        finally { working=false }
    }
}

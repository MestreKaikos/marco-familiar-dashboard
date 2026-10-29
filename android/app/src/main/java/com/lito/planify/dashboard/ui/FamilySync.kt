package com.lito.planify.dashboard.ui

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Compra i sopars sincronitzats amb un Google Sheets privat per mitjà d'Apps Script.
 * Les mutacions es conserven en una cua persistent per funcionar sense Internet.
 * Les feines d'en Rai i el PIN parental NO es pugen al núvol en aquesta fase.
 */
class FamilySync internal constructor(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences("marc_family_sync", Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val mutex = Mutex()

    var connected by mutableStateOf(prefs.getBoolean("ready", false))
        private set
    var busy by mutableStateOf(false)
        private set
    var status by mutableStateOf(if (connected) "Connectat · sincronització pendent" else "Sense sincronització")
        private set
    var lastUpdate by mutableStateOf(prefs.getString("last_sync", null))
        private set
    var pendingCount by mutableIntStateOf(readQueue().size)
        private set

    // Esborrany persistent: permet enganxar l'URL i la clau en moments diferents
    // i tornar a Opcions sense perdre el que s'ha escrit. Mai no es publica al núvol.
    var draftUrl by mutableStateOf(prefs.getString("draft_url", "").orEmpty())
        private set
    var draftKey by mutableStateOf(prefs.getString("draft_key", "").orEmpty())
        private set

    fun editDraftUrl(value: String) {
        draftUrl = value.take(350)
        prefs.edit().putString("draft_url", draftUrl).apply()
    }

    fun editDraftKey(value: String) {
        draftKey = value.take(64)
        prefs.edit().putString("draft_key", draftKey).apply()
    }

    var cloudGroceries by mutableIntStateOf(prefs.getInt("cloud_grocery_count", -1))
        private set
    var cloudDinners by mutableIntStateOf(prefs.getInt("cloud_dinner_count", -1))
        private set

    var cloudTasks by mutableIntStateOf(prefs.getInt("cloud_task_count", -1))
        private set
    var cloudRewards by mutableIntStateOf(prefs.getInt("cloud_reward_count", -1))
        private set
    var cloudReminders by mutableIntStateOf(prefs.getInt("cloud_reminder_count", -1))
        private set

    private var remindersStore: FamilyReminders? = null
    fun attachReminders(store: FamilyReminders) { remindersStore = store }

    private val url: String get() = prefs.getString("url", "").orEmpty()
    private val key: String get() = prefs.getString("key", "").orEmpty()

    companion object {
        fun validUrl(input: String): Boolean {
            val uri = runCatching { Uri.parse(input.trim()) }.getOrNull() ?: return false
            return uri.scheme == "https" && uri.host == "script.google.com" &&
                uri.path?.matches(Regex("^/macros/s/[A-Za-z0-9_-]+/exec$")) == true &&
                uri.query == null && uri.fragment == null && (uri.encodedAuthority?.contains("@") != true)
        }

        fun validKey(input: String) = input.matches(Regex("^[0-9a-fA-F]{64}$"))
    }

    private fun readQueue(): List<JSONObject> = runCatching {
        val json = JSONArray(prefs.getString("queue", "[]") ?: "[]")
        (0 until json.length()).mapNotNull { json.optJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun putQueue(ops: List<JSONObject>) {
        val items = JSONArray()
        ops.forEach { items.put(it) }
        prefs.edit().putString("queue", items.toString()).apply()
        pendingCount = ops.size
    }

    /** A cada modificació local es desa la mutació abans d'enviar-la. */
    fun queue(operation: JSONObject) {
        if (!connected) return
        val op = JSONObject(operation.toString()).put("mutationId", UUID.randomUUID().toString())
        val current = readQueue()
        val type = op.optString("type")
        val identity = if (type == "dinner_set") op.optString("date") else op.optString("id")
        // L'última modificació sobre el mateix element substitueix l'anterior.
        val compacted = current.filterNot {
            val sameElement = if (type == "dinner_set") it.optString("date") == identity else it.optString("id") == identity
            sameElement && (
                (type == "dinner_set" && it.optString("type") == type) ||
                    (type.startsWith("grocery_") && it.optString("type").startsWith("grocery_"))
            )
        }
        putQueue(compacted + op)
        status = "Canvis pendents de sincronitzar"
    }

    /** Fes servir sempre un dispositiu de confiança: la clau es desa només a l'app. */
    suspend fun connect(
        newUrl: String,
        newKey: String,
        importLocal: Boolean,
        dinners: DinnerPlan,
        family: LocalFamilyStore
    ): Boolean = mutex.withLock {
        if (busy) return@withLock false
        val endpoint = newUrl.trim()
        val token = newKey.trim()
        if (!validUrl(endpoint) || !validKey(token)) {
            status = "Revisa l'adreça d'Apps Script i la clau de 64 caràcters."
            return@withLock false
        }
        busy = true
        try {
            val remote = call(endpoint, token, emptyList())
            val remoteGroceries = parseGroceries(remote)
            val remoteDinners = parseDinners(remote)
            var latest = remote
            if (importLocal) {
                val known = remoteGroceries.map { it.name.trim().lowercase() }.toSet()
                val localGroceries = family.groceries.filterNot { it.name.trim().lowercase() in known }
                val localDinners = dinners.snapshot().filterKeys { it !in remoteDinners.keys }
                val ops = localGroceries.map { grocerySet(it) } +
                    localDinners.map { (date, meal) -> dinnerSet(date, meal) }
                ops.chunked(60).forEach { chunk -> latest = call(endpoint, token, chunk) }
            }
            prefs.edit().putString("url", endpoint).putString("key", token)
                .putBoolean("ready", true).putString("queue", "[]")
                .remove("draft_url").remove("draft_key").apply()
            draftUrl = ""
            draftKey = ""
            connected = true
            pendingCount = 0
            applySnapshot(latest, dinners, family)
            updateSuccess(latest)
            true
        } catch (e: Exception) {
            status = messageForError(e)
            false
        } finally {
            busy = false
        }
    }

    /** La desconexió conserva les dades locals, però no es poden enviar nous canvis. */
    fun disconnect() {
        prefs.edit().remove("url").remove("key").remove("last_sync")
            .remove("queue").remove("draft_url").remove("draft_key")
            .remove("cloud_grocery_count").remove("cloud_dinner_count")
            .remove("cloud_task_count").remove("cloud_reward_count")
            .remove("cloud_reminder_count")
            .putBoolean("ready", false).apply()
        draftUrl = ""
        draftKey = ""
        cloudGroceries = -1
        cloudDinners = -1
        cloudTasks = -1
        cloudRewards = -1
        cloudReminders = -1
        connected = false
        pendingCount = 0
        lastUpdate = null
        status = "Desconnectat · la compra i els sopars continuen al marc"
    }

    suspend fun synchronize(dinners: DinnerPlan, family: LocalFamilyStore) = mutex.withLock {
        if (!connected || busy) return@withLock
        busy = true
        try {
            // Les mutacions s'envien una sola vegada i per ordre. Són idempotents:
            // un reintent no torna a incrementar cap comptador.
            repeat(5) {
                val pending = readQueue()
                val batch = pending.take(60)
                val result = call(url, key, batch)
                if (batch.isNotEmpty()) {
                    val ack = batch.map { it.optString("mutationId") }.toSet()
                    putQueue(readQueue().filterNot { it.optString("mutationId") in ack })
                }
                if (readQueue().isEmpty()) {
                    applySnapshot(result, dinners, family)
                    updateSuccess(result)
                    return@withLock
                }
            }
            status = "Hi ha canvis pendents: es tornaran a enviar"
        } catch (e: Exception) {
            status = messageForError(e)
        } finally {
            busy = false
        }
    }

    private fun messageForError(ex: Exception): String {
        val detail = ex.message.orEmpty()
        return when {
            detail.contains("clau", ignoreCase = true) -> "Clau familiar incorrecta. Comprova-la a Opcions."
            detail.contains("Límit", ignoreCase = true) -> "El full ha arribat al límit de registres."
            detail.contains("No es pot connectar al servei", ignoreCase = true) ->
                "No s'ha pogut contactar amb Apps Script. Revisa l'enllaç /exec i el desplegament."
            else -> "No s'ha pogut sincronitzar. Revisa la connexió i la versió publicada d'Apps Script."
        }
    }

    private fun updateSuccess(snapshot: JSONObject) {
        val now = java.time.LocalDateTime.now().toString().take(16).replace("T", " ")
        cloudGroceries = snapshot.optJSONObject("data")?.optJSONArray("groceries")?.length() ?: -1
        cloudDinners = snapshot.optJSONObject("data")?.optJSONObject("dinners")?.length() ?: -1
        cloudTasks = snapshot.optJSONObject("data")?.optJSONArray("tasks")?.length() ?: -1
        cloudRewards = snapshot.optJSONObject("data")?.optJSONArray("rewards")?.length() ?: -1
        cloudReminders = snapshot.optJSONObject("data")?.optJSONArray("reminders")?.length() ?: -1
        lastUpdate = now
        prefs.edit().putString("last_sync", now)
            .putInt("cloud_grocery_count", cloudGroceries)
            .putInt("cloud_dinner_count", cloudDinners)
            .putInt("cloud_task_count", cloudTasks)
            .putInt("cloud_reward_count", cloudRewards)
            .putInt("cloud_reminder_count", cloudReminders).apply()
        status = "Sincronitzat correctament · " + now
    }

    private fun applySnapshot(root: JSONObject, dinners: DinnerPlan, family: LocalFamilyStore) {
        family.replaceGroceries(parseGroceries(root))
        dinners.replaceFromCloud(parseDinners(root))
        // No esborra tasques locals si l'Apps Script encara és una versió antiga.
        root.optJSONObject("data")?.let { data ->
            data.optJSONArray("reminders")?.let { items -> remindersStore?.replaceFromCloud(items) }
            if (data.has("tasks") && data.has("taskDone") &&
                data.has("rewards") && data.has("stars")) {
                family.replaceFamilyFromCloud(data)
            }
        }
    }

    private fun parseGroceries(root: JSONObject): List<LocalGrocery> {
        val arr = root.getJSONObject("data").getJSONArray("groceries")
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("id")
            val name = obj.optString("name")
            if (id.isBlank() || name.isBlank()) null
            else LocalGrocery(id, name.take(80), obj.optBoolean("bought"))
        }.take(200)
    }

    private fun parseDinners(root: JSONObject): Map<LocalDate, String> {
        val obj = root.getJSONObject("data").getJSONObject("dinners")
        val dates = obj.keys()
        val result = mutableMapOf<LocalDate, String>()
        while (dates.hasNext()) {
            val key = dates.next()
            val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: continue
            val value = obj.optString(key).take(180)
            if (value.isNotBlank()) result[date] = value
        }
        return result
    }

    /** Fotos privades: reutilitza el servei i la clau ja configurats, sense URL pública de Drive. */
    suspend fun photoAction(action: String, payload: JSONObject = JSONObject()): JSONObject {
        if (!connected) throw IllegalStateException("Connecta primer la sincronització familiar.")
        require(action in setOf("photo_list", "photo_get")) { "Acció de fotografia no admesa al marc" }
        return post(url, key, action, payload)
    }

    private suspend fun call(endpoint: String, token: String, operations: List<JSONObject>): JSONObject {
        val ops = JSONArray()
        operations.forEach { ops.put(it) }
        return post(endpoint, token, "sync", JSONObject().put("ops", ops))
    }

    private suspend fun post(endpoint: String, token: String, action: String, payload: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val requestBody = JSONObject()
                .put("key", token)
                .put("action", action)
                .put("payload", payload)
                .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url(endpoint).post(requestBody).build()
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("No es pot connectar al servei")
                val reply = JSONObject(response.body?.string() ?: "")
                if (!reply.optBoolean("ok")) throw IllegalStateException(reply.optString("error", "Error del servidor"))
                reply
            }
        }

    private fun grocerySet(item: LocalGrocery) = JSONObject()
        .put("type", "grocery_set").put("id", item.id)
        .put("name", item.name).put("bought", item.bought)

    private fun dinnerSet(day: LocalDate, meal: String) = JSONObject()
        .put("type", "dinner_set").put("date", day.toString()).put("value", meal)
}

@Composable
fun rememberFamilySync(): FamilySync {
    val context = LocalContext.current
    return remember(context) { FamilySync(context) }
}

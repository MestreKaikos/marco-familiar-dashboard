package com.lito.planify.dashboard.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.util.UUID

data class LocalGrocery(val id: String, val name: String, val bought: Boolean = false)
data class LocalChore(
    val id: String,
    val title: String,
    val owner: String,
    val stars: Int = 1,
    val daily: Boolean = false,
    val date: String = "",
    val status: String = "todo" // todo, pending, approved
)
data class LocalReward(val id: String, val title: String, val cost: Int)

/**
 * Primera versió sense servidor: dades privades del dispositiu, no sincronitzades.
 * Les estrelles només s'acrediten després d'una aprovació amb PIN parental.
 */
class LocalFamilyStore internal constructor(private val prefs: SharedPreferences) {
    val groceries = mutableStateListOf<LocalGrocery>()
    val chores = mutableStateListOf<LocalChore>()
    val rewards = mutableStateListOf<LocalReward>()
    /** Només notificacions de la compra; les feines/premis continuen locals. */
    var onGroceryMutation: ((JSONObject) -> Unit)? = null
    var onFamilyMutation: ((JSONObject) -> Unit)? = null

    /** Rep les compres del núvol sense tocar tasques, recompenses ni PIN dels adults. */
    fun replaceGroceries(items: List<LocalGrocery>) {
        groceries.clear()
        groceries.addAll(items)
        persist()
    }

    /** El full de Google és la font de veritat quan el servei retorna tasques.
     * Abans del primer canvi de font, es guarda una còpia de les feines locals.
     * Les feines pendents d'aprovació en aquest dispositiu no es descarten.
     */
    fun replaceFamilyFromCloud(data: JSONObject) {
        val remoteTasks = data.optJSONArray("tasks") ?: return
        val remoteDone = data.optJSONArray("taskDone") ?: return
        val remoteRewards = data.optJSONArray("rewards") ?: return
        if (!data.has("stars")) return
        if (!prefs.contains("family_before_cloud")) {
            prefs.edit().putString("family_before_cloud", prefs.getString("data", "{}")).apply()
        }
        val pendingLocal = chores.filter { it.status == "pending" }.associateBy { it.id }
        val doneByTask = mutableMapOf<String, Pair<String, String>>()
        for (j in 0 until remoteDone.length()) {
            val d = remoteDone.optJSONObject(j) ?: continue
            val id = d.optString("taskId")
            val date = d.optString("date")
            if (id.isBlank() || date.isBlank()) continue
            if (doneByTask[id]?.first.orEmpty() < date) {
                doneByTask[id] = date to d.optString("owner", "Rai")
            }
        }
        val newChores = mutableListOf<LocalChore>()
        for (i in 0 until remoteTasks.length()) {
            val item = remoteTasks.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val title = item.optString("title")
            if (id.isBlank() || title.isBlank()) continue
            val owner = if (item.optString("owner") == "Família") "Família" else "Rai"
            val completed = doneByTask[id]?.takeIf { it.second == owner }?.first
            val localPending = pendingLocal[id]?.takeIf { completed == null }
            newChores.add(LocalChore(
                id = id, title = title.take(100), owner = owner,
                stars = item.optInt("stars", 1).coerceIn(1, 5),
                daily = item.optBoolean("daily"),
                date = completed ?: localPending?.date.orEmpty(),
                status = when {
                    completed != null -> "approved"
                    localPending != null -> "pending"
                    else -> "todo"
                }
            ))
        }
        val newRewards = mutableListOf<LocalReward>()
        for (i in 0 until remoteRewards.length()) {
            val reward = remoteRewards.optJSONObject(i) ?: continue
            if (reward.optString("id").isNotBlank() && reward.optString("title").isNotBlank()) {
                newRewards.add(LocalReward(reward.getString("id"),reward.getString("title").take(100),
                    reward.optInt("cost", 1).coerceIn(1, 999)))
            }
        }
        chores.clear()
        chores.addAll(newChores)
        rewards.clear()
        rewards.addAll(newRewards)
        stars = data.optInt("stars",0).coerceAtLeast(0)
        val remotePending = data.optString("pendingReward").takeIf { it.isNotBlank() }
        pendingRewardId = remotePending ?: pendingRewardId?.takeIf { id -> rewards.any { it.id == id } }
        persist()
    }

    private fun emitSet(item: LocalGrocery) {
        onGroceryMutation?.invoke(JSONObject().put("type", "grocery_set").put("id", item.id)
            .put("name", item.name).put("bought", item.bought))
    }

    private fun emitDelete(id: String) {
        onGroceryMutation?.invoke(JSONObject().put("type", "grocery_delete").put("id", id))
    }

    var stars by mutableIntStateOf(0)
        private set
    var pendingRewardId by mutableStateOf<String?>(null)
        private set

    init { load() }

    private fun newId() = UUID.randomUUID().toString()

    private fun load() {
        runCatching {
            val root = JSONObject(prefs.getString("data", "{}") ?: "{}")
            root.optJSONArray("groceries")?.let { arr ->
                repeat(arr.length()) { i ->
                    val item = arr.optJSONObject(i) ?: return@repeat
                    val name = item.optString("name").trim()
                    if (name.isNotBlank()) groceries.add(LocalGrocery(item.optString("id", newId()), name, item.optBoolean("bought")))
                }
            }
            root.optJSONArray("chores")?.let { arr ->
                repeat(arr.length()) { i ->
                    val item = arr.optJSONObject(i) ?: return@repeat
                    val title = item.optString("title").trim()
                    if (title.isNotBlank()) chores.add(
                        LocalChore(
                            id = item.optString("id", newId()), title = title,
                            owner = if (item.optString("owner") == "Rai") "Rai" else "Família",
                            stars = item.optInt("stars", 1).coerceIn(1, 5),
                            daily = item.optBoolean("daily"), date = item.optString("date"),
                            status = item.optString("status").takeIf { it in listOf("todo", "pending", "approved") } ?: "todo"
                        )
                    )
                }
            }
            root.optJSONArray("rewards")?.let { arr ->
                repeat(arr.length()) { i ->
                    val item = arr.optJSONObject(i) ?: return@repeat
                    val title = item.optString("title").trim()
                    if (title.isNotBlank()) rewards.add(LocalReward(item.optString("id", newId()), title, item.optInt("cost", 5).coerceIn(1, 999)))
                }
            }
            stars = root.optInt("stars").coerceAtLeast(0)
            pendingRewardId = root.optString("pendingReward").takeIf { id -> rewards.any { it.id == id } }
        }
    }

    private fun persist() {
        val root = JSONObject()
        root.put("groceries", JSONArray().apply {
            groceries.forEach { item ->
                put(JSONObject().put("id", item.id).put("name", item.name).put("bought", item.bought))
            }
        })
        root.put("chores", JSONArray().apply {
            chores.forEach { item ->
                put(JSONObject().put("id", item.id).put("title", item.title).put("owner", item.owner)
                    .put("stars", item.stars).put("daily", item.daily).put("date", item.date).put("status", item.status))
            }
        })
        root.put("rewards", JSONArray().apply {
            rewards.forEach { reward ->
                put(JSONObject().put("id", reward.id).put("title", reward.title).put("cost", reward.cost))
            }
        })
        root.put("stars", stars)
        root.put("pendingReward", pendingRewardId ?: "")
        prefs.edit().putString("data", root.toString()).apply()
    }

    fun addGrocery(raw: String) {
        val name = raw.trim().take(80)
        if (name.isBlank() || groceries.any { it.name.equals(name, ignoreCase = true) && !it.bought }) return
        val item = LocalGrocery(newId(), name)
        groceries.add(item)
        persist()
        emitSet(item)
    }

    fun toggleGrocery(id: String) {
        val i = groceries.indexOfFirst { it.id == id }
        if (i >= 0) {
            val item = groceries[i].copy(bought = !groceries[i].bought)
            groceries[i] = item
            persist()
            emitSet(item)
        }
    }

    fun renameGrocery(id: String, raw: String) {
        val name = raw.trim().take(80)
        val i = groceries.indexOfFirst { it.id == id }
        if (i >= 0 && name.isNotBlank()) {
            val item = groceries[i].copy(name = name)
            groceries[i] = item
            persist()
            emitSet(item)
        }
    }

    fun removeGrocery(id: String) {
        if (groceries.removeAll { it.id == id }) {
            persist()
            emitDelete(id)
        }
    }

    fun clearBought() {
        val ids = groceries.filter { it.bought }.map { it.id }
        groceries.removeAll { it.bought }
        persist()
        ids.forEach(::emitDelete)
    }

    fun addChore(raw: String, owner: String, daily: Boolean, points: Int) {
        val title = raw.trim().take(100)
        if (title.isBlank()) return
        val chore = LocalChore(newId(), title, if (owner == "Rai") "Rai" else "Família", points.coerceIn(1, 5), daily)
        chores.add(chore)
        persist()
        onFamilyMutation?.invoke(JSONObject().put("type", "task_set").put("id", chore.id)
            .put("title", chore.title).put("stars", chore.stars)
            .put("owner", chore.owner).put("daily", chore.daily))
    }

    fun removeChore(id: String) {
        // Els punts ja validats es mantenen com un registre de l'esforç realitzat.
        if (chores.removeAll { it.id == id }) {
            persist()
            onFamilyMutation?.invoke(JSONObject().put("type", "task_delete").put("id", id))
        }
    }

    fun status(chore: LocalChore, today: LocalDate): String =
        if (chore.daily && chore.status != "pending" && chore.date != today.toString()) "todo" else chore.status

    fun requestDone(id: String, today: LocalDate) {
        val i = chores.indexOfFirst { it.id == id }
        if (i < 0) return
        val task = chores[i]
        if (status(task, today) != "todo") return
        chores[i] = task.copy(
            date = today.toString(),
            status = if (task.owner == "Rai") "pending" else "approved"
        )
        persist()
        if (task.owner != "Rai") {
            onFamilyMutation?.invoke(JSONObject().put("type","task_complete").put("id",id)
                .put("date",today.toString()))
        }
    }

    /** L'aprovació només acredita una vegada la mateixa tasca i data. */
    fun approveChore(id: String, today: LocalDate) {
        val i = chores.indexOfFirst { it.id == id }
        if (i < 0) return
        val task = chores[i]
        if (task.owner != "Rai" || status(task, today) != "pending") return
        chores[i] = task.copy(status = "approved")
        stars += task.stars
        persist()
        onFamilyMutation?.invoke(JSONObject().put("type","task_complete").put("id",id)
            .put("date",today.toString()))
    }

    fun rejectChore(id: String, today: LocalDate) {
        val i = chores.indexOfFirst { it.id == id }
        if (i >= 0 && status(chores[i], today) == "pending") {
            chores[i] = chores[i].copy(status = "todo", date = "")
            persist()
        }
    }

    fun addReward(raw: String, points: Int) {
        val title = raw.trim().take(100)
        if (title.isBlank()) return
        val reward = LocalReward(newId(), title, points.coerceIn(1, 999))
        rewards.add(reward)
        persist()
        onFamilyMutation?.invoke(JSONObject().put("type","reward_set").put("id",reward.id)
            .put("title",reward.title).put("cost",reward.cost))
    }

    fun removeReward(id: String) {
        if (rewards.removeAll { it.id == id }) {
            if (pendingRewardId == id) pendingRewardId = null
            persist()
            onFamilyMutation?.invoke(JSONObject().put("type","reward_delete").put("id",id))
        }
    }

    fun requestReward(id: String) {
        val reward = rewards.firstOrNull { it.id == id } ?: return
        if (pendingRewardId != null || stars < reward.cost) return
        pendingRewardId = id
        persist()
    }

    fun decideReward(approved: Boolean) {
        val id = pendingRewardId ?: return
        val reward = rewards.firstOrNull { it.id == id }
        if (approved && reward != null && stars >= reward.cost) {
            stars -= reward.cost
            onFamilyMutation?.invoke(JSONObject().put("type","reward_redeem").put("id",reward.id))
        }
        pendingRewardId = null
        persist()
    }

    val pinConfigured: Boolean get() = prefs.contains("adult_pin")
    fun checkOrSetAdultPin(raw: String): Boolean {
        if (raw.length != 4 || raw.any { !it.isDigit() }) return false
        val existing = prefs.getString("adult_pin", null)
        if (existing == null) {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val value = salt.toHex() + ":" + digest(salt, raw).toHex()
            prefs.edit().putString("adult_pin", value).apply()
            return true
        }
        val parts = existing.split(":")
        if (parts.size != 2) return false
        val salt = runCatching { parts[0].hexBytes() }.getOrNull() ?: return false
        val expected = runCatching { parts[1].hexBytes() }.getOrNull() ?: return false
        return MessageDigest.isEqual(digest(salt, raw), expected)
    }

    private fun digest(salt: ByteArray, pin: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray(StandardCharsets.UTF_8))
    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

@Composable
fun rememberLocalFamilyStore(): LocalFamilyStore {
    val context = LocalContext.current
    return remember(context) {
        LocalFamilyStore(context.getSharedPreferences("marc_familiar_local", Context.MODE_PRIVATE))
    }
}

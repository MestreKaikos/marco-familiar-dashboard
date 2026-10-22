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
        groceries.add(LocalGrocery(newId(), name))
        persist()
    }

    fun toggleGrocery(id: String) {
        val i = groceries.indexOfFirst { it.id == id }
        if (i >= 0) { groceries[i] = groceries[i].copy(bought = !groceries[i].bought); persist() }
    }

    fun renameGrocery(id: String, raw: String) {
        val name = raw.trim().take(80)
        val i = groceries.indexOfFirst { it.id == id }
        if (i >= 0 && name.isNotBlank()) { groceries[i] = groceries[i].copy(name = name); persist() }
    }

    fun removeGrocery(id: String) {
        groceries.removeAll { it.id == id }
        persist()
    }

    fun clearBought() {
        groceries.removeAll { it.bought }
        persist()
    }

    fun addChore(raw: String, owner: String, daily: Boolean, points: Int) {
        val title = raw.trim().take(100)
        if (title.isBlank()) return
        chores.add(LocalChore(newId(), title, if (owner == "Rai") "Rai" else "Família", points.coerceIn(1, 5), daily))
        persist()
    }

    fun removeChore(id: String) {
        // Els punts ja validats es mantenen com un registre de l'esforç realitzat.
        chores.removeAll { it.id == id }
        persist()
    }

    fun status(chore: LocalChore, today: LocalDate): String =
        if (chore.daily && chore.date != today.toString()) "todo" else chore.status

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
    }

    /** L'aprovació només acredita una vegada la mateixa tasca i data. */
    fun approveChore(id: String, today: LocalDate) {
        val i = chores.indexOfFirst { it.id == id }
        if (i < 0) return
        val task = chores[i]
        if (task.owner != "Rai" || status(task, today) != "pending" || task.date != today.toString()) return
        chores[i] = task.copy(status = "approved")
        stars += task.stars
        persist()
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
        rewards.add(LocalReward(newId(), title, points.coerceIn(1, 999)))
        persist()
    }

    fun removeReward(id: String) {
        rewards.removeAll { it.id == id }
        if (pendingRewardId == id) pendingRewardId = null
        persist()
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
        if (approved && reward != null && stars >= reward.cost) stars -= reward.cost
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

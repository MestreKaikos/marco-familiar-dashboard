package com.lito.planify.dashboard.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

data class FamilyReminder(
    val id: String, val title: String, val date: String = "",
    val done: Boolean = false, val notes: String = ""
) {
    val deadline: LocalDate? get() = runCatching { LocalDate.parse(date) }.getOrNull()
}

/** Estat privat local; quan hi ha connexió, Google Sheets és la font de veritat. */
class FamilyReminders internal constructor(private val prefs: SharedPreferences) {
    val items = mutableStateListOf<FamilyReminder>()
    var onMutation: ((JSONObject) -> Unit)? = null
    init {
        runCatching {
            val saved = JSONArray(prefs.getString("items", "[]") ?: "[]")
            for (i in 0 until saved.length()) {
                val item = saved.optJSONObject(i) ?: continue
                parse(item)?.let { items.add(it) }
            }
        }
        sort()
    }
    private fun parse(obj: JSONObject): FamilyReminder? {
        val id = obj.optString("id").takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,80}")) } ?: return null
        val title = obj.optString("title").trim().take(120).ifBlank { return null }
        val date = obj.optString("date").take(10)
            .takeIf { it.isBlank() || runCatching { LocalDate.parse(it) }.isSuccess } ?: ""
        return FamilyReminder(id, title, date, obj.optBoolean("done"), obj.optString("notes").take(240))
    }
    private fun sort() {
        val sorted = items.sortedWith(compareBy<FamilyReminder> { it.done }
            .thenBy { it.date.ifBlank { "9999-99-99" } }.thenBy { it.title })
        items.clear()
        items.addAll(sorted)
    }
    private fun persist() {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("title", it.title)
                .put("date", it.date).put("done", it.done).put("notes", it.notes))
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }
    fun replaceFromCloud(values: JSONArray) {
        val cloud = (0 until values.length()).mapNotNull { i -> values.optJSONObject(i)?.let(::parse) }.take(250)
        items.clear()
        items.addAll(cloud)
        sort()
        persist()
    }
    fun save(id: String?, title: String, date: String, notes: String): Boolean {
        val cleanTitle = title.trim().take(120)
        val cleanDate = date.trim()
        if (cleanTitle.isBlank() || (cleanDate.isNotBlank() &&
                runCatching { LocalDate.parse(cleanDate) }.isFailure)) return false
        val realId = id?.takeIf { value -> items.any { it.id == value } } ?: UUID.randomUUID().toString()
        val original = items.firstOrNull { it.id == realId }
        val updated = FamilyReminder(realId, cleanTitle, cleanDate, original?.done ?: false, notes.trim().take(240))
        items.removeAll { it.id == realId }
        items.add(updated)
        sort()
        persist()
        onMutation?.invoke(JSONObject().put("type", "reminder_set")
            .put("id", realId).put("title", updated.title).put("date", updated.date)
            .put("notes", updated.notes).put("done", updated.done))
        return true
    }
    fun toggle(id: String, done: Boolean) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        items[index] = items[index].copy(done = done)
        sort()
        persist()
        onMutation?.invoke(JSONObject().put("type", "reminder_done").put("id", id).put("done", done))
    }
    fun delete(id: String) {
        if (!items.removeAll { it.id == id }) return
        persist()
        onMutation?.invoke(JSONObject().put("type", "reminder_delete").put("id", id))
    }
    fun dueOn(date: LocalDate): List<FamilyReminder> = items.filter { !it.done && it.date == date.toString() }
    fun pending(): List<FamilyReminder> = items.filterNot { it.done }
}
@Composable
fun rememberFamilyReminders(): FamilyReminders {
    val context = LocalContext.current
    return remember(context) {
        FamilyReminders(context.getSharedPreferences("marc_familiar_recordatoris", Context.MODE_PRIVATE))
    }
}

@Composable
fun FamilyRemindersScreen(store: FamilyReminders, today: LocalDate) {
    var editing by remember { mutableStateOf<FamilyReminder?>(null) }
    var creating by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(30.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("📌 Recordatoris", style = Type.Large.copy(fontWeight = FontWeight.Bold))
                Text(store.pending().size.toString() + " pendents · Els vençuts apareixen destacats",
                    style = Type.Small.copy(color = Palette.InkSoft))
            }
            Button(onClick = { creating = true }) { Text("+ Recordatori") }
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(store.items, key = { it.id }) { reminder ->
                val expired = !reminder.done && reminder.deadline?.isBefore(today) == true
                val dueToday = !reminder.done && reminder.deadline == today
                Row(Modifier.fillMaxWidth().background(Palette.Card, RoundedCornerShape(18.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Checkbox(checked = reminder.done, onCheckedChange = { store.toggle(reminder.id, it) })
                    Column(Modifier.weight(1f)) {
                        Text(reminder.title, style = Type.Body.copy(
                            color = if(reminder.done) Palette.InkMuted else Palette.Ink,
                            fontWeight = FontWeight.SemiBold))
                        val dueLabel = when {
                            reminder.done -> "✓ Fet"
                            expired -> "Vençut"
                            dueToday -> "Avui"
                            reminder.date.isBlank() -> "Sense data límit"
                            else -> "Data límit"
                        }
                        val formatted = reminder.deadline?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        Text(if (formatted == null) dueLabel else dueLabel + " · " + formatted,
                            style = Type.Small.copy(color = if(expired) Palette.Accent else Palette.InkSoft))
                        if (reminder.notes.isNotBlank())
                            Text(reminder.notes, style = Type.Tiny.copy(color = Palette.InkMuted), maxLines = 2)
                    }
                    OutlinedButton(onClick = { editing = reminder }) { Text("Editar") }
                    TextButton(onClick = { store.delete(reminder.id) }) { Text("✕") }
                }
            }
        }
    }
    if (creating || editing != null) {
        ReminderDialog(editing, onDismiss = { creating = false; editing = null }) { title, date, notes ->
            val ok = store.save(editing?.id, title, date, notes)
            if (ok) { creating = false; editing = null }
            ok
        }
    }
}
@Composable
private fun ReminderDialog(
    original: FamilyReminder?, onDismiss: () -> Unit,
    onSave: (String, String, String) -> Boolean
) {
    var title by remember(original) { mutableStateOf(original?.title ?: "") }
    var date by remember(original) { mutableStateOf(original?.date ?: "") }
    var notes by remember(original) { mutableStateOf(original?.notes ?: "") }
    var error by remember(original) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if(original == null) "Nou recordatori" else "Editar recordatori") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(120) },
                    label = { Text("Què cal fer?") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = date, onValueChange = { date = it.take(10) },
                    label = { Text("Data límit (AAAA-MM-DD) · opcional") },
                    isError = error, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it.take(240) },
                    label = { Text("Nota (opcional)") }, modifier = Modifier.fillMaxWidth())
                if (error) Text("Revisa la data límit: AAAA-MM-DD", color = Palette.Accent)
            }
        },
        confirmButton = {
            TextButton(onClick = { error = !onSave(title, date, notes) },
                enabled = title.isNotBlank()) { Text("Desar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel·lar") } }
    )
}

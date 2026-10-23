package com.lito.planify.dashboard.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import java.time.LocalDate

/**
 * Planificació local del sopar per data, sense comptes ni connexió a Internet.
 * Les entrades són exclusivament d'aquest dispositiu i es poden editar o esborrar.
 */
class DinnerPlan internal constructor(private val preferences: SharedPreferences) {
    private val meals = mutableStateMapOf<LocalDate, String>()
    /** Desactivat en actualitzacions rebudes del núvol, per evitar bucles. */
    var onLocalChange: ((LocalDate, String) -> Unit)? = null

    fun snapshot(): Map<LocalDate, String> = meals.toMap()

    fun replaceFromCloud(values: Map<LocalDate, String>) {
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith(PREFIX) }.forEach(editor::remove)
        values.forEach { (date, meal) ->
            if (meal.isNotBlank()) editor.putString(PREFIX + date, meal.take(180))
        }
        editor.apply()
        meals.clear()
        values.forEach { (date, meal) -> if (meal.isNotBlank()) meals[date] = meal.take(180) }
    }

    init {
        preferences.all.forEach { (key, value) ->
            if (key.startsWith(PREFIX) && value is String) {
                val date = runCatching { LocalDate.parse(key.removePrefix(PREFIX)) }.getOrNull()
                if (date != null && value.isNotBlank()) meals[date] = value
            }
        }
    }

    operator fun get(date: LocalDate): String? = meals[date]

    fun save(date: LocalDate, description: String) {
        val cleaned = description.trim().take(180)
        if (cleaned.isBlank()) {
            meals.remove(date)
            preferences.edit().remove(PREFIX + date).apply()
        } else {
            meals[date] = cleaned
            preferences.edit().putString(PREFIX + date, cleaned).apply()
        }
        onLocalChange?.invoke(date, cleaned)
    }

    private companion object {
        const val PREFIX = "sopar:"
    }
}

@Composable
fun rememberDinnerPlan(): DinnerPlan {
    val context = LocalContext.current
    return remember(context) {
        DinnerPlan(context.getSharedPreferences("marc_familiar_sopars", Context.MODE_PRIVATE))
    }
}

@Composable
fun DinnerEditor(date: LocalDate, initial: String?, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var description by remember(date, initial) { mutableStateOf(initial.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Què sopem?", style = Type.Large) },
        text = {
            Column {
                Text(
                    "${date.weekdayName().replaceFirstChar { it.uppercaseChar() }} ${date.dayOfMonth} ${date.monthWithArticle()}",
                    style = Type.Small.copy(color = Palette.InkSoft),
                    modifier = Modifier.padding(bottom = 14.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(180) },
                    label = { Text("Sopar previst") },
                    placeholder = { Text("Per exemple: truita i amanida") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3
                )
                Text(
                    "Es desa al marc i, si actives la sincronització, també a la família.",
                    style = Type.Tiny.copy(color = Palette.InkMuted),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(description) }) {
                Text("Desar", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel·lar") }
        }
    )
}

package com.lito.planify.dashboard.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.data.PersonalCalendar
import com.lito.planify.dashboard.data.Store
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

/**
 * Formulari com a PANTALLA, no diàleg.
 * Els esborranys romanen al magatzem privat de l'app quan es canvia a un navegador
 * o a Google Sheets per copiar una altra URL/clau.
 */
@Composable
internal fun RaiDirectSetup(onDismiss: () -> Unit, onSaved: (String, String) -> Unit) {
    val context = LocalContext.current
    val familyPrefs = remember(context) {
        context.getSharedPreferences("marc_rai_sync", Context.MODE_PRIVATE)
    }
    val photoPrefs = remember(context) {
        context.getSharedPreferences("marc_fotos", Context.MODE_PRIVATE)
    }
    val draftPrefs = remember(context) {
        context.getSharedPreferences("marc_setup_draft", Context.MODE_PRIVATE)
    }
    var url by remember {
        mutableStateOf(draftPrefs.getString("url", null)
            ?: familyPrefs.getString("url", "").orEmpty()
                .ifBlank { photoPrefs.getString("shared_url", "").orEmpty() })
    }
    var familyKey by remember {
        mutableStateOf(draftPrefs.getString("familyKey", null)
            ?: familyPrefs.getString("key", "").orEmpty())
    }
    var photoKey by remember {
        mutableStateOf(draftPrefs.getString("photoKey", null)
            ?: photoPrefs.getString("shared_key", "").orEmpty())
    }
    var calendars by remember {
        mutableStateOf(draftPrefs.getString("calendars", null)
            ?: Store(context).calendarFeeds.joinToString("\n") { "${it.name} | ${it.url}" })
    }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("⚙️ Configurar Marc Familiar", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onDismiss) { Text("Tornar") }
        }
        Text(
            "Pots sortir al navegador per copiar cada dada i tornar: el que escriguis " +
                "quedarà guardat automàticament en aquest mòbil.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            value = url, onValueChange = {
                url = it; error = ""
                draftPrefs.edit().putString("url", it).apply()
            },
            label = { Text("URL d'Apps Script (/exec)") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedTextField(
            value = familyKey, onValueChange = {
                familyKey = it; error = ""
                draftPrefs.edit().putString("familyKey", it).apply()
            },
            label = { Text("Clau familiar per a les tasques") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        HorizontalDivider()
        Text("Fotografies (opcional)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = photoKey, onValueChange = {
                photoKey = it; error = ""
                draftPrefs.edit().putString("photoKey", it).apply()
            },
            label = { Text("Clau privada de lectura de fotos") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        HorizontalDivider()
        Text("Calendaris de Google (opcional)", style = MaterialTheme.typography.titleMedium)
        Text("Un per línia: Nom | URL privada iCal", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = calendars, onValueChange = {
                calendars = it; error = ""
                draftPrefs.edit().putString("calendars", it).apply()
            },
            label = { Text("Calendaris") },
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5
        )
        if (error.isNotEmpty()) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val cleanUrl = url.trim()
                val cleanFamilyKey = familyKey.trim()
                val cleanPhotoKey = photoKey.trim()
                val outcome = runCatching {
                    val u = cleanUrl.toHttpUrlOrNull()
                    require(u != null && u.isHttps && u.host == "script.google.com" &&
                        u.encodedPath.endsWith("/exec")) {
                        "La URL ha de ser l'adreça /exec d'Apps Script."
                    }
                    require(cleanFamilyKey.matches(Regex("[a-fA-F0-9]{64}"))) {
                        "Revisa la clau familiar: ha de tenir 64 caràcters hexadecimals."
                    }
                    require(cleanPhotoKey.isBlank() ||
                        cleanPhotoKey.matches(Regex("[a-fA-F0-9]{64}"))) {
                        "La clau de fotografies no és vàlida."
                    }
                    val lines = calendars.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    require(lines.size <= 5) { "Pots afegir com a màxim cinc calendaris." }
                    val entries = lines.map { line ->
                        val parts = line.split("|", limit = 2)
                        require(parts.size == 2 && parts[0].trim().isNotEmpty()) {
                            "Escriu cada calendari com: Nom | URL iCal"
                        }
                        val calUrl = parts[1].trim()
                        val cal = calUrl.toHttpUrlOrNull()
                        require(cal != null && cal.isHttps &&
                            (cal.host == "calendar.google.com" || cal.host == "www.google.com") &&
                            cal.encodedPath.contains("/ical/")) {
                            "Un calendari no té un enllaç secret iCal vàlid."
                        }
                        PersonalCalendar(UUID.randomUUID().toString(), parts[0].trim().take(60), calUrl)
                    }
                    familyPrefs.edit().putString("url", cleanUrl)
                        .putString("key", cleanFamilyKey).apply()
                    if (cleanPhotoKey.isNotBlank()) {
                        photoPrefs.edit().putString("shared_url", cleanUrl)
                            .putString("shared_key", cleanPhotoKey).apply()
                    }
                    if (entries.isNotEmpty()) Store(context).calendarFeeds = entries
                }
                if (outcome.isSuccess) {
                    // Esborrany temporal eliminat després de desar correctament.
                    draftPrefs.edit().remove("url").remove("familyKey")
                        .remove("photoKey").remove("calendars").apply()
                    onSaved(cleanUrl, cleanFamilyKey)
                } else {
                    error = outcome.exceptionOrNull()?.message ?: "No s'ha pogut desar."
                }
            }
        ) { Text("Desar i connectar") }
        Text(
            "No cal introduir la contrasenya de Google. Les claus només es desen " +
                "a l'espai privat de l'aplicació en aquest dispositiu.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(20.dp))
    }
}

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
 * Configuració local per provar el marc en un únic telèfon.
 * Evita enviar les credencials pel navegador i el servidor HTTP de vinculació.
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
    var url by remember {
        mutableStateOf(familyPrefs.getString("url", "").orEmpty()
            .ifBlank { photoPrefs.getString("shared_url", "").orEmpty() })
    }
    var familyKey by remember { mutableStateOf(familyPrefs.getString("key", "").orEmpty()) }
    var photoKey by remember { mutableStateOf(photoPrefs.getString("shared_key", "").orEmpty()) }
    var calendars by remember {
        mutableStateOf(Store(context).calendarFeeds.joinToString("\n") { "${it.name} | ${it.url}" })
    }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar Marc Familiar") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("Les dades es desen directament al mòbil, sense passar pel QR.")
                OutlinedTextField(
                    value = url, onValueChange = { url = it; error = "" },
                    label = { Text("URL d'Apps Script (/exec)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = familyKey, onValueChange = { familyKey = it; error = "" },
                    label = { Text("Clau familiar (compra, sopars i tasques)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Text("Fotografies (opcional)", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = photoKey, onValueChange = { photoKey = it; error = "" },
                    label = { Text("Clau privada de lectura de fotos") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Text("Calendaris (opcional)", style = MaterialTheme.typography.titleSmall)
                Text("Un calendari per línia: Nom | URL privada iCal", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = calendars, onValueChange = { calendars = it; error = "" },
                    label = { Text("Calendaris de Google") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5
                )
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "No introdueixis la contrasenya de Google. Només les claus que ja utilitza Marc Familiar.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel·lar") } },
        confirmButton = {
            Button(onClick = {
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
                            "Un dels calendaris no té un enllaç secret iCal vàlid."
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
                    onSaved(cleanUrl, cleanFamilyKey)
                } else {
                    error = outcome.exceptionOrNull()?.message ?: "No s'ha pogut desar la configuració."
                }
            }) { Text("Desar i connectar") }
        }
    )
}

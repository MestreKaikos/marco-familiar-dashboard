package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type

/** Les adreces privades de Google només s'introdueixen aquí i mai es mostren en llistes. */
@Composable
fun CalendarSetupScreen(state: UiState, callbacks: SettingsCallbacks, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var showUrl by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }

    Row(
        Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 30.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            Modifier.weight(0.95f).fillMaxHeight().background(Palette.Card, RoundedCornerShape(28.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("📅 Calendaris familiars", style = Type.Title)
            Text("Connecta fins a cinc calendaris de Google sense usuari ni contrasenya de Planify.", style = Type.Small)
            Text("Com obtenir l'enllaç", style = Type.Large.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "1. Obre Google Calendar des de l'ordinador.\n" +
                    "2. Entra a Configuració i tria un calendari.\n" +
                    "3. A Integrar calendari, copia l'adreça secreta en format iCal.\n" +
                    "4. Enganxa-la aquí i prem Afegir calendari.",
                style = Type.Small
            )
            Text(
                "Important: qui tingui l'adreça secreta podrà llegir el calendari. " +
                    "No la comparteixis al xat, al GitHub ni en captures.",
                style = Type.Small.copy(color = Palette.Accent)
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("← Tornar a Opcions")
            }
        }
        Column(
            Modifier.weight(1.1f).fillMaxHeight().background(Palette.Card, RoundedCornerShape(28.dp)).padding(28.dp)
        ) {
            Text("Els teus calendaris", style = Type.Large.copy(fontWeight = FontWeight.SemiBold))
            if (state.personalCalendars.isEmpty()) {
                Text(
                    "Encara no hi ha calendaris connectats.", style = Type.Small.copy(color = Palette.InkMuted),
                    modifier = Modifier.padding(top = 14.dp, bottom = 24.dp)
                )
            } else {
                Column(
                    Modifier.padding(top = 10.dp).weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    state.personalCalendars.forEach { calendar ->
                        Column(
                            Modifier.fillMaxWidth().background(Palette.Highlight, RoundedCornerShape(16.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(calendar.name, style = Type.Small.copy(fontWeight = FontWeight.SemiBold))
                                    Text("Color del calendari · enllaç privat guardat", style = Type.Tiny.copy(color = Palette.InkMuted))
                                }
                                TextButton(onClick = { deleteId = calendar.id }) { Text("Eliminar") }
                            }
                            CalendarColorPicker(
                                current = calendar.color,
                                onSelect = { hex -> callbacks.onChangeCalendarColor(calendar.id, hex) }
                            )
                        }
                    }
                }
            }
            if (state.calendarError != null) {
                Text(state.calendarError, style = Type.Small.copy(color = Palette.Accent), modifier = Modifier.padding(bottom = 12.dp))
            }
            Text("Afegir un calendari", style = Type.Large.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 14.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(50) },
                label = { Text("Nom: Família, Rai, Personal...") },
                singleLine = true, textStyle = Type.Small,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            OutlinedTextField(
                value = url, onValueChange = { url = it.take(2048) },
                label = { Text("Adreça secreta iCal de Google") },
                placeholder = { Text("https://calendar.google.com/…", style = Type.Tiny) },
                visualTransformation = if (showUrl) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true, textStyle = Type.Tiny,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            TextButton(onClick = { showUrl = !showUrl }) {
                Text(if (showUrl) "Amagar l'adreça" else "Mostrar l'adreça")
            }
            if (error != null) Text(error.orEmpty(), style = Type.Small.copy(color = Palette.Accent))
            Button(
                onClick = {
                    error = callbacks.onAddCalendar(name, url)
                    if (error == null) {
                        name = ""
                        url = ""
                        showUrl = false
                    }
                },
                enabled = name.isNotBlank() && url.isNotBlank() && state.personalCalendars.size < 5,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Afegir calendari") }
            Text(
                "Els esdeveniments s'actualitzen amb Internet. El marc és de només lectura.",
                style = Type.Tiny.copy(color = Palette.InkMuted),
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }

    deleteId?.let { id ->
        val calendar = state.personalCalendars.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Eliminar calendari?") },
            text = { Text("Es deixarà de mostrar «" + (calendar?.name ?: "aquest calendari") + "» al marc.") },
            confirmButton = {
                TextButton(onClick = { callbacks.onRemoveCalendar(id); deleteId = null }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel·lar") } }
        )
    }
}

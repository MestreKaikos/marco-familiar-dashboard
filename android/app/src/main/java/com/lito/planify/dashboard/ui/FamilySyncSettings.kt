package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * Pantalla pròpia d'Opcions, no una finestra modal. Els dos camps queden
 * desats en el dispositiu mentre es copia cadascun des d'una altra aplicació.
 * En pantalles estretes els controls es disposen en una sola columna desplaçable.
 */
@Composable
fun FamilySyncSettings(sync: FamilySync, dinners: DinnerPlan, family: LocalFamilyStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var confirmDisconnect by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }

    val settingsPanel: @Composable (Modifier) -> Unit = { modifier ->
        Column(
            modifier.background(Palette.Card, RoundedCornerShape(24.dp)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Text("Estat de la connexió", style = Type.Large.copy(fontWeight = FontWeight.SemiBold))
            Text(sync.status, style = Type.Small.copy(color = if (sync.connected) Palette.Online else Palette.InkSoft))
            if (sync.connected) {
                Text("Productes al núvol: " + (sync.cloudGroceries.takeIf { it >= 0 }?.toString() ?: "—"),
                    style = Type.Small)
                Text("Sopars al núvol: " + (sync.cloudDinners.takeIf { it >= 0 }?.toString() ?: "—"),
                    style = Type.Small)
                Text("Productes visibles al marc: " + family.groceries.size, style = Type.Small)
                Text("Canvis pendents d'enviar: " + sync.pendingCount, style = Type.Small)
                Text("Última actualització: " + (sync.lastUpdate ?: "encara no"),
                    style = Type.Tiny.copy(color = Palette.InkMuted))
                Button(
                    onClick = { scope.launch { sync.synchronize(dinners, family) } },
                    enabled = !sync.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (sync.busy) "Sincronitzant..." else "Sincronitzar ara") }
                Text("La compra i els sopars es comparteixen. Les feines, les estrelles, els premis i el PIN d'en Rai continuen locals.",
                    style = Type.Small.copy(color = Palette.InkSoft))
                OutlinedButton(onClick = { confirmDisconnect = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Desconnectar aquest dispositiu")
                }
            } else {
                Text("Copia un camp cada vegada. Pots sortir d'aquesta pantalla o obrir una altra aplicació: el que escriguis quedarà desat aquí.",
                    style = Type.Small.copy(color = Palette.InkSoft))
                OutlinedTextField(
                    value = sync.draftUrl,
                    onValueChange = { sync.editDraftUrl(it); attempted = false },
                    label = { Text("Adreça web d'Apps Script") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec", style = Type.Tiny) },
                    singleLine = true,
                    isError = attempted && !FamilySync.validUrl(sync.draftUrl),
                    textStyle = Type.Tiny,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sync.draftKey,
                    onValueChange = { sync.editDraftKey(it); attempted = false },
                    label = { Text("Clau familiar de 64 caràcters") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = attempted && !FamilySync.validKey(sync.draftKey.trim()),
                    textStyle = Type.Tiny,
                    modifier = Modifier.fillMaxWidth()
                )
                val valid = FamilySync.validUrl(sync.draftUrl) && FamilySync.validKey(sync.draftKey.trim())
                if (attempted && !valid) {
                    Text("Revisa els camps: l'URL ha de començar per https://script.google.com/ i acabar en /exec; la clau ha de tenir 64 caràcters hexadecimals.",
                        style = Type.Small.copy(color = Palette.Accent))
                }
                Text("Tria com unir les dades d'aquest Android amb les del full de Google.",
                    style = Type.Small.copy(color = Palette.InkSoft))
                Button(
                    onClick = {
                        attempted = true
                        if (valid) scope.launch { sync.connect(sync.draftUrl, sync.draftKey, true, dinners, family) }
                    },
                    enabled = !sync.busy && sync.draftUrl.isNotBlank() && sync.draftKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (sync.busy) "Connectant..." else "Connectar i fusionar dades") }
                Text("Recomanat. Conserva els productes i els sopars del núvol i hi afegeix els locals que hi falten.",
                    style = Type.Tiny.copy(color = Palette.InkMuted))
                OutlinedButton(
                    onClick = {
                        attempted = true
                        if (valid) scope.launch { sync.connect(sync.draftUrl, sync.draftKey, false, dinners, family) }
                    },
                    enabled = !sync.busy && sync.draftUrl.isNotBlank() && sync.draftKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Utilitzar només les dades del núvol") }
                Text("Aquesta opció substitueix els productes i els sopars locals. Les tasques i els premis no es modifiquen.",
                    style = Type.Tiny.copy(color = Palette.Accent))
            }
        }
    }
    val helpPanel: @Composable (Modifier) -> Unit = { modifier ->
        Column(
            modifier.background(Palette.Card, RoundedCornerShape(24.dp)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Sincronització familiar", style = Type.Title)
            Text("La mateixa llista de la compra i els mateixos sopars al marc i als mòbils.",
                style = Type.Small)
            Text("Com funciona", style = Type.Large.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "1. Utilitza el Google Sheets privat de la família.\n" +
                    "2. A Apps Script, comprova que hi ha Code.gs i Mobile.html.\n" +
                    "3. Copia la URL publicada que acaba en /exec.\n" +
                    "4. Enganxa-la aquí i després copia la clau privada.\n" +
                    "5. Prem Connectar i fusionar dades.",
                style = Type.Small
            )
            Text("La clau es desa només a les preferències privades d'aquest Android. No la comparteixis al xat ni a GitHub.",
                style = Type.Small.copy(color = Palette.Accent))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("← Tornar a Opcions")
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < 930.dp) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                helpPanel(Modifier.fillMaxWidth())
                settingsPanel(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                helpPanel(Modifier.weight(0.9f).fillMaxHeight().verticalScroll(rememberScrollState()))
                settingsPanel(Modifier.weight(1.1f).fillMaxHeight().verticalScroll(rememberScrollState()))
            }
        }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Desconnectar la família?") },
            text = { Text("La compra i els sopars actuals continuaran en aquest dispositiu, però deixaran de compartir-se fins a tornar-lo a connectar.") },
            confirmButton = {
                TextButton(onClick = { sync.disconnect(); confirmDisconnect = false }) { Text("Desconnectar") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel·lar") } }
        )
    }
}

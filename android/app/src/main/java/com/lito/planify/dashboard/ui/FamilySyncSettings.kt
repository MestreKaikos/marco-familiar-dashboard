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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import kotlinx.coroutines.launch

@Composable
fun FamilySyncSettings(sync: FamilySync, dinners: DinnerPlan, family: LocalFamilyStore, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var confirmDisconnect by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 30.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            Modifier.weight(1f).fillMaxHeight().background(Palette.Card, RoundedCornerShape(28.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("🔄 Sincronització familiar", style = Type.Title)
            Text("La mateixa llista de la compra i els mateixos sopars al marc i als mòbils.", style = Type.Small)
            Text("Com funciona", style = Type.Large.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "1. Crea un Google Sheets privat de la família.\n" +
                    "2. Al menú Extensions → Apps Script, afegeix el codi de la carpeta sync/google-apps-script.\n" +
                    "3. Executa setup_() a Apps Script: rebràs una clau aleatòria privada.\n" +
                    "4. Publica'l com a aplicació web i copia l'adreça /exec.\n" +
                    "5. Escriu l'adreça i la clau aquí. Comparteix amb la família el web del servei.",
                style = Type.Small
            )
            Text(
                "L'enllaç del servei només s'utilitza per sincronitzar. La clau no es mostra a les captures " +
                    "i no l'has de copiar al xat ni al GitHub.",
                style = Type.Small.copy(color = Palette.Accent)
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("← Tornar a Opcions")
            }
        }

        Column(
            Modifier.weight(1.05f).fillMaxHeight().background(Palette.Card, RoundedCornerShape(28.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Estat de la connexió", style = Type.Large.copy(fontWeight = FontWeight.SemiBold))
            Text(sync.status, style = Type.Small.copy(color = if (sync.connected) Palette.Online else Palette.InkSoft))
            if (sync.connected) {
                Text("Canvis pendents: " + sync.pendingCount, style = Type.Small)
                Text("Última actualització: " + (sync.lastUpdate ?: "encara no"),
                    style = Type.Tiny.copy(color = Palette.InkMuted))
                Button(
                    onClick = { scope.launch { sync.synchronize(dinners, family) } },
                    enabled = !sync.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (sync.busy) "Sincronitzant..." else "Sincronitzar ara") }
                Text(
                    "Les tasques i recompenses d'en Rai encara es guarden només al marc. " +
                        "La sincronització afecta únicament la compra i els sopars.",
                    style = Type.Small.copy(color = Palette.InkSoft)
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { confirmDisconnect = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Desconnectar aquest dispositiu")
                }
            } else {
                Text(
                    "Sense configuració, tot continua funcionant en aquest Android sense Internet.",
                    style = Type.Small.copy(color = Palette.InkMuted)
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it.take(350) },
                    label = { Text("Adreça web d'Apps Script") },
                    placeholder = { Text("https://script.google.com/macros/s/.../exec", style = Type.Tiny) },
                    singleLine = true, textStyle = Type.Tiny,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it.take(64) },
                    label = { Text("Clau familiar de 64 caràcters") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    textStyle = Type.Tiny, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Tria com unir les dades del marc amb el núvol. Aquesta elecció es fa només una vegada.",
                    style = Type.Small.copy(color = Palette.InkSoft)
                )
                Button(
                    onClick = { scope.launch {
                        if (sync.connect(url, key, importLocal = true, dinners, family)) key = ""
                    } },
                    enabled = !sync.busy && FamilySync.validUrl(url) && FamilySync.validKey(key),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (sync.busy) "Connectant..." else "Connectar i fusionar dades")
                }
                Text(
                    "Recomanat: conserva la compra existent al núvol, afegeix productes locals " +
                        "que hi falten i conserva els sopars del núvol quan coincideix la data.",
                    style = Type.Tiny.copy(color = Palette.InkMuted)
                )
                OutlinedButton(
                    onClick = { scope.launch {
                        if (sync.connect(url, key, importLocal = false, dinners, family)) key = ""
                    } },
                    enabled = !sync.busy && FamilySync.validUrl(url) && FamilySync.validKey(key),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Utilitzar només les dades del núvol") }
                Text(
                    "Aquesta segona opció substituirà la compra i els sopars d'aquest Android " +
                        "pels del núvol. No modificarà les feines, els premis ni el PIN.",
                    style = Type.Tiny.copy(color = Palette.Accent)
                )
            }
        }
    }
    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Desconnectar la família?") },
            text = { Text("Les dades actuals es mantindran en aquest dispositiu. Els canvis nous deixaran de compartir-se fins que el tornis a connectar.") },
            confirmButton = {
                TextButton(onClick = { sync.disconnect(); confirmDisconnect = false }) { Text("Desconnectar") }
            },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel·lar") } }
        )
    }
}

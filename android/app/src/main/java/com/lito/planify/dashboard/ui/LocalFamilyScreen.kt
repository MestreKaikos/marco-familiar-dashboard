package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import java.time.LocalDate

/** Una sola pantalla amb tres columnes: compra, feines i recompenses. Tot és local. */
@Composable
fun LocalFamilyScreen(store: LocalFamilyStore, today: LocalDate) {
    var groceryName by remember { mutableStateOf("") }
    var editGrocery by remember { mutableStateOf<LocalGrocery?>(null) }
    var addChore by remember { mutableStateOf(false) }
    var addReward by remember { mutableStateOf(false) }
    var adultAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var adultTitle by remember { mutableStateOf("") }

    val protect: (String, () -> Unit) -> Unit = { title, action ->
        adultTitle = title
        adultAction = action
    }

    Row(
        Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        FamilyPanel(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("🛒 Llista de la compra", style = Type.Large.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text("${store.groceries.count { !it.bought }} pendent(s)", style = Type.Tiny)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = groceryName,
                    onValueChange = { groceryName = it.take(80) },
                    placeholder = { Text("Afegir un producte", style = Type.Tiny) },
                    singleLine = true,
                    textStyle = Type.Small,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { store.addGrocery(groceryName); groceryName = "" }),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { store.addGrocery(groceryName); groceryName = "" }, enabled = groceryName.isNotBlank()) {
                    Text("Afegir")
                }
            }
            Spacer(Modifier.height(12.dp))
            if (store.groceries.isEmpty()) {
                Text("La llista és buida. Afegeix allò que falta a casa.", style = Type.Small.copy(color = Palette.InkSoft), modifier = Modifier.padding(top = 24.dp))
                Text("Idees per començar", style = Type.Tiny.copy(color = Palette.InkMuted), modifier = Modifier.padding(top = 22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Llet", "Fruita", "Pa").forEach { name ->
                        OutlinedButton(onClick = { store.addGrocery(name) }) { Text("+ $name") }
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(store.groceries.sortedWith(compareBy<LocalGrocery> { it.bought }), key = { it.id }) { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = item.bought, onCheckedChange = { store.toggleGrocery(item.id) })
                            Text(
                                item.name, style = Type.Small.copy(
                                    color = if (item.bought) Palette.InkMuted else Palette.Ink,
                                    textDecoration = if (item.bought) TextDecoration.LineThrough else TextDecoration.None
                                ),
                                modifier = Modifier.weight(1f).clickable { store.toggleGrocery(item.id) },
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = { editGrocery = item }) { Text("Editar") }
                            TextButton(onClick = { store.removeGrocery(item.id) }) { Text("✕") }
                        }
                    }
                }
                if (store.groceries.any { it.bought }) {
                    OutlinedButton(onClick = { store.clearBought() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Treure els productes comprats")
                    }
                }
            }
            Text("Només es desa en aquest dispositiu", style = Type.Tiny.copy(color = Palette.InkMuted), modifier = Modifier.padding(top = 12.dp))
        }

        FamilyPanel(Modifier.weight(1.12f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("✅ Feines de casa", style = Type.Large.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Button(onClick = { addChore = true }) { Text("+ Nova feina") }
            }
            Spacer(Modifier.height(12.dp))
            Text("En Rai pot marcar una feina feta; un adult la valida abans de sumar estrelles.", style = Type.Tiny.copy(color = Palette.InkSoft))
            Spacer(Modifier.height(14.dp))
            if (store.chores.isEmpty()) {
                Text("Encara no hi ha feines. Pots crear-ne per a en Rai o per a tota la família.", style = Type.Small.copy(color = Palette.InkSoft), modifier = Modifier.padding(top = 16.dp))
                Text("Idees de feines", style = Type.Tiny, modifier = Modifier.padding(top = 24.dp))
                listOf("Parar taula", "Recollir l'habitació", "Preparar la motxilla").forEach { name ->
                    OutlinedButton(
                        onClick = { protect("Afegir feina d'en Rai") { store.addChore(name, "Rai", true, 1) } },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    ) { Text("+ $name · 1 ⭐ al dia", maxLines = 1) }
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(store.chores, key = { it.id }) { task ->
                        val status = store.status(task, today)
                        Column(
                            Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                .background(Palette.Highlight, RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(task.title, style = Type.Small.copy(fontWeight = FontWeight.SemiBold), maxLines = 2)
                                    Text(
                                        "${task.owner} · ${if (task.daily) "Cada dia" else "Una vegada"}${if (task.owner == "Rai") " · +${task.stars} ⭐" else ""}",
                                        style = Type.Tiny.copy(color = Palette.InkSoft), modifier = Modifier.padding(top = 3.dp)
                                    )
                                }
                                TextButton(onClick = { protect("Eliminar feina") { store.removeChore(task.id) } }) { Text("✕") }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                when (status) {
                                    "todo" -> OutlinedButton(onClick = { store.requestDone(task.id, today) }) {
                                        Text(if (task.owner == "Rai") "Ja l'he feta" else "Marcar com feta")
                                    }
                                    "pending" -> {
                                        Text("Pendent de validar", style = Type.Tiny.copy(color = Palette.Warm), modifier = Modifier.weight(1f))
                                        Button(onClick = { protect("Validar feina d'en Rai") { store.approveChore(task.id, today) } }) { Text("Validar") }
                                        TextButton(onClick = { protect("Rebutjar feina") { store.rejectChore(task.id, today) } }) { Text("Rebutjar") }
                                    }
                                    else -> Text("✓ Feta ${if (task.daily) "avui" else ""}", style = Type.Small.copy(color = Palette.Online, fontWeight = FontWeight.SemiBold))
                                }
                            }
                        }
                    }
                }
            }
        }

        FamilyPanel(Modifier.weight(0.95f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("🎁 Recompenses", style = Type.Large.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Button(onClick = { addReward = true }) { Text("+ Premi") }
            }
            Text("Rai · ⭐ ${store.stars} disponibles", style = Type.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 14.dp, bottom = 14.dp))
            val requested = store.rewards.firstOrNull { it.id == store.pendingRewardId }
            if (requested != null) {
                Column(
                    Modifier.fillMaxWidth().background(Palette.Warm.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Text("⏳ Premi demanat: ${requested.title}", style = Type.Small.copy(fontWeight = FontWeight.SemiBold))
                    Text("Cal l'autorització d'un adult.", style = Type.Tiny.copy(color = Palette.InkSoft))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Button(onClick = { protect("Aprovar el premi") { store.decideReward(true) } }) { Text("Aprovar") }
                        OutlinedButton(onClick = { protect("Denegar el premi") { store.decideReward(false) } }) { Text("Denegar") }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (store.rewards.isEmpty()) {
                Text("Encara no hi ha recompenses. Definiu-les junts: una activitat especial, triar una pel·lícula…",
                    style = Type.Small.copy(color = Palette.InkSoft), modifier = Modifier.padding(top = 12.dp))
                Text("Exemple de premi", style = Type.Tiny, modifier = Modifier.padding(top = 20.dp))
                OutlinedButton(
                    onClick = { protect("Afegir una recompensa") { store.addReward("Triar una pel·lícula", 5) } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("+ Triar una pel·lícula · 5 ⭐") }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(store.rewards, key = { it.id }) { prize ->
                        Column(
                            Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                .background(Palette.Highlight, RoundedCornerShape(20.dp))
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(prize.title, style = Type.Small.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, modifier = Modifier.weight(1f))
                                TextButton(onClick = { protect("Eliminar recompensa") { store.removeReward(prize.id) } }) { Text("✕") }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("${prize.cost} ⭐", style = Type.Small.copy(color = Palette.InkSoft))
                                if (store.pendingRewardId == prize.id) {
                                    Text("Demanat ⏳", style = Type.Tiny.copy(color = Palette.Warm))
                                } else if (store.stars >= prize.cost && store.pendingRewardId == null) {
                                    OutlinedButton(onClick = { store.requestReward(prize.id) }) { Text("Demanar") }
                                } else {
                                    Text(
                                        if (store.pendingRewardId != null) "Hi ha un premi pendent" else "Falten ${(prize.cost - store.stars).coerceAtLeast(0)} ⭐",
                                        style = Type.Tiny.copy(color = Palette.InkMuted)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Text("Punts validats pels adults · premis configurables", style = Type.Tiny.copy(color = Palette.InkMuted), modifier = Modifier.padding(top = 12.dp))
        }
    }

    editGrocery?.let { item ->
        TextEntryDialog("Editar producte", item.name, "Producte", onDismiss = { editGrocery = null }) { name ->
            store.renameGrocery(item.id, name)
            editGrocery = null
        }
    }
    if (addChore) {
        ChoreCreator(onDismiss = { addChore = false }) { title, owner, daily, stars ->
            protect("Afegir feina") { store.addChore(title, owner, daily, stars) }
            addChore = false
        }
    }
    if (addReward) {
        RewardCreator(onDismiss = { addReward = false }) { title, cost ->
            protect("Afegir recompensa") { store.addReward(title, cost) }
            addReward = false
        }
    }
    adultAction?.let { action ->
        AdultPinDialog(
            title = adultTitle,
            configured = store.pinConfigured,
            onDismiss = { adultAction = null },
            onConfirm = { pin ->
                if (store.checkOrSetAdultPin(pin)) {
                    action()
                    adultAction = null
                    true
                } else false
            }
        )
    }
}

@Composable
private fun FamilyPanel(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(modifier.background(Palette.Card, RoundedCornerShape(28.dp)).padding(horizontal = 24.dp, vertical = 22.dp), content = content)
}

@Composable
private fun TextEntryDialog(
    title: String, initial: String, label: String, onDismiss: () -> Unit, onSave: (String) -> Unit
) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = Type.Large) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it.take(100) }, label = { Text(label) },
                singleLine = true, textStyle = Type.Small, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onSave(text) }) { Text("Desar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel·lar") } }
    )
}

@Composable
private fun ChoreCreator(onDismiss: () -> Unit, onSave: (String, String, Boolean, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("Rai") }
    var daily by remember { mutableStateOf(true) }
    var stars by remember { mutableStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova feina", style = Type.Large) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(100) },
                    label = { Text("Quina feina s'ha de fer?") }, singleLine = true,
                    textStyle = Type.Small, modifier = Modifier.fillMaxWidth()
                )
                Text("Qui la farà?", style = Type.Small)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Rai", "Família").forEach { option ->
                        FilterChip(selected = owner == option, onClick = { owner = option },
                            label = { Text(option) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = daily, onCheckedChange = { daily = it })
                    Text("Repetir cada dia", style = Type.Small)
                }
                if (owner == "Rai") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Estrelles per feina", style = Type.Small, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { stars = (stars - 1).coerceAtLeast(1) }, enabled = stars > 1) { Text("−") }
                        Text("$stars ⭐", style = Type.Small)
                        OutlinedButton(onClick = { stars = (stars + 1).coerceAtMost(5) }, enabled = stars < 5) { Text("+") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, owner, daily, stars) }, enabled = title.isNotBlank()) { Text("Afegir feina") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel·lar") } }
    )
}

@Composable
private fun RewardCreator(onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var points by remember { mutableStateOf("5") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova recompensa", style = Type.Large) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(100) },
                    label = { Text("Quin és el premi?") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = points, onValueChange = { points = it.filter(Char::isDigit).take(3) },
                    label = { Text("Estrelles necessàries") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank() && (points.toIntOrNull() ?: 0) > 0,
                onClick = { onSave(title, points.toIntOrNull() ?: 5) }) { Text("Crear premi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel·lar") } }
    )
}

/** El PIN es crea al primer ús i només es desa com a hash amb salt al dispositiu. */
@Composable
private fun AdultPinDialog(title: String, configured: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = Type.Large) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (configured) "Introdueix el PIN dels adults per confirmar."
                    else "Configura ara un PIN de 4 xifres per validar feines i recompenses. Apunta'l en un lloc segur.",
                    style = Type.Small
                )
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(4); error = false },
                    label = { Text(if (configured) "PIN dels adults" else "Nou PIN dels adults") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) Text("PIN incorrecte. Comprova les quatre xifres.", style = Type.Tiny.copy(color = Palette.Accent))
                if (!configured) Text(
                    "Si oblides el PIN, caldrà esborrar les dades de l'aplicació per reiniciar-lo.",
                    style = Type.Tiny.copy(color = Palette.InkMuted)
                )
            }
        },
        confirmButton = {
            TextButton(enabled = pin.length == 4, onClick = { if (!onConfirm(pin)) { error = true; pin = "" } }) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel·lar") } }
    )
}

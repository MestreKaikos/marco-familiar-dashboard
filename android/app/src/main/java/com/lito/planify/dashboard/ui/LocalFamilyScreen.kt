package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import java.time.LocalDate

/** Família és exclusivament l’espai de les feines i recompenses d’en Rai. */
@Composable
fun LocalFamilyScreen(store: LocalFamilyStore, today: LocalDate, connected: Boolean = false, pending: Int = 0, tasksShared: Boolean = false) {
    var addChore by remember { mutableStateOf(false) }
    var addReward by remember { mutableStateOf(false) }
    var adultAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var adultTitle by remember { mutableStateOf("") }

    val protect: (String, () -> Unit) -> Unit = { title, action ->
        adultTitle = title
        adultAction = action
    }

    // Els dos panells es mostren de costat en un marc ample i apilats en un mòbil vertical.
    val choresPanel: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("✅ Feines d’en Rai", style = Type.Large.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Button(onClick = { addChore = true }, modifier = Modifier.heightIn(min = 68.dp)) { Text("+ Nova feina", style = Type.Small.copy(fontSize = 21.sp, color = Color.White)) }
            }
            Spacer(Modifier.height(12.dp))
            Text("En Rai pot marcar una feina feta; un adult la valida abans de sumar estrelles.", style = Type.Tiny.copy(color = Palette.InkSoft))
            Text(
                if (tasksShared) "✓ Tasques i punts compartits amb Google Sheets" else
                    if (connected) "Feines locals · cal actualitzar Apps Script per sincronitzar-les" else
                        "Feines locals · connecta la sincronització a Opcions",
                style = Type.Tiny.copy(color = if (tasksShared) Palette.Online else Palette.InkMuted),
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(14.dp))
            if (store.chores.isEmpty()) {
                Text("Encara no hi ha feines d’en Rai. Pots crear-les aquí.", style = Type.Small.copy(color = Palette.InkSoft), modifier = Modifier.padding(top = 16.dp))
                Text("Idees de feines", style = Type.Tiny, modifier = Modifier.padding(top = 24.dp))
                listOf("Parar taula", "Recollir l'habitació", "Preparar la motxilla").forEach { name ->
                    OutlinedButton(
                        onClick = { protect("Afegir feina d'en Rai") { store.addChore(name, "Rai", true, 1) } },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    ) { Text("+ $name · 1 ⭐ al dia", maxLines = 1) }
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(orderedRaiChores(store.chores.toList(), today), key = { it.id }) { task ->
                        val status = store.status(task, today)
                        Column(
                            Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                .background(
                                    if (status == "approved") Palette.Online.copy(alpha = 0.13f)
                                    else Palette.Highlight, RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(task.title, style = Type.Small.copy(fontWeight = FontWeight.SemiBold), maxLines = 2)
                                    Text(
                                        "${task.owner} · ${when (task.frequency) { "daily" -> "Cada dia"; "weekly", "weekdays" -> "Dies: ${task.days}"; "slots" -> "Per franges"; else -> "Una vegada" }}${if (task.owner == "Rai") " · +${task.stars} ⭐" else ""}",
                                        style = Type.Tiny.copy(color = Palette.InkSoft), modifier = Modifier.padding(top = 3.dp)
                                    )
                                }
                                TextButton(onClick = { protect("Eliminar feina") { store.removeChore(task.id) } }) { Text("✕") }
                            }
                            Spacer(Modifier.height(10.dp))
                            when (status) {
                                "todo" -> {
                                    // Objectiu tàctil gran: en Rai pot demanar la validació
                                    // sense haver d'encertar una casella petita.
                                    Button(
                                        onClick = { store.requestDone(task.id, today) },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
                                        shape = RoundedCornerShape(18.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF7857BC),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text(
                                            if (task.owner == "Rai") "✓ JA L'HE FETA!" else "✓ Marcar com a feta",
                                            style = Type.Small.copy(
                                                fontSize = 27.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }
                                "pending" -> {
                                    Box(
                                        Modifier.fillMaxWidth()
                                            .background(Palette.Warm.copy(alpha = 0.15f), RoundedCornerShape(15.dp))
                                            .padding(horizontal = 14.dp, vertical = 13.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("⏳ Esperant validació", style = Type.Small.copy(
                                            color = Palette.Ink, fontWeight = FontWeight.SemiBold,
                                            fontSize = 24.sp
                                        ))
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = { protect("Validar feina d'en Rai") { store.approveChore(task.id, today) } },
                                            modifier = Modifier.weight(1f).heightIn(min = 60.dp)
                                        ) {
                                            Text("Validar", style = Type.Small.copy(
                                                fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            ))
                                        }
                                        OutlinedButton(
                                            onClick = { protect("Rebutjar feina") { store.rejectChore(task.id, today) } },
                                            modifier = Modifier.weight(1f).heightIn(min = 60.dp)
                                        ) {
                                            Text("Rebutjar", style = Type.Small.copy(fontSize = 22.sp))
                                        }
                                    }
                                }
                                "approved" -> {
                                    Box(
                                        Modifier.fillMaxWidth().heightIn(min = 70.dp)
                                            .background(Palette.Online.copy(alpha = 0.17f), RoundedCornerShape(18.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (task.frequency == "once") "✓ Feta · no es repeteix"
                                            else "✓ Feta avui",
                                            style = Type.Small.copy(
                                                color = Palette.Online, fontWeight = FontWeight.Bold,
                                                fontSize = 27.sp
                                            ), maxLines = 1
                                        )
                                    }
                                }
                                "not_due" -> Text("No toca avui", style = Type.Small.copy(color = Palette.InkMuted))
                                "slots" -> Text("Gestiona les franges des del mòbil", style = Type.Tiny.copy(color = Palette.InkMuted))
                                else -> Text("No disponible avui", style = Type.Small.copy(color = Palette.InkMuted))
                            }
                        }
                    }
                }
            }
    }
    val rewardsPanel: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("🎁 Recompenses", style = Type.Large.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Button(onClick = { addReward = true }, modifier = Modifier.heightIn(min = 68.dp)) { Text("+ Nou premi", style = Type.Small.copy(fontSize = 21.sp, color = Color.White)) }
            }
            Text("Rai · ⭐ ${store.stars} disponibles", style = Type.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 14.dp, bottom = 14.dp))
            val requested = store.rewards.firstOrNull { it.id == store.pendingRewardId }
            if (requested != null) {
                Column(
                    Modifier.fillMaxWidth().background(Palette.Warm.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Text("⏳ Premi demanat: ${requested.title}", style = Type.Small.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        if (tasksShared) "Cal aprovar o denegar aquest premi des de la pàgina mòbil amb el PIN familiar."
                        else "Cal l'autorització d'un adult.",
                        style = Type.Tiny.copy(color = Palette.InkSoft)
                    )
                    if (!tasksShared) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Button(onClick = { protect("Aprovar el premi") { store.decideReward(true) } }) { Text("Aprovar") }
                            OutlinedButton(onClick = { protect("Denegar el premi") { store.decideReward(false) } }) { Text("Denegar") }
                        }
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
                                    OutlinedButton(onClick = { store.requestReward(prize.id) },
                                        enabled = !store.cloudManaged || connected) { Text("Demanar") }
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
            Text(if (tasksShared) "Saldo i premis del Google Sheets" else "Punts i premis locals", style = Type.Tiny.copy(color = Palette.InkMuted), modifier = Modifier.padding(top = 12.dp))
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 830.dp) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                FamilyPanel(Modifier.weight(1.12f).fillMaxHeight(), choresPanel)
                FamilyPanel(Modifier.weight(0.95f).fillMaxHeight(), rewardsPanel)
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                FamilyPanel(Modifier.fillMaxWidth().height(540.dp), choresPanel)
                FamilyPanel(Modifier.fillMaxWidth().height(480.dp), rewardsPanel)
            }
        }
    }

    if (addChore) {
        ChoreCreator(cloud = tasksShared, onDismiss = { addChore = false }) { title, owner, daily, stars ->
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

/** La mateixa llista de compra i les mateixes mutacions, ara al costat dels Recordatoris. */
@Composable
fun FamilyShoppingPanel(
    store: LocalFamilyStore,
    connected: Boolean,
    pending: Int,
    modifier: Modifier = Modifier
) {
    var groceryName by rememberSaveable { mutableStateOf("") }
    var editGrocery by remember { mutableStateOf<LocalGrocery?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val addProduct = {
        if (groceryName.isNotBlank()) {
            store.addGrocery(groceryName)
            groceryName = ""
            focus.clearFocus()
            keyboard?.hide()
        }
    }
    FamilyPanel(modifier) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("🛒 Llista de la compra", style = Type.Large.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Text("${store.groceries.count { !it.bought }} ${if (store.groceries.count { !it.bought } == 1) "pendent" else "pendents"}", style = Type.Tiny)
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
                    keyboardActions = KeyboardActions(onDone = { addProduct() }),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { addProduct() }, enabled = groceryName.isNotBlank(), modifier = Modifier.heightIn(min = 66.dp)) {
                    Text("Afegir", style = Type.Small.copy(fontSize = 21.sp))
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

                            Text(
                                item.name, style = Type.Small.copy(
                                    color = if (item.bought) Palette.InkMuted else Palette.Ink,
                                    textDecoration = if (item.bought) TextDecoration.LineThrough else TextDecoration.None
                                ),
                                modifier = Modifier.weight(1f).clickable { store.toggleGrocery(item.id) },
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            Button(onClick = { store.toggleGrocery(item.id) }, modifier = Modifier.heightIn(min = 64.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (item.bought) Palette.Online else Color(0xFF7857BC), contentColor = Color.White)) {
                                Text(if (item.bought) "✓ Comprat" else "Marcar comprat", style = Type.Small.copy(color = Color.White, fontWeight = FontWeight.Bold))
                            }
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
            Text(
                if (!connected) "Només es desa en aquest dispositiu"
                else if (pending > 0) "$pending ${if (pending == 1) "canvi pendent" else "canvis pendents"} de sincronitzar"
                else "Compra compartida · dades del marc i dels mòbils",
                style = Type.Tiny.copy(color = Palette.InkMuted),
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    editGrocery?.let { item ->
        TextEntryDialog("Editar producte", item.name, "Producte", onDismiss = { editGrocery = null }) { name ->
            store.renameGrocery(item.id, name)
            editGrocery = null
        }
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
    val dismissKeyboard = familyFormKeyboardActions()
    FamilyFormDialog(title = title, confirmLabel = "Desar",
        confirmEnabled = text.isNotBlank(),
        onDismiss = onDismiss, onConfirm = { onSave(text) }) {
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(100) },
            label = { Text(label) }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = dismissKeyboard, textStyle = Type.Small,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ChoreCreator(
    cloud: Boolean, onDismiss: () -> Unit,
    onSave: (String, String, Boolean, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var owner by remember { mutableStateOf("Rai") }
    var daily by remember { mutableStateOf(true) }
    var stars by remember { mutableStateOf(1) }
    val dismissKeyboard = familyFormKeyboardActions()
    FamilyFormDialog(title = "Nova feina", confirmLabel = "Afegir feina",
        confirmEnabled = title.isNotBlank(),
        onDismiss = onDismiss, onConfirm = { onSave(title, owner, daily, stars) }) {
        OutlinedTextField(
            value = title, onValueChange = { title = it.take(100) },
            label = { Text("Quina feina s'ha de fer?") }, singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = dismissKeyboard,
            textStyle = Type.Small, modifier = Modifier.fillMaxWidth()
        )
        Text("Qui la farà?", style = Type.Small)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            (if (cloud) listOf("Rai") else listOf("Rai", "Família")).forEach { option ->
                FilterChip(selected = owner == option, onClick = { owner = option },
                    label = { Text(option, style = Type.Small) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = daily, onCheckedChange = { daily = it })
            Text("Repetir cada dia", style = Type.Small)
        }
        if (owner == "Rai") {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Estrelles per feina", style = Type.Small, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { stars = (stars - 1).coerceAtLeast(1) },
                    enabled = stars > 1, modifier = Modifier.heightIn(min = 62.dp)) {
                    Text("−", style = Type.Body)
                }
                Text("$stars ⭐", style = Type.Small)
                OutlinedButton(onClick = { stars = (stars + 1).coerceAtMost(5) },
                    enabled = stars < 5, modifier = Modifier.heightIn(min = 62.dp)) {
                    Text("+", style = Type.Body)
                }
            }
        }
    }
}

@Composable
private fun RewardCreator(onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var title by remember { mutableStateOf("") }
    var points by remember { mutableStateOf("5") }
    val dismissKeyboard = familyFormKeyboardActions()
    FamilyFormDialog(title = "Nova recompensa", confirmLabel = "Crear premi",
        confirmEnabled = title.isNotBlank() && (points.toIntOrNull() ?: 0) > 0,
        onDismiss = onDismiss,
        onConfirm = { onSave(title, points.toIntOrNull() ?: 5) }) {
        OutlinedTextField(
            value = title, onValueChange = { title = it.take(100) },
            label = { Text("Quin és el premi?") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = dismissKeyboard, textStyle = Type.Small, singleLine = true
        )
        OutlinedTextField(
            value = points,
            onValueChange = { points = it.filter(Char::isDigit).take(3) },
            label = { Text("Estrelles necessàries") }, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = dismissKeyboard, textStyle = Type.Small, singleLine = true
        )
    }
}

/** El PIN es crea al primer ús i només es desa com a hash amb salt al dispositiu. */
@Composable
private fun AdultPinDialog(
    title: String, configured: Boolean,
    onDismiss: () -> Unit, onConfirm: (String) -> Boolean
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val dismissKeyboard = familyFormKeyboardActions()
    FamilyFormDialog(title = title, confirmLabel = "Confirmar",
        confirmEnabled = pin.length == 4,
        onDismiss = onDismiss,
        onConfirm = { if (!onConfirm(pin)) { error = true; pin = "" } }) {
        Text(
            if (configured) "Introdueix el PIN dels adults per confirmar."
            else "Configura un PIN de 4 xifres per validar feines i recompenses. Desa'l en un lloc segur.",
            style = Type.Small
        )
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(4); error = false },
            label = { Text(if (configured) "PIN dels adults" else "Nou PIN dels adults") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done),
            keyboardActions = dismissKeyboard, singleLine = true,
            modifier = Modifier.fillMaxWidth(), textStyle = Type.Small
        )
        if (error) Text("PIN incorrecte. Comprova les quatre xifres.",
            style = Type.Tiny.copy(color = Palette.Accent))
        if (!configured) Text(
            "Si oblides el PIN, caldrà esborrar les dades de l'aplicació per reiniciar-lo.",
            style = Type.Tiny.copy(color = Palette.InkMuted)
        )
    }
}

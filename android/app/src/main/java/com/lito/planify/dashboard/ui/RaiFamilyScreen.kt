package com.lito.planify.dashboard.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.ui.theme.Type
import kotlinx.coroutines.launch

/** Família i compra sempre llegeixen el mateix estat compartit que la portada. */
@Composable
fun RaiFamilyScreen(
    cloud: FamilyCloudStore,
    onSetupChange: (Boolean) -> Unit = {}
) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val draftPrefs=remember(context) {
        context.getSharedPreferences("marc_setup_draft",Context.MODE_PRIVATE)
    }
    var showQr by remember { mutableStateOf(false) }
    var showDirect by remember {
        mutableStateOf(draftPrefs.getBoolean("in_progress",false))
    }
    var productName by remember { mutableStateOf("") }

    LaunchedEffect(showDirect) { onSetupChange(showDirect) }
    DisposableEffect(Unit) { onDispose { onSetupChange(false) } }

    if(showDirect) {
        RaiDirectSetup(
            onDismiss = {
                draftPrefs.edit().putBoolean("in_progress",false).apply()
                showDirect=false
            },
            onSaved = { _,_ ->
                draftPrefs.edit().putBoolean("in_progress",false).apply()
                showQr=false
                showDirect=false
                scope.launch { cloud.refresh() }
            }
        )
        return
    }
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)
        ) {
            Text("👨‍👩‍👦 Família",style=Type.Title,modifier=Modifier.weight(1f))
            OutlinedButton(onClick={showQr=!showQr}) {
                Text(if(showQr)"Tancar QR" else "QR")
            }
            OutlinedButton(onClick={
                draftPrefs.edit().putBoolean("in_progress",true).apply()
                showQr=false
                showDirect=true
            }) { Text("Configurar") }
            OutlinedButton(onClick={scope.launch {cloud.refresh()}},enabled=!cloud.loading && !cloud.working) {
                Text("Actualitzar")
            }
        }
        if(showQr) {
            RaiPairingView { _,_ ->
                showQr=false
                scope.launch { cloud.refresh() }
            }
        }
        if(cloud.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(cloud.error.isNotBlank()) Text(
            cloud.error + " · Es conserven les darreres dades disponibles.",
            color=MaterialTheme.colorScheme.error,
            style=MaterialTheme.typography.bodySmall
        )
        if(!cloud.connected) {
            Text("Prem Configurar per connectar la compra, els sopars i les tasques amb Google Sheets.")
        }
        Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                val groceries=cloud.data?.groceries.orEmpty()
                val pending=groceries.count { !it.bought }
                Text("🛒 Compra · $pending pendents",style=Type.Large)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value=productName,onValueChange={productName=it.take(80)},
                        placeholder={Text("Afegir producte")},
                        modifier=Modifier.weight(1f),singleLine=true
                    )
                    Button(
                        enabled=cloud.connected && !cloud.working && productName.isNotBlank(),
                        onClick={
                            val name=productName.trim()
                            productName=""
                            scope.launch { cloud.grocerySet(null,name,false) }
                        }
                    ) {Text("+")}
                }
                if(cloud.data==null) {
                    Text(if(cloud.connected) "Carregant la llista compartida…" else "Connecta el marc per veure la compra.")
                } else if(groceries.isEmpty()) {
                    Text("La llista de la compra és buida.")
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(groceries.sortedBy { it.bought },key={it.id}) { item ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical=3.dp),
                                verticalAlignment=Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked=item.bought,
                                    enabled=!cloud.working,
                                    onCheckedChange={checked->
                                        scope.launch { cloud.grocerySet(item.id,item.name,checked) }
                                    }
                                )
                                Text(
                                    item.name,modifier=Modifier.weight(1f),
                                    textDecoration=if(item.bought)TextDecoration.LineThrough else TextDecoration.None
                                )
                                TextButton(
                                    enabled=!cloud.working,
                                    onClick={scope.launch {cloud.groceryDelete(item.id)}}
                                ) {Text("✕")}
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1.06f).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                val snap=cloud.tasks
                Text("⭐ Tasques d'en Rai",style=Type.Large)
                if(snap==null) {
                    Text(if(cloud.connected) "Carregant tasques…" else "Connecta la família per veure les tasques.")
                } else {
                    Text("Saldo: ${snap.points} punts",style=Type.Body)
                    LazyColumn(Modifier.weight(1f)) {
                        items(snap.chores,key={it.id+"|"+it.slot}) { chore ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical=3.dp),
                                verticalAlignment=Alignment.CenterVertically
                            ) {
                                Text(
                                    chore.title + (if(chore.slot=="única")"" else " · ${chore.slot}") +
                                        " (+${chore.points})",
                                    modifier=Modifier.weight(1f)
                                )
                                Button(
                                    enabled=!cloud.working,
                                    onClick={
                                        scope.launch {
                                            cloud.actionTask(
                                                if(chore.done)"tasks_undo" else "tasks_done",
                                                chore.id,chore.slot
                                            )
                                        }
                                    }
                                ) { Text(if(chore.done)"↩" else "✓ Feta") }
                            }
                        }
                        item {
                            Spacer(Modifier.height(12.dp))
                            Text("🎁 Recompenses",style=Type.Large)
                        }
                        items(snap.rewards,key={it.id}) { reward ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical=3.dp),
                                verticalAlignment=Alignment.CenterVertically
                            ) {
                                Text("${reward.title} · ${reward.cost} punts",Modifier.weight(1f))
                                Button(
                                    enabled=!cloud.working && !reward.pending && snap.points>=reward.cost,
                                    onClick={scope.launch {cloud.actionTask("rewards_claim",reward.id)}}
                                ) {Text(if(reward.pending)"Pendent" else "Demanar")}
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import kotlinx.coroutines.launch

private val SpotifyGreen = Color(0xFF1DB954)

/** Comandament Spotify Connect: el marc no reprodueix mai àudio. */
@Composable
internal fun SpotifyPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(context) { SpotifyController(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val cast = remember(context) { CastDiscovery(context.applicationContext) }
    DisposableEffect(cast) {
        cast.start()
        onDispose { cast.stop() }
    }
    var all by remember { mutableStateOf(false) }
    var castExpanded by remember { mutableStateOf(false) }
    var castSearchTimedOut by remember { mutableStateOf(false) }
    var castSearchAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(castSearchAttempt) {
        castSearchTimedOut = false
        kotlinx.coroutines.delay(20_000)
        castSearchTimedOut = true
    }
    var confirmUnlink by remember { mutableStateOf(false) }

    LaunchedEffect(player.linked) {
        if (player.linked) player.refresh()
    }

    Panel(modifier, compact = true) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("♫ Spotify", style = Type.Large.copy(color = SpotifyGreen,
                fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            if (player.linked) {
                TextButton(onClick = { scope.launch { player.refresh() } },
                    enabled = !player.loading) { Text("Actualitzar") }
                TextButton(onClick = { confirmUnlink = true }) { Text("Desvincular") }
            }
        }
        if (!player.linked) {
            SpotifyPairingPanel(player)
            return@Panel
        }

        Text("ALTAVEU PREDETERMINAT", style = Type.Tiny.copy(
            color = Palette.InkMuted, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            modifier = Modifier.padding(top = 8.dp, bottom = 7.dp))
        if (player.speakers.isEmpty()) {
            Text("ABAIX no està disponible. Activa Spotify a ABAIX des del mòbil i prem Actualitzar.",
                style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = 17.sp),
                modifier = Modifier.padding(bottom = 7.dp))
        } else {
            Text("✓ ABAIX",
                style = Type.Tiny.copy(fontSize = 18.sp, color = Color(0xFF092413),
                    fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .background(SpotifyGreen, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp))
        }

        Row(Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("GOOGLE CAST · XARXA LOCAL", style = Type.Tiny.copy(
                color = Palette.InkMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f))
            TextButton(onClick = { castExpanded = !castExpanded }) {
                Text(if (castExpanded) "Amagar ▲" else "Detalls ▼")
            }
            if (castExpanded) {
                TextButton(onClick = { cast.stop(); cast.start(); castSearchAttempt++ }) { Text("Cercar") }
            }
        }
        val castGroup = cast.names.firstOrNull { it.equals("Abaix", ignoreCase = true) }
        Text(when {
            castGroup != null -> "✓ Abaix detectat per Google Cast"
            cast.error.value.isNotBlank() -> cast.error.value
            castSearchTimedOut -> "Abaix no detectat després de 20 segons de cerca."
            cast.scanning.value -> "Cercant Abaix per Google Cast…"
            else -> "No s'ha detectat Abaix per Google Cast"
        }, style = Type.Tiny.copy(
            color = if (castGroup != null) Palette.Online else Palette.InkSoft,
            fontSize = 16.sp))
        if (castExpanded) {
            if (cast.names.isNotEmpty()) {
                Text("Dispositius Cast trobats: " + cast.names.joinToString(" · "),
                    style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = 14.sp), maxLines = 3)
            } else if (castSearchTimedOut) {
                Text("Cap dispositiu Cast detectat. Comprova que el marc i els Nest siguin a la mateixa xarxa.",
                    style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = 14.sp), maxLines = 3)
            }
            if (cast.details.isNotEmpty()) {
                Text("Detalls Cast (nom · model · capacitats):",
                    style = Type.Tiny.copy(color = Palette.InkMuted, fontSize = 14.sp))
                cast.details.forEach { device ->
                    Text("• " + device, style = Type.Tiny.copy(
                        color = Palette.InkSoft, fontSize = 14.sp), maxLines = 2)
                }
            }
            val members = listOf("Cuina", "Cuina-Menjador")
            val foundMembers = members.filter { expected ->
                cast.names.any { it.equals(expected, ignoreCase = true) }
            }
            if (foundMembers.isNotEmpty()) {
                Text("Nest del grup detectats: " + foundMembers.joinToString(" + "),
                    style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = 14.sp))
            }
        }


        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (all) "TOTES LES PLAYLISTS" else "PLAYLISTS FAVORITES",
                style = Type.Tiny.copy(color = Palette.InkMuted, fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp), modifier = Modifier.weight(1f))
            TextButton(onClick = { all = !all }) {
                Text(if (all) "Veure favorites" else "Veure totes",
                    style = Type.Tiny.copy(color = SpotifyGreen, fontSize = 17.sp))
            }
        }
        val visible = if (all || player.favorites.isEmpty()) player.lists
            else player.lists.filter { it.uri in player.favorites }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (player.favorites.isEmpty()) {
                Text("Marca amb ★ les playlists que vols tenir a mà.",
                    style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = 16.sp))
            }
            visible.forEach { playlist ->
                Row(Modifier.fillMaxWidth()
                    .background(Palette.Highlight, RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (playlist.uri in player.favorites) "★" else "☆",
                        style = Type.Body.copy(color = SpotifyGreen),
                        modifier = Modifier.clickable { player.toggleFavorite(playlist.uri) }
                            .padding(horizontal = 9.dp, vertical = 6.dp))
                    Text(playlist.title, style = Type.Small.copy(fontSize = 19.sp),
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { player.play(playlist) } },
                        enabled = !player.loading && player.selectedDeviceId.isNotEmpty()) {
                        Text("▶", style = Type.Body.copy(color = SpotifyGreen))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 9.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { scope.launch { player.toggleShuffle() } },
                enabled = !player.loading && player.selectedDeviceId.isNotEmpty()) {
                Text(if (player.shuffleEnabled) "ALEATORI ✓" else "ALEATORI",
                    style = Type.Tiny.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        color = if (player.shuffleEnabled) SpotifyGreen else Palette.InkMuted))
            }
            listOf("⏮" to "previous", "⏸" to "pause",
                "▶" to "resume", "⏭" to "next").forEach { (symbol, command) ->
                TextButton(onClick = { scope.launch { player.control(command) } },
                    enabled = !player.loading && player.selectedDeviceId.isNotEmpty()) {
                    Text(symbol, style = Type.Large.copy(fontSize = 26.sp, color = SpotifyGreen))
                }
            }
        }
        Text(if (player.shuffleEnabled) "Aleatori activat" else "Aleatori desactivat",
            style = Type.Tiny.copy(color = if (player.shuffleEnabled) SpotifyGreen else Palette.InkMuted,
                fontSize = 14.sp))
        Text(player.status, style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = 16.sp),
            maxLines = 3, modifier = Modifier.padding(top = 5.dp))
        Text("Reproducció configurada només per a ABAIX.",
            style = Type.Tiny.copy(color = Palette.InkMuted, fontSize = 14.sp),
            maxLines = 2, modifier = Modifier.padding(top = 4.dp))
    }

    if (confirmUnlink) {
        AlertDialog(onDismissRequest = { confirmUnlink = false },
            title = { Text("Desvincular Spotify?") },
            text = { Text("S'eliminaran les credencials del marc, no les del compte Spotify.") },
            confirmButton = {
                TextButton(onClick = { player.disconnect(); confirmUnlink = false }) {
                    Text("Desvincular")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnlink = false }) { Text("Cancel·lar") }
            })
    }
}

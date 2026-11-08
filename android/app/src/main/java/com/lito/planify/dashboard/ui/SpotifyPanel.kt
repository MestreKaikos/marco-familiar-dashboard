package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
    var all by remember { mutableStateOf(false) }
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

        Text("ALTAVEUS / GRUPS NEST", style = Type.Tiny.copy(
            color = Palette.InkMuted, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            modifier = Modifier.padding(top = 8.dp, bottom = 7.dp))
        if (player.speakers.isEmpty()) {
            Text("No hi ha cap altaveu actiu. Activa Spotify al Nest des del mòbil i prem Actualitzar.",
                style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = 17.sp),
                modifier = Modifier.padding(bottom = 7.dp))
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                player.speakers.forEach { speaker ->
                    val selected = player.selectedDeviceId == speaker.id
                    Text((if (selected) "✓ " else "") + speaker.name,
                        style = Type.Tiny.copy(fontSize = 17.sp,
                            color = if (selected) Color(0xFF092413) else Palette.Ink,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                        modifier = Modifier
                            .background(if (selected) SpotifyGreen else Palette.Highlight,
                                RoundedCornerShape(12.dp))
                            .clickable { player.chooseDevice(speaker.id) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        maxLines = 1)
                }
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
            listOf("⏮" to "previous", "⏸" to "pause",
                "▶" to "resume", "⏭" to "next").forEach { (symbol, command) ->
                TextButton(onClick = { scope.launch { player.control(command) } },
                    enabled = !player.loading && player.selectedDeviceId.isNotEmpty()) {
                    Text(symbol, style = Type.Large.copy(fontSize = 26.sp, color = SpotifyGreen))
                }
            }
        }
        Text(player.status, style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = 16.sp),
            maxLines = 3, modifier = Modifier.padding(top = 5.dp))
        Text("Un grup Nest funciona com un sol dispositiu si Spotify el detecta.",
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

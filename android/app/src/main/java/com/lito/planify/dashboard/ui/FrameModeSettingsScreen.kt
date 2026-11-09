package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FrameModeSettingsScreen(mode: FrameModeSettings, onBack: () -> Unit) {
    val config = mode.config
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Fotos i agenda", style = Type.Title)
            OutlinedButton(onClick = onBack) { Text("← Tornar a Opcions") }
        }
        Column(Modifier.fillMaxWidth().background(Palette.Card, RoundedCornerShape(24.dp)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Alternança automàtica", style = Type.Large, modifier = Modifier.weight(1f))
                Switch(checked = config.enabled, onCheckedChange = { mode.update(config.copy(enabled = it)) },
                    modifier = Modifier.semantics { contentDescription = "Alternança automàtica" })
            }
            Text("Quan deixis de tocar el marc, mostrarà fotos a pantalla completa. Periòdicament tornarà a Avui i després reprendrà les fotos. Toca una foto per obrir Avui.", style = Type.Small)
            Text("S'espera mentre ets a Opcions, tens una cita o un diàleg obert, o estàs escrivint amb el teclat. Sense fotos disponibles es manté la informació, i durant l'horari nocturn s'atura l'alternança.", style = Type.Tiny)
        }
        Column(Modifier.fillMaxWidth().background(Palette.Card, RoundedCornerShape(24.dp)).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Començar les fotos després de", style = Type.Small)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FrameCycleConfig.IDLE_MINUTES.forEach { value ->
                    FilterChip(selected = config.idleMinutes == value,
                        onClick = { mode.update(config.copy(idleMinutes = value)) }, label = { Text("$value min sense tocar", style = Type.Tiny) })
                }
            }
            Text("Mostrar Avui cada", style = Type.Small)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FrameCycleConfig.PHOTO_MINUTES.forEach { value ->
                    FilterChip(selected = config.photoMinutes == value,
                        onClick = { mode.update(config.copy(photoMinutes = value)) }, label = { Text("$value min de fotos", style = Type.Tiny) })
                }
            }
            Text("Temps que es mostra Avui", style = Type.Small)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FrameCycleConfig.AGENDA_SECONDS.forEach { value ->
                    FilterChip(selected = config.agendaSeconds == value,
                        onClick = { mode.update(config.copy(agendaSeconds = value)) }, label = { Text("$value segons", style = Type.Tiny) })
                }
            }
            Text("Ordre de reproducció", style = Type.Small)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = !config.shufflePhotos,
                    onClick = { mode.update(config.copy(shufflePhotos = false)) },
                    label = { Text("Seguint l'ordre", style = Type.Tiny) }
                )
                FilterChip(
                    selected = config.shufflePhotos,
                    onClick = { mode.update(config.copy(shufflePhotos = true)) },
                    label = { Text("Aleatori", style = Type.Tiny) }
                )
            }
            Text("En mode aleatori, es mostren totes les fotos abans de repetir-ne cap.", style = Type.Tiny)
            Text("Canviar de fotografia cada", style = Type.Small)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FrameCycleConfig.SLIDE_SECONDS.forEach { value ->
                    FilterChip(selected = config.slideSeconds == value,
                        onClick = { mode.update(config.copy(slideSeconds = value)) }, label = { Text("$value segons", style = Type.Tiny) })
                }
            }
            Text("El canvi de fotografia utilitza les imatges desades al marc. La cerca de fotos noves manté l'interval de sincronització familiar.", style = Type.Tiny)
        }
    }
}

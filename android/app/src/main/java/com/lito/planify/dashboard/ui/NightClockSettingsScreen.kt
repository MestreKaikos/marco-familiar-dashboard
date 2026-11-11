package com.lito.planify.dashboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NightClockSettingsScreen(state: UiState, callbacks: SettingsCallbacks, settings: NightClockSettings, onBack: () -> Unit) {
    val c = settings.config
    var preview by remember { mutableStateOf(false) }
    var colorInput by remember(c.colorHex) { mutableStateOf(c.colorHex) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Mode nocturn", style = Type.Title, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBack) { Text("← Tornar a Opcions") }
        }
        Text("Fons negre i rellotge discret. Els canvis es desen automàticament al marc.", style = Type.Small)
        Box(Modifier.fillMaxWidth().height(240.dp).background(Color.Black, RoundedCornerShape(24.dp))) {
            NightClock(state.now, c, Modifier.fillMaxSize(), drift = false)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { preview = true }) { Text("Provar a pantalla completa", style = Type.Tiny.copy(color = Color.White)) }
            OutlinedButton(onClick = { settings.update(NightClockConfig()) }) { Text("Restablir aspecte", style = Type.Tiny) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NightPanel {
            NightChoices("Tipus de rellotge", c.layout, listOf("digital" to "Digital", "stacked" to "Apilat", "analog" to "Analògic")) { settings.update(c.copy(layout = it)) }
            if (c.layout != "analog") {
                NightChoices("Tipografia", c.font, listOf("modern" to "Moderna", "classic" to "Clàssica", "mono" to "Digital mono", "compact" to "Compacta")) { settings.update(c.copy(font = it)) }
            }
            NightChoices(if (c.layout == "analog") "Gruix de les busques" else "Gruix", c.weight,
                listOf("light" to "Fi", "normal" to "Normal", "bold" to "Negreta")) { settings.update(c.copy(weight = it)) }
            NightChoices("Color", c.colorHex, listOf("#B0B0B0" to "Gris", "#FFC078" to "Ambre", "#FF7B72" to "Vermell suau", "#90B8D8" to "Blau suau")) { settings.update(c.copy(colorHex = it)) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(value = colorInput, onValueChange = { colorInput = it.take(7) }, singleLine = true,
                    label = { Text("Color personalitzat · #RRGGBB") }, modifier = Modifier.weight(1f),
                    isError = NightClockConfig.normalizeColor(colorInput) == null)
                OutlinedButton(enabled = NightClockConfig.normalizeColor(colorInput) != null,
                    onClick = { settings.update(c.copy(colorHex = colorInput)) }) { Text("Aplicar color") }
            }
            NightSlider("Mida", c.scale, 0.35f..1f) { settings.update(c.copy(scale = it)) }
            NightSlider("Intensitat del color", c.intensity, 0.08f..1f) { settings.update(c.copy(intensity = it)) }
            NightSlider("Brillantor nocturna de pantalla", c.brightness, 0.01f..0.30f) { settings.update(c.copy(brightness = it)) }
            Text("La brillantor s'aplica al rellotge nocturn i a la prova a pantalla completa. La llum mínima depèn del panell del marc. Toca la prova per tornar als ajustos.", style = Type.Tiny)
        }
        NightPanel {
            ScheduleSection(state.schedule, callbacks.onSchedule)
            Text("Activa l'horari i «Rellotge nocturn». Amb «Rellotge nocturn» desactivat, es fa servir l'apagada real existent. Un toc al rellotge mostra Avui temporalment; al matí es recupera el funcionament habitual.", style = Type.Tiny)
        }
        }
    }
    if (preview) {
        Dialog(onDismissRequest = { preview = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val view = LocalView.current
            val window = (view.parent as? DialogWindowProvider)?.window
            SideEffect {
                window?.let {
                    it.attributes = it.attributes.apply { screenBrightness = c.brightness }
                    WindowCompat.setDecorFitsSystemWindows(it, false)
                    WindowInsetsControllerCompat(it, view).apply {
                        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
            NightScreen(state, c, onWake = { preview = false })
        }
    }
}

@Composable
private fun NightPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palette.Card, RoundedCornerShape(24.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NightChoices(label: String, selected: String, choices: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    Text(label, style = Type.Small)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        choices.forEach { (value, title) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF7857BC), selectedLabelColor = Color.White,
                    labelColor = Palette.Ink),
                label = { Text(title, style = Type.Tiny.copy(color = if (selected == value) Color.White else Palette.Ink)) })
        }
    }
}

@Composable
private fun NightSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Text("$label · ${(value * 100).roundToInt()}%", style = Type.Small)
    Slider(value = value, onValueChange = onChange, valueRange = range,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
}

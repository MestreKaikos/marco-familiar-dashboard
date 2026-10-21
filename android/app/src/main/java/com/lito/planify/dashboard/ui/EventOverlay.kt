package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.data.Event
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import java.time.LocalDate
import java.time.format.TextStyle

/** Abre el aviso de una cita; lo da [DashboardApp], que lo dibuja encima de todo ([EventOverlay]). */
val LocalShowEvent = staticCompositionLocalOf<(Event) -> Unit> { {} }

/**
 * Una cita al tocarla: abre el aviso con el título entero y sus datos. Envuelve la fila o tarjeta de la cita, que
 * así no tiene que saber nada del aviso.
 */
@Composable
fun EventTapTarget(event: Event, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val show = LocalShowEvent.current
    Box(modifier.clickable { show(event) }) { content() }
}

/**
 * La cita completa en un aviso: calendario (con su color), título sin cortar, día y horas, lugar y descripción (que
 * se desplaza si es larga). Se cierra tocando fuera o en «Cerrar». Se dibuja dentro de la pantalla y no en un Dialog:
 * una ventana aparte perdería la escala 1 dp = 1 px y el modo inmersivo del quiosco (salen las barras de Android).
 */
@Composable
fun EventOverlay(event: Event?, onDismiss: () -> Unit) {
    if (event == null) return
    val color = parseColor(event.calendar_color, Palette.Accent)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
    ) {
        Column(
            Modifier
                .width(1000.dp)
                // Tocar dentro del aviso no lo cierra
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .background(Palette.Card, RoundedCornerShape(36.dp))
                .padding(horizontal = 48.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(color, 18)
                Text(
                    event.calendar_name.orEmpty().uppercase(Spanish),
                    style = Type.Label.copy(color = color),
                    modifier = Modifier.padding(start = 14.dp)
                )
            }
            Text(event.title, style = Type.Title.copy(fontWeight = FontWeight.SemiBold))
            Text(whenLabel(event), style = Type.Body.copy(color = Palette.InkSoft))
            event.persons?.takeIf { it.isNotEmpty() }?.let { persons ->
                Text(persons.joinToString(", ") { it.name }, style = Type.Body.copy(color = Palette.InkSoft))
            }
            event.location?.takeIf { it.isNotBlank() }?.let { Detail("📍", it) }
            event.description?.takeIf { it.isNotBlank() }?.let { text ->
                Box(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { Detail("📝", text.trim()) }
            }
            Text(
                "Cerrar",
                style = Type.Body.copy(color = color, fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .align(Alignment.End)
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(28.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 36.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun Detail(icon: String, text: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(icon, style = Type.Body, modifier = Modifier.width(52.dp))
        Text(text, style = Type.Body.copy(color = Palette.Ink, lineHeight = 38.sp))
    }
}

/** «Miércoles 21 de octubre · 17:00–18:30»; los de día completo, «Todo el día» (y hasta qué día, si son varios). */
private fun whenLabel(event: Event): String {
    fun day(d: LocalDate) = "${d.weekdayName()} ${d.dayOfMonth} de ${d.month.getDisplayName(TextStyle.FULL, Spanish)}"
    val start = event.at.toLocalDate()
    if (event.all_day) {
        val last = event.end?.toLocalDate()?.minusDays(1)
        return if (last != null && last > start) "${day(start)} – ${day(last)} · Todo el día" else "${day(start)} · Todo el día"
    }
    return "${day(start)} · ${event.rangeLabel() ?: event.timeLabel()}"
}

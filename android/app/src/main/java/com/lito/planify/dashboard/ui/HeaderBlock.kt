package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.data.Forecast
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle

/**
 * Bloque ancho de la portada con la hora, la fecha y el tiempo juntos sobre un «cielo»: un degradado que sigue al sol
 * (amanecer, día, atardecer y noche, con la salida y la puesta de hoy).
 */
@Composable
fun HeaderBlock(state: UiState, modifier: Modifier) = SkyHeader(state, modifier)

/** Colores del cielo según la hora: noche, amanecer, día y atardecer (con la salida y la puesta del sol de hoy). */
private fun skyColors(f: Forecast?, now: LocalDateTime): Pair<List<Color>, Boolean> {
    val today = now.toLocalDate()
    val rise = f?.sunrise(today)?.let { LocalTime.parse(it) } ?: LocalTime.of(8, 0)
    val set = f?.sunset(today)?.let { LocalTime.parse(it) } ?: LocalTime.of(19, 0)
    val t = now.toLocalTime()
    return when {
        t.isBefore(rise.minusMinutes(45)) || t.isAfter(set.plusMinutes(60)) -> listOf(Color(0xFF1E2A4A), Color(0xFF3B3F78)) to true
        t.isBefore(rise.plusMinutes(60)) -> listOf(Color(0xFFF6B48F), Color(0xFFB7C9F2)) to false
        t.isBefore(set.minusMinutes(60)) -> listOf(Color(0xFF8EC5FC), Color(0xFFE0F0FF)) to false
        else -> listOf(Color(0xFFF08A5D), Color(0xFF8E5BC8)) to true
    }
}

@Composable
private fun SkyHeader(state: UiState, modifier: Modifier) {
    val now = state.now
    val (colors, dark) = skyColors(state.forecast, now)
    val ink = if (dark) Color.White else Palette.Ink
    val soft = if (dark) Color.White.copy(alpha = 0.8f) else Palette.InkSoft
    Row(
        modifier
            .background(Brush.linearGradient(colors), RoundedCornerShape(28.dp))
            .padding(horizontal = 36.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (state.clockValid) now.format(HourMinute) else "--:--",
                style = Type.Clock.copy(color = ink, fontSize = 150.sp, lineHeight = 150.sp, letterSpacing = (-6).sp, fontWeight = FontWeight.Normal),
                maxLines = 1
            )
            DateLine(now.toLocalDate(), ink, soft)
        }
        WeatherBlock(state, ink, soft, Modifier.weight(1.25f).fillMaxHeight(), chips = true, dark = dark)
    }
}

// --- Común ---

@Composable
private fun DateLine(today: LocalDate, ink: Color, soft: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(today.weekdayName(), style = Type.Large.copy(color = ink, fontWeight = FontWeight.SemiBold))
        Text(
            "  ${today.dayOfMonth} ${today.monthWithArticle()}",
            style = Type.Large.copy(color = soft)
        )
    }
}

/**
 * El tiempo: hoy (icono, temperatura, mín/máx y lluvia) y los 5 días siguientes con su icono, máx/mín y % de lluvia.
 * [chips]: cada día sobre una pastilla translúcida (estilo cielo).
 */
@Composable
private fun WeatherBlock(state: UiState, ink: Color, soft: Color, modifier: Modifier, chips: Boolean = false, dark: Boolean = false) {
    val f = state.forecast
    val today = state.now.toLocalDate()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (f == null) {
            Text("Tria la ubicació del temps a Opcions", style = Type.Small.copy(color = soft))
            return@Row
        }
        val d = f.dayIndex(today)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 18.dp)) {
            DayWeatherIcon(f, today, today, 80)
            Text(f.current.temperature_2m.degrees(), style = Type.Title.copy(color = ink, fontWeight = FontWeight.SemiBold))
            if (d >= 0) {
                Text("${f.daily.temperature_2m_min[d].degrees()} / ${f.daily.temperature_2m_max[d].degrees()}", style = Type.Tiny.copy(color = soft))
                Rain(f.daily.precipitation_probability_max[d], dark)
            }
        }
        (1..5).forEach { i ->
            val day = today.plusDays(i.toLong())
            val k = f.dayIndex(day)
            if (k < 0) return@forEach
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 3.dp)
                    .then(if (chips) Modifier.background(Color.White.copy(alpha = if (dark) 0.12f else 0.45f), RoundedCornerShape(18.dp)) else Modifier)
                    .padding(vertical = 8.dp)
            ) {
                Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Spanish).trimEnd('.').capitalized(), style = Type.Tiny.copy(color = soft, fontWeight = FontWeight.SemiBold))
                DayWeatherIcon(f, day, today, 44)
                Text(f.daily.temperature_2m_max[k].degrees(), style = Type.Small.copy(color = ink, fontWeight = FontWeight.SemiBold))
                Text(f.daily.temperature_2m_min[k].degrees(), style = Type.Tiny.copy(color = soft))
                Rain(f.daily.precipitation_probability_max[k], dark)
            }
        }
    }
}

/** Probabilidad de lluvia del día, en azul; gris si es baja. */
@Composable
private fun Rain(pct: Int?, dark: Boolean) {
    val p = pct ?: 0
    val color = when {
        p >= 40 -> if (dark) Color(0xFF9CC3FF) else Palette.Rain
        else -> if (dark) Color.White.copy(alpha = 0.6f) else Palette.InkMuted
    }
    Text("💧 $p %", style = Type.Tiny.copy(color = color, fontSize = 15.sp, fontWeight = if (p >= 40) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1)
}

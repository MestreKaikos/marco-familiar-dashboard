package com.lito.planify.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Duration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * El tiempo a pantalla completa, en dos páginas que se deslizan: esta semana (24 horas, de 4 antes de la
 * actual a 19 después, con iconos, y 7 días) y la siguiente (gráfica diaria de los días 8 a 14).
 */
@Composable
fun WeatherScreen(state: UiState) {
    val forecast = state.forecast
    val location = state.location
    ScreenPadding {
        if (forecast == null || location == null) {
            Text(if (location == null) "Tria una ubicació a Opcions" else "Carregant la previsió…", style = Type.Large.copy(color = Palette.InkMuted))
            return@ScreenPadding
        }
        val pager = rememberPagerState { 3 }
        val scope = rememberCoroutineScope()
        val today = state.now.toLocalDate()
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), pageSpacing = 40.dp) { page ->
                if (page == 0) {
                    // Al pasar del último día de la gráfica sigue la semana siguiente, y antes de la semana vuelve al día
                    WeatherCard(forecast, location.name, state.now, days = 7, modifier = Modifier.fillMaxSize()) { edge ->
                        if (edge > 0) scope.launch { pager.animateScrollToPage(1) }
                    }
                } else if (page == 1) {
                    WeekWeatherCard(forecast, location.name, "La setmana vinent", today.plusDays(7), state.now, Modifier.fillMaxSize()) { edge ->
                        if (edge < 0) scope.launch { pager.animateScrollToPage(0) }
                    }
                } else {
                    AstronomyCard(forecast, location.name, today, Modifier.fillMaxSize())
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            ) {
                repeat(3) { Dot(if (pager.currentPage == it) Palette.Ink else Palette.Line, 14) }
            }
        }
    }
}

/**
 * Tercera pàgina d'El temps: dades del sol per als propers dies i fases lunars.
 * Manté intactes els gràfics de les dues pàgines meteorològiques existents.
 */
@Composable
private fun AstronomyCard(
    forecast: com.lito.planify.dashboard.data.Forecast,
    location: String,
    today: LocalDate,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.background(Palette.Card, RoundedCornerShape(32.dp))
            .padding(horizontal = 36.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Sol i lluna", style = Type.Title.copy(fontWeight = FontWeight.SemiBold))
        Text(location, style = Type.Body.copy(color = Palette.InkMuted))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            AstronomyPanel("☀  Sol", "Avui", Modifier.weight(1f)) {
                Text("Sortida  ${forecast.sunrise(today) ?: "—"}", style = Type.Large)
                Text("Posta  ${forecast.sunset(today) ?: "—"}", style = Type.Large)
                val sunrise = forecast.sunrise(today)
                val sunset = forecast.sunset(today)
                if (sunrise != null && sunset != null) {
                    val minutes = Duration.between(
                        java.time.LocalTime.parse(sunrise),
                        java.time.LocalTime.parse(sunset)
                    ).toMinutes()
                    if (minutes >= 0) Text(
                        "Hores de llum  ${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')} min",
                        style = Type.Body.copy(color = Palette.InkSoft)
                    )
                }
            }
            AstronomyPanel("☾  Lluna", "Fase aproximada", Modifier.weight(1f)) {
                Text(moonPhaseLabel(today), style = Type.Large)
                Text("La fase és una estimació astronòmica. Les hores de sortida i posta de la lluna encara no estan disponibles.",
                    style = Type.Small.copy(color = Palette.InkMuted))
            }
        }
        Text("Propers dies", style = Type.Large.copy(fontWeight = FontWeight.SemiBold))
        (0 until 7).forEach { offset ->
            val date = today.plusDays(offset.toLong())
            if (forecast.dayIndex(date) >= 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (offset == 0) "Avui" else date.dayOfWeek.getDisplayName(
                        java.time.format.TextStyle.FULL, Catalan
                    ).replaceFirstChar { it.uppercaseChar() }, style = Type.Body, modifier = Modifier.weight(1f))
                    Text("☀  ${forecast.sunrise(date) ?: "—"}  ·  ${forecast.sunset(date) ?: "—"}",
                        style = Type.Body, modifier = Modifier.weight(1.5f))
                    Text("☾  ${moonPhaseLabel(date)}", style = Type.Body, modifier = Modifier.weight(1.2f))
                }
                Hairline()
            }
        }
    }
}

@Composable
private fun AstronomyPanel(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(modifier.background(Palette.Highlight, RoundedCornerShape(22.dp))
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = Type.Large.copy(fontWeight = FontWeight.SemiBold))
        Text(subtitle, style = Type.Small.copy(color = Palette.InkMuted))
        content()
    }
}

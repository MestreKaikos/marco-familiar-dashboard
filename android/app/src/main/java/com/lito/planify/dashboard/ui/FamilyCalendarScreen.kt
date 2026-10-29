package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import java.time.DayOfWeek
import java.time.LocalDate

/** Semanas que enseña el Calendario de la familia: esta y las 12 siguientes, unos 3 meses (l'agenda familiar). */
private const val WEEKS_SHOWN = 13

/**
 * Calendario de la familia, como uno de pared: las semanas de lunes a domingo una debajo de otra, sin títulos
 * (desplazamiento vertical), empezando por la actual; caben dos a la vista. Cada día es como en la portada (turno de
 * la persona con turnos en su pastilla, cites i recordatoris amb data límit, amb lletra una mica més petita) y además lleva el tiempo de ese día mientras haya
 * previsión (14 días).
 */
@Composable
fun FamilyCalendarScreen(state: UiState, reminders: FamilyReminders, onOpenReminders: () -> Unit) {
    val now = state.now
    val today = now.toLocalDate()
    val thisMonday = today.with(DayOfWeek.MONDAY)
    val events = state.content?.events.orEmpty()
    val shifts = state.content?.sessions.orEmpty()
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp)
    ) {
        if (state.personalCalendars.isNotEmpty()) {
            item {
                CalendarLegend(state.personalCalendars)
            }
        }
        if (!state.ics && state.token == null) {
            item {
                Text(
                    "Encara no hem connectat els calendaris familiars. Els afegirem sense contrasenyes de Planify.",
                    style = Type.Small.copy(color = Palette.Ink),
                    modifier = Modifier.fillMaxWidth()
                        .background(Palette.Card, RoundedCornerShape(18.dp))
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                )
            }
        }
        items(WEEKS_SHOWN) { w ->
            val monday = thisMonday.plusWeeks(w.toLong())
            Row(Modifier.fillMaxWidth().height(568.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                (0 until 7).forEach { i ->
                    val day: LocalDate = monday.plusDays(i.toLong())
                    WeekDay(
                        day, today, now,
                        events.filter { it.at.toLocalDate() == day },
                        shifts.filter { it.at.toLocalDate() == day },
                        reminders.dueOn(day).take(2).joinToString(" · ") { it.title }.ifBlank { null },
                        Modifier.weight(1f).fillMaxHeight(),
                        onDinnerClick = onOpenReminders,
                        forecast = state.forecast,
                        small = true,
                        footerTitle = "📌 RECORDATORIS"
                    )
                }
            }
        }
    }
}

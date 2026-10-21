package com.lito.planify.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.UiState
import java.time.DayOfWeek
import java.time.LocalDate

/** Semanas que enseña el Calendario de la familia: esta y las 12 siguientes, unos 3 meses (lo que traen turnos y menús). */
private const val WEEKS_SHOWN = 13

/**
 * Calendario de la familia, como uno de pared: las semanas de lunes a domingo una debajo de otra, sin títulos
 * (desplazamiento vertical), empezando por la actual; caben dos a la vista. Cada día es como en la portada (turno de
 * la persona con turnos en su pastilla, citas y menú del cole, con letra algo menor) y además lleva el tiempo de ese día mientras haya
 * previsión (14 días).
 */
@Composable
fun FamilyCalendarScreen(state: UiState) {
    val now = state.now
    val today = now.toLocalDate()
    val thisMonday = today.with(DayOfWeek.MONDAY)
    val events = state.content?.events.orEmpty()
    val shifts = state.content?.sessions.orEmpty()
    val menu = state.content?.menu.orEmpty()
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp)
    ) {
        items(WEEKS_SHOWN) { w ->
            val monday = thisMonday.plusWeeks(w.toLong())
            Row(Modifier.fillMaxWidth().height(568.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                (0 until 7).forEach { i ->
                    val day: LocalDate = monday.plusDays(i.toLong())
                    WeekDay(
                        day, today, now,
                        events.filter { it.at.toLocalDate() == day },
                        shifts.filter { it.at.toLocalDate() == day },
                        menu.firstOrNull { it.at.toLocalDate() == day },
                        Modifier.weight(1f).fillMaxHeight(),
                        forecast = state.forecast,
                        small = true
                    )
                }
            }
        }
    }
}

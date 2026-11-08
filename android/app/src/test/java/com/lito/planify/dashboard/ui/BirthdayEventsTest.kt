package com.lito.planify.dashboard.ui

import com.lito.planify.dashboard.data.Event
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BirthdayEventsTest {
    private val today = LocalDate.of(2026, 11, 8)

    private fun e(id: Int, title: String, day: String = "2026-11-08", calendar: Int = 1) =
        Event(
            id = id, calendar_id = calendar, calendar_name = "Família",
            calendar_color = "#ABCDEF", title = title,
            date_at = "$day 00:00:00", all_day = true,
            end_at = "${LocalDate.parse(day).plusDays(1)} 00:00:00"
        )

    @Test fun understandsCatalanTitlesAndExtractsPerson() {
        assertEquals("Marc", birthdayPerson("Aniversari de Marc"))
        assertEquals("Anna", birthdayPerson("Aniversari d'Anna"))
        assertEquals("Èlia", birthdayPerson("ANIVERSARI D’ÈLIA"))
        assertEquals("Rai Busquets", birthdayPerson(" aniversari de Rai Busquets "))
        assertNull(birthdayPerson("Aniversari de"))
        assertNull(birthdayPerson("Aniversari"))
    }

    @Test fun doesNotHideUnrelatedAllDayEvents() {
        assertFalse(isBirthdayEvent(e(1, "Dia festiu")))
        assertFalse(isBirthdayEvent(e(2, "Reunió de família")))
        assertFalse(isBirthdayEvent(e(3, "Aniversari de l'empresa: 10 anys")))
        assertTrue(isBirthdayEvent(e(4, "Aniversari de Marc")))
    }

    @Test fun showsOnlyBirthdaysTodayAndKeepsAllPeople() {
        val list = listOf(
            e(1, "Aniversari de Marc"),
            e(2, "Aniversari d'Anna"),
            e(3, "Aniversari de Joan", "2026-11-09"),
            e(4, "Visita al metge"),
            e(5, "ANIVERSARI DE MARC", calendar = 3)
        )
        assertEquals(listOf("Marc", "Anna"), birthdaysToday(list, today))
        assertEquals(listOf("Joan"), birthdaysToday(list, today.plusDays(1)))
    }

    @Test fun nextCalendarEventsKeepsRegularDatesAndRemovesBirthdayTitles() {
        val now = LocalDateTime.of(2026, 11, 8, 9, 0)
        val list = listOf(
            e(1, "Aniversari de Marc"),
            e(2, "Aniversari de Joan", "2026-11-09"),
            e(3, "Dia festiu"),
            e(4, "Visita al metge", "2026-11-09")
        )
        assertEquals(listOf(3, 4), nextCalendarEvents(list, now).map { it.id })
        assertTrue(nextCalendarEvents(listOf(e(1, "Aniversari de Marc")), now).isEmpty())
    }
}

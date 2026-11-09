package com.lito.planify.dashboard.ui

import com.lito.planify.dashboard.data.Event
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpcomingAgendaTest {
    private val now = LocalDateTime.of(2026, 11, 8, 17, 0)

    private fun event(
        id: Int, date: String, allDay: Boolean = false, end: String? = null
    ) = Event(
        id = id, calendar_id = 1, calendar_name = "Família",
        calendar_color = "#224488", title = "Cita $id", date_at = date,
        all_day = allDay, end_at = end
    )

    @Test fun showsAllUpcomingEventsOnSameDay() {
        val events = listOf(
            event(1, "2026-11-08 18:00:00"),
            event(2, "2026-11-08 19:30:00"),
            event(3, "2026-11-08 21:00:00")
        )
        assertEquals(listOf(1, 2, 3), nextCalendarEvents(events, now).map { it.id })
    }

    @Test fun includesAllDayTodayAndEventsTomorrow() {
        val events = listOf(
            event(1, "2026-11-08 00:00:00", allDay = true, end = "2026-11-09 00:00:00"),
            event(2, "2026-11-09 10:00:00")
        )
        assertEquals(listOf(1, 2), nextCalendarEvents(events, now).map { it.id })
    }

    @Test fun preservesOngoingAndExcludesFinishedOrBeyondWeek() {
        val events = listOf(
            event(1, "2026-11-08 15:00:00", end = "2026-11-08 18:00:00"),
            event(2, "2026-11-08 12:00:00", end = "2026-11-08 13:00:00"),
            event(3, "2026-11-15 09:00:00"),
            event(4, "2026-11-08 19:00:00")
        )
        assertEquals(listOf(1, 4), nextCalendarEvents(events, now).map { it.id })
    }

    @Test fun duplicatesAreNotShownTwice() {
        val a = event(1, "2026-11-08 18:00:00")
        assertEquals(1, nextCalendarEvents(listOf(a, a), now).size)
    }

    @Test fun includesMultiDayEventAlreadyStarted() {
        val event = event(4, "2026-11-07 00:00:00", allDay = true,
            end = "2026-11-10 00:00:00")
        assertTrue(nextCalendarEvents(listOf(event), now).contains(event))
    }
    @Test fun nextDayIncludesOnlyEventsOfNearestDate() {
        val events = listOf(
            event(1, "2026-11-09 18:00:00"),
            event(2, "2026-11-09 09:00:00"),
            event(3, "2026-11-10 10:00:00"),
            event(4, "2026-11-12 20:00:00")
        )
        assertEquals(listOf(2, 1), nextCalendarDayEvents(events, now).map { it.id })
    }

    @Test fun nextDayIncludesAllRemainingActivitiesOfToday() {
        val events = listOf(
            event(1, "2026-11-08 15:00:00", end = "2026-11-08 18:00:00"),
            event(2, "2026-11-08 19:30:00"),
            event(3, "2026-11-09 09:00:00")
        )
        assertEquals(listOf(1, 2), nextCalendarDayEvents(events, now).map { it.id })
    }

    @Test fun nextDayFindsFirstActivityBeyondSevenDays() {
        val event = event(1, "2026-12-02 11:30:00")
        assertEquals(listOf(1), nextCalendarDayEvents(listOf(event), now).map { it.id })
    }

    @Test fun nextDayExcludesBirthdaysAndCompletedEvents() {
        val birthday = event(1, "2026-11-09 00:00:00", allDay = true,
            end = "2026-11-10 00:00:00").copy(title = "Aniversari de Gemma")
        val finished = event(2, "2026-11-08 10:00:00", end = "2026-11-08 11:00:00")
        val next = event(3, "2026-11-11 18:00:00")
        assertEquals(listOf(3), nextCalendarDayEvents(listOf(birthday, finished, next), now).map { it.id })
    }

    @Test fun nextDayDeduplicatesAndIncludesMultiDayOngoing() {
        val continuing = event(1, "2026-11-07 00:00:00", allDay = true,
            end = "2026-11-10 00:00:00")
        val later = event(2, "2026-11-09 09:00:00")
        assertEquals(listOf(1), nextCalendarDayEvents(listOf(continuing, continuing, later), now).map { it.id })
    }

}

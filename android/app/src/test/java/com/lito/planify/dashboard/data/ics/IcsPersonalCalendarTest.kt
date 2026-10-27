package com.lito.planify.dashboard.data.ics

import com.lito.planify.dashboard.data.PersonalCalendar
import com.lito.planify.dashboard.data.CalendarColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class IcsPersonalCalendarTest {
    @Test
    fun localSourcesEnableCalendarWithoutBuildTimeCredentials() {
        val config = IcsConfig(Properties())
        assertFalse(config.enabled)

        config.setPersonalCalendars(
            listOf(
                PersonalCalendar("1", "Família", "https://calendar.google.com/calendar/ical/a/private-1/basic.ics"),
                PersonalCalendar("2", "Rai", "https://calendar.google.com/calendar/ical/b/private-2/basic.ics")
            )
        )

        assertTrue(config.enabled)
        assertEquals(listOf("Família", "Rai"), config.calendars.map { it.name })
        assertEquals(listOf(CalendarRole.EVENTS, CalendarRole.EVENTS), config.calendars.map { it.role })
        assertEquals(2, config.calendars.map { it.color }.distinct().size)

        config.setPersonalCalendars(emptyList())
        assertFalse(config.enabled)
        assertTrue(config.calendars.isEmpty())
    }

    @Test
    fun deletingCalendarDoesNotChangeColorsOfRemainingCalendars() {
        val initial = CalendarColors.normalize(listOf(
            PersonalCalendar("one", "Família", "https://example.com/a"),
            PersonalCalendar("two", "Rai", "https://example.com/b"),
            PersonalCalendar("three", "Personal", "https://example.com/c")
        ))
        val remaining = CalendarColors.normalize(initial.filterNot { it.id == "one" })
        assertEquals(initial[1].color, remaining[0].color)
        assertEquals(initial[2].color, remaining[1].color)

        val edited = remaining.map { if (it.id == "two") it.copy(color = "#7955B3") else it }
        val config = IcsConfig(Properties())
        config.setPersonalCalendars(edited)
        assertEquals("#7955B3", config.calendars.first().color)
        assertEquals(remaining[1].color, config.calendars.last().color)
    }

        @Test
    fun noMoreThanFiveLocalCalendars() {
        val config = IcsConfig(Properties())
        config.setPersonalCalendars((1..7).map {
            PersonalCalendar(it.toString(), "Calendar $it", "https://calendar.google.com/calendar/ical/$it/basic.ics")
        })
        assertEquals(5, config.calendars.size)
    }
}

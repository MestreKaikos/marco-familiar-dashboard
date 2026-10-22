package com.lito.planify.dashboard.data.ics

import com.lito.planify.dashboard.data.PersonalCalendar
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
    fun noMoreThanFiveLocalCalendars() {
        val config = IcsConfig(Properties())
        config.setPersonalCalendars((1..7).map {
            PersonalCalendar(it.toString(), "Calendar $it", "https://calendar.google.com/calendar/ical/$it/basic.ics")
        })
        assertEquals(5, config.calendars.size)
    }
}

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
    fun calendarKeepsColorWhenAnotherCalendarIsRemovedOrReordered() {
        val config = IcsConfig(Properties())
        val family = PersonalCalendar("1", "Família", "https://example.org/family.ics", "#36A070")
        val rai = PersonalCalendar("2", "Rai", "https://example.org/rai.ics", "#3977CB")
        val personal = PersonalCalendar("3", "Personal", "https://example.org/personal.ics", "#BF5960")

        config.setPersonalCalendars(listOf(family, rai, personal))
        assertEquals(listOf("#36A070", "#3977CB", "#BF5960"), config.calendars.map { it.color })

        config.setPersonalCalendars(listOf(personal, rai))
        assertEquals(listOf("#BF5960", "#3977CB"), config.calendars.map { it.color })
    }

    @Test
    fun changingOneCalendarColorDoesNotChangeOtherColors() {
        val config = IcsConfig(Properties())
        val family = PersonalCalendar("1", "Família", "https://example.org/family.ics", "#3977CB")
        val rai = PersonalCalendar("2", "Rai", "https://example.org/rai.ics", "#36A070")
        config.setPersonalCalendars(listOf(family, rai.copy(color = "#E09132")))
        assertEquals(listOf("#3977CB", "#E09132"), config.calendars.map { it.color })
    }

    @Test
    fun migrationPreservesPreviousOrderColorsOnlyOnce() {
        val old = listOf(
            PersonalCalendar("1", "Família", "https://example.org/family.ics"),
            PersonalCalendar("2", "Rai", "https://example.org/rai.ics"),
            PersonalCalendar("3", "Personal", "https://example.org/personal.ics")
        )
        val migrated = CalendarColors.migrate(old)
        assertEquals(listOf("#3977CB", "#B965AF", "#36A070"), migrated.map { it.color })
        assertEquals(old.map { it.url }, migrated.map { it.url })
        assertEquals(migrated, CalendarColors.migrate(migrated))
        assertEquals(listOf("#36A070", "#B965AF"), CalendarColors.migrate(listOf(migrated[2], migrated[1])).map { it.color })
    }

    @Test
    fun newCalendarGetsFirstUnusedColorAndInvalidColorIsRejected() {
        val one = PersonalCalendar("1", "Família", "https://example.org/family.ics", "#3977CB")
        assertEquals("#B965AF", CalendarColors.nextAvailable(listOf(one)))
        assertEquals("#B965AF", CalendarColors.normalize("#b965af"))
        assertEquals(null, CalendarColors.normalize("red; injected"))
        assertEquals(null, CalendarColors.normalize("#FFFF"))
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

package com.lito.planify.dashboard.data.ics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Properties

class IcsSourceTest {
    private val zone = ZoneId.of("Europe/Madrid")

    private fun config() = IcsConfig(Properties().apply {
        setProperty("calendar.familia.name", "Familia")
        setProperty("calendar.familia.color", "#E8604C")
        setProperty("calendar.familia.role", "events")
        setProperty("calendar.familia.url", "familia")
        setProperty("calendar.turnos.name", "Turnos")
        setProperty("calendar.turnos.color", "#8882BE")
        setProperty("calendar.turnos.role", "shifts")
        setProperty("calendar.turnos.url", "turnos")
        setProperty("shift.color.Tarde", "#7BCDC8")
        setProperty("shift.color.Día_libre", "#C4DF9B")
    })

    private fun source(files: Map<String, String>) = IcsSource(config(), zone, download = { files.getValue(it) })

    private fun ics(vararg events: String) = (listOf(
        "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Google Inc//Google Calendar 70.9054//EN",
        "BEGIN:VTIMEZONE", "TZID:Europe/Madrid",
        "BEGIN:DAYLIGHT", "TZOFFSETFROM:+0100", "TZOFFSETTO:+0200", "TZNAME:CEST", "DTSTART:19700329T020000",
        "RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU", "END:DAYLIGHT",
        "BEGIN:STANDARD", "TZOFFSETFROM:+0200", "TZOFFSETTO:+0100", "TZNAME:CET", "DTSTART:19701025T030000",
        "RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU", "END:STANDARD",
        "END:VTIMEZONE"
    ) + events.toList() + "END:VCALENDAR").joinToString("\r\n")

    private val weekly = """
        BEGIN:VEVENT
        DTSTART;TZID=Europe/Madrid:20261005T173000
        DTEND;TZID=Europe/Madrid:20261005T183000
        RRULE:FREQ=WEEKLY;BYDAY=MO,WE
        EXDATE;TZID=Europe/Madrid:20261014T173000
        UID:natacion@google.com
        SUMMARY:Natación
        END:VEVENT
        BEGIN:VEVENT
        DTSTART;TZID=Europe/Madrid:20261019T180000
        DTEND;TZID=Europe/Madrid:20261019T190000
        RECURRENCE-ID;TZID=Europe/Madrid:20261019T173000
        UID:natacion@google.com
        SUMMARY:Natación (cambio de hora)
        END:VEVENT
    """.trimIndent()

    private val birthday = """
        BEGIN:VEVENT
        DTSTART;VALUE=DATE:20200314
        DTEND;VALUE=DATE:20200315
        RRULE:FREQ=YEARLY
        UID:cumple@google.com
        SUMMARY:Cumple Laura
        END:VEVENT
    """.trimIndent()

    private val utcEvent = """
        BEGIN:VEVENT
        DTSTART:20261024T200000Z
        DTEND:20261025T070000Z
        UID:noche@google.com
        SUMMARY:Tarde
        END:VEVENT
        BEGIN:VEVENT
        DTSTART;VALUE=DATE:20261026
        DTEND;VALUE=DATE:20261027
        UID:libre@google.com
        SUMMARY:Día libre
        END:VEVENT
        BEGIN:VEVENT
        DTSTART:20261027T130000Z
        DTEND:20261027T200000Z
        STATUS:CANCELLED
        UID:cancelado@google.com
        SUMMARY:Tarde
        END:VEVENT
    """.trimIndent()

    @Test
    fun weeklyRecurrenceWithExceptionAndOverride() = runBlocking {
        val events = source(mapOf("familia" to ics(weekly), "turnos" to ics())).events(
            LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 26), CalendarRole.EVENTS
        )
        assertEquals(
            listOf(
                "2026-10-05 17:30:00 Natación", "2026-10-07 17:30:00 Natación", "2026-10-12 17:30:00 Natación",
                // el 14 está excluido (EXDATE) y el 19 cambia de hora (RECURRENCE-ID)
                "2026-10-19 18:00:00 Natación (cambio de hora)", "2026-10-21 17:30:00 Natación"
            ),
            events.map { "${it.date_at} ${it.title}" }
        )
        assertEquals("2026-10-05 18:30:00", events.first().end_at)
        assertEquals("#E8604C", events.first().calendar_color)
        assertTrue(events.all { it.routine_id == null && !it.all_day })
    }

    @Test
    fun recurrenceKeepsLocalTimeAcrossDaylightSavingChange() = runBlocking {
        val events = source(mapOf("familia" to ics(weekly), "turnos" to ics())).events(
            LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 27), CalendarRole.EVENTS
        )
        assertEquals(listOf("2026-10-26 17:30:00"), events.map { it.date_at })
    }

    @Test
    fun yearlyAllDayBirthday() = runBlocking {
        val events = source(mapOf("familia" to ics(birthday), "turnos" to ics())).events(
            LocalDate.of(2026, 1, 1), LocalDate.of(2028, 1, 1), CalendarRole.EVENTS
        )
        assertEquals(listOf("2026-03-14 00:00:00", "2027-03-14 00:00:00"), events.map { it.date_at })
        assertTrue(events.all { it.all_day })
        assertEquals("2026-03-15 00:00:00", events.first().end_at)
    }

    @Test
    fun shiftsUseTypeColorRoutineIdLocalTimeAndSkipCancelled() = runBlocking {
        val shifts = source(mapOf("familia" to ics(), "turnos" to ics(utcEvent))).events(
            LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 31), CalendarRole.SHIFTS
        )
        assertEquals(listOf("2026-10-24 22:00:00 Tarde", "2026-10-26 00:00:00 Día libre"), shifts.map { "${it.date_at} ${it.title}" })
        // 20:00Z del 24 (CEST) → 07:00Z del 25 (ya en CET) = 22:00 → 08:00
        assertEquals("2026-10-25 08:00:00", shifts[0].end_at)
        assertEquals("#7BCDC8", shifts[0].calendar_color)
        assertEquals("#C4DF9B", shifts[1].calendar_color)
        assertTrue(shifts[1].all_day)
        assertTrue(shifts.all { it.routine_id != null })
    }

    @Test
    fun rolesAreSeparatedAndDownloadsShared() = runBlocking {
        var downloads = 0
        val source = IcsSource(config(), zone, download = { downloads++; if (it == "familia") ics(weekly) else ics(utcEvent) })
        val from = LocalDate.of(2026, 10, 1)
        val to = LocalDate.of(2026, 11, 1)
        assertFalse(source.events(from, to, CalendarRole.EVENTS).any { it.routine_id != null })
        assertTrue(source.events(from, to, CalendarRole.SHIFTS).all { it.routine_id != null })
        source.events(from, to, CalendarRole.EVENTS, CalendarRole.SHIFTS)
        assertEquals(2, downloads)
        source.invalidate()
        source.events(from, to, CalendarRole.EVENTS)
        assertEquals(3, downloads)
    }

    @Test
    fun isPastForAllDayEventsEndsWithTheDay() {
        val e = com.lito.planify.dashboard.data.Event(1, 1, null, null, "Cumple", "2026-10-20 00:00:00", all_day = true, end_at = "2026-10-21 00:00:00")
        assertFalse(e.isPast(java.time.LocalDateTime.of(2026, 10, 20, 18, 0)))
        assertTrue(e.isPast(java.time.LocalDateTime.of(2026, 10, 21, 0, 0)))
    }
}

package com.lito.planify.dashboard.ui

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SummaryReminderDateTest {
    private val today = LocalDate.of(2026, 11, 8)

    @Test fun showsDateForTodayInMutedSummaryText() {
        val label = summaryReminderDate(FamilyReminder("1", "Trucar", today.toString()), today)
        assertTrue(label.startsWith("Avui · 8 "))
    }

    @Test fun showsTomorrowAndUpcomingDates() {
        assertTrue(summaryReminderDate(FamilyReminder("2", "Comprar", "2026-11-09"), today)
            .startsWith("Demà · 9 "))
        assertTrue(summaryReminderDate(FamilyReminder("3", "Renovar", "2026-11-15"), today)
            .startsWith("15 "))
    }

    @Test fun showsOverdueAndUnscheduledReminders() {
        assertTrue(summaryReminderDate(FamilyReminder("4", "Pendent", "2026-11-07"), today)
            .endsWith(" · vençut"))
        assertEquals("Sense data", summaryReminderDate(FamilyReminder("5", "Una idea"), today))
    }
}

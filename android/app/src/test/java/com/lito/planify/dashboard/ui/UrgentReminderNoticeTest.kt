package com.lito.planify.dashboard.ui

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class UrgentReminderNoticeTest {
    private val today = LocalDate.of(2026, 11, 9)

    @Test fun overdueRemindersComeBeforeTodaysAndShowExtraCount() {
        val reminders = listOf(
            FamilyReminder("today", "Avui", "2026-11-09"),
            FamilyReminder("yesterday", "Ahir", "2026-11-08"),
            FamilyReminder("older", "Primer vençut", "2026-11-01")
        )
        assertEquals(UrgentReminderNotice("Primer vençut", true, 2), urgentReminderNotice(reminders, today))
    }

    @Test fun todaysReminderAppearsWhenThereAreNoOverdueReminders() {
        val reminders = listOf(FamilyReminder("a", "Pagar assegurança", "2026-11-09"))
        assertEquals(UrgentReminderNotice("Pagar assegurança", false, 0), urgentReminderNotice(reminders, today))
    }

    @Test fun futureUndatedAndCompletedRemindersDoNotTriggerAlerts() {
        val reminders = listOf(
            FamilyReminder("a", "Ja pagat", "2026-11-01", done = true),
            FamilyReminder("b", "Demà", "2026-11-10"),
            FamilyReminder("c", "Sense data")
        )
        assertNull(urgentReminderNotice(reminders, today))
        assertNull(urgentReminderNotice(emptyList(), today))
    }

    @Test fun markingReminderAsDoneRemovesItFromTheAlert() {
        val due = FamilyReminder("a", "Urgent", "2026-11-08")
        assertNotNull(urgentReminderNotice(listOf(due), today))
        assertNull(urgentReminderNotice(listOf(due.copy(done = true)), today))
    }
}

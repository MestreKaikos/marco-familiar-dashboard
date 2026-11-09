package com.lito.planify.dashboard.ui

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChoreCompletionTest {
    private val today = LocalDate.of(2026, 11, 9)
    private val yesterday = today.minusDays(1)
    private fun chore(
        frequency: String = "daily",
        date: String = today.toString(),
        state: String = "approved",
        days: String = "1,3",
        slots: String = "matí,vespre",
        doneSlots: Set<String> = emptySet()
    ) = LocalChore("llamp", "Treure en Llamp", "Rai",
        stars = 5, daily = frequency == "daily",
        date = date, status = state, frequency = frequency,
        days = days, slots = slots, completedSlots = doneSlots)

    @Test fun approvedDailyChoreIsOnlyAvailableAgainTomorrow() {
        val task = chore()
        assertEquals("approved", choreStatusOnDate(task, today))
        assertEquals("todo", choreStatusOnDate(task, today.plusDays(1)))
    }

    @Test fun pendingChoreCannotBeSubmittedTwiceWhileWaitingForValidation() {
        assertEquals("pending", choreStatusOnDate(chore(state = "pending"), today))
    }

    @Test fun rejectedDailyChoreCanBeResubmitted() {
        assertEquals("todo", choreStatusOnDate(chore(state = "todo",date = ""), today))
    }

    @Test fun onceChoreStaysDoneForeverAfterApproval() {
        val task = chore(frequency = "once", date = "2026-10-22")
        assertEquals("approved", choreStatusOnDate(task, today))
        assertEquals("approved", choreStatusOnDate(task, today.plusMonths(1)))
    }

    @Test fun onceChoreWaitingForApprovalDoesNotReopenAtMidnight() {
        assertEquals("pending", choreStatusOnDate(
            chore(frequency = "once", state = "pending", date = yesterday.toString()), today))
    }

    @Test fun weekdaysAndWeeklyRespectDaysAndApprovals() {
        val weekly = chore(frequency = "weekly", days = "1,4")
        assertEquals("approved", choreStatusOnDate(weekly, today))
        assertEquals("not_due", choreStatusOnDate(weekly, today.plusDays(1)))
        assertEquals("todo", choreStatusOnDate(weekly, today.plusDays(3)))
        val weekdays = chore(frequency = "weekdays", days = "1,2,3,4,5")
        assertEquals("approved", choreStatusOnDate(weekdays, today))
        assertEquals("todo", choreStatusOnDate(weekdays, today.plusDays(1)))
        assertEquals("not_due", choreStatusOnDate(weekdays, today.plusDays(5)))
    }

    @Test fun slotsOnlyShowDoneWhenAllTodaysSlotsAreCompleted() {
        val partial = chore(frequency = "slots", doneSlots = setOf("matí"))
        val complete = chore(frequency = "slots", doneSlots = setOf("matí", "vespre"))
        assertEquals("slots", choreStatusOnDate(partial, today))
        assertEquals("approved", choreStatusOnDate(complete, today))
        assertEquals("slots", choreStatusOnDate(complete, today.plusDays(1)))
    }

    @Test fun oldCloudApprovalNeverReopensTodayPendingChore() {
        val local = chore(state = "pending")
        val cloud = mapOf(yesterday.toString() to setOf("única"))
        val result = reconcileChoreState(cloud, local, "daily", today)
        assertEquals(today.toString(), result.date)
        assertEquals("pending", result.status)
    }

    @Test fun oldCloudApprovalNeverReopensTodayApprovedChore() {
        val local = chore(state = "approved")
        val cloud = mapOf(yesterday.toString() to setOf("única"))
        val result = reconcileChoreState(cloud, local, "daily", today)
        assertEquals(today.toString(), result.date)
        assertEquals("approved", result.status)
        assertEquals("approved", choreStatusOnDate(local.copy(date=result.date,status=result.status),today))
    }

    @Test fun currentCloudApprovalBeatsLocalPendingState() {
        val local = chore(state = "pending")
        val result = reconcileChoreState(
            mapOf(today.toString() to setOf("única")), local, "daily", today)
        assertEquals("approved", result.status)
        assertEquals(today.toString(), result.date)
    }

    @Test fun completedOnceChoreSurvivesSnapshotWithoutHistory() {
        val local = chore(frequency = "once", date = yesterday.toString(), state = "approved")
        val merged = reconcileChoreState(emptyMap(), local, "once", today)
        assertEquals("approved", merged.status)
        assertEquals("approved", choreStatusOnDate(local.copy(status = merged.status), today))
    }

    @Test fun twoCompletedSlotsAreMergedBeforeStatusIsCalculated() {
        val local = chore(frequency="slots",doneSlots=setOf("matí"))
        val merged = reconcileChoreState(
            mapOf(today.toString() to setOf("vespre")), local, "slots", today)
        assertEquals(setOf("matí","vespre"), merged.completedSlots)
        assertEquals("approved",choreStatusOnDate(local.copy(
            completedSlots=merged.completedSlots, date=merged.date),today))
    }

    @Test fun googleSheetsDateObjectsAreNormalisedToIsoCalendarDates() {
        assertEquals("2026-11-09",normalizeChoreDate("2026-11-09"))
        assertEquals("2026-11-09",normalizeChoreDate("2026-11-09 00:00:00"))
        assertEquals("2026-11-09",normalizeChoreDate(
            "Mon Nov 09 2026 00:00:00 GMT+0100 (Central European Standard Time)"))
        assertEquals(null, normalizeChoreDate("not a date"))
    }

    @Test fun completedItemsAreOrderedAfterOpenAndWaitingTasks() {
        val sorted = orderedRaiChores(listOf(
            chore(state = "approved").copy(id = "approved"),
            chore(state = "pending").copy(id = "pending"),
            chore(state = "todo").copy(id = "todo")
        ),today)
        assertEquals(listOf("todo","pending","approved"),sorted.map { it.id })
    }
}

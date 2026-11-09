package com.lito.planify.dashboard.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekFooterVisibilityTest {
    @Test fun emptyRemindersAreNotRepeatedOnToday() {
        assertFalse(shouldShowWeekFooter(showEmptyFooter = false, detail = null))
        assertFalse(shouldShowWeekFooter(showEmptyFooter = false, detail = ""))
        assertFalse(shouldShowWeekFooter(showEmptyFooter = false, detail = "   "))
    }

    @Test fun reminderWithContentAlwaysAppears() {
        assertTrue(shouldShowWeekFooter(showEmptyFooter = false, detail = "Portar el gos al veterinari"))
        assertTrue(shouldShowWeekFooter(showEmptyFooter = false, detail = "Vacunació"))
    }

    @Test fun otherScreensCanPreserveTheirEmptyFooter() {
        assertTrue(shouldShowWeekFooter(showEmptyFooter = true, detail = null))
        assertTrue(shouldShowWeekFooter(showEmptyFooter = true, detail = ""))
    }
}

package com.lito.planify.dashboard.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTimingTest {
    @Test fun resumeRefreshesAfterFiveMinutes() {
        val last = 1_000_000L
        assertFalse(SyncTiming.isStale(last, last + 299_999L, 300_000L))
        assertTrue(SyncTiming.isStale(last, last + 300_000L, 300_000L))
    }
    @Test fun separateDataAndPhotoIntervals() {
        val last = 1_000_000L
        assertTrue(SyncTiming.isStale(last, last + 900_000L, 900_000L))
        assertFalse(SyncTiming.isStale(last, last + 900_000L, 1_800_000L))
        assertTrue(SyncTiming.isStale(last, last + 1_800_000L, 1_800_000L))
    }
    @Test fun firstRunAndClockResetRefresh() {
        assertTrue(SyncTiming.isStale(0L, 1_000_000L, 900_000L))
        assertTrue(SyncTiming.isStale(2_000_000L, 1_000_000L, 900_000L))
    }
}

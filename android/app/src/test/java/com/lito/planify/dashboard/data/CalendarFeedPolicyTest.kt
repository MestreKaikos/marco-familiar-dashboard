package com.lito.planify.dashboard.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarFeedPolicyTest {
    @Test fun onlyGoogleIcalFeedsAreAccepted() {
        val valid = "https://calendar.google.com/calendar/ical/test%40example.com/private-fixture/basic.ics"
        assertTrue(CalendarFeedPolicy.validUrl(valid))
        assertTrue(CalendarFeedPolicy.validUrl(valid.replace("calendar.google.com", "www.google.com")))
        listOf("http://localhost/basic.ics", valid + "?redirect=1", valid + "#fragment",
            valid.replace("calendar.google.com", "calendar.google.com.evil.test"),
            valid.replace("calendar.google.com", "calendar.google.com@evil.test"),
            valid.replace("https:", "http:"), "https://calendar.google.com/calendar/ical/" + "x".repeat(2048) + "/basic.ics"
        ).forEach { assertFalse(CalendarFeedPolicy.validUrl(it)) }
    }
}

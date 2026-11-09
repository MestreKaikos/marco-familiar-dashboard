package com.lito.planify.dashboard.data

object CalendarFeedPolicy {
    const val MAX = 10
    private val googleIcal = Regex("""^https://(calendar\.google\.com|www\.google\.com)/calendar/ical/[^?#\s\\]+/basic\.ics$""")
    fun validUrl(value: String): Boolean = value.length <= 2048 && googleIcal.matches(value)
}

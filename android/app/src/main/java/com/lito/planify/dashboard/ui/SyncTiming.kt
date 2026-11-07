package com.lito.planify.dashboard.ui

internal object SyncTiming {
    fun isStale(lastSuccess: Long, now: Long, maxAgeMillis: Long): Boolean =
        lastSuccess <= 0L || now < lastSuccess || now - lastSuccess >= maxAgeMillis
}

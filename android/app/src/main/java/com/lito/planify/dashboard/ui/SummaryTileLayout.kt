package com.lito.planify.dashboard.ui

/** Capacitat en píxels mesurats: ni límit fix de files ni elements mig tallats. */
internal object SummaryTileLayout {
    fun visibleItems(availableHeight: Int, rowHeight: Int, gap: Int, total: Int): Int {
        if (availableHeight <= 0 || rowHeight <= 0 || total <= 0) return 0
        val safeGap = gap.coerceAtLeast(0)
        return ((availableHeight.toLong() + safeGap) / (rowHeight.toLong() + safeGap))
            .coerceAtMost(total.toLong()).toInt()
    }
}

package com.lito.planify.dashboard.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Evita regressions de la data retallada a «Avui», en pantalles compactes. */
class HeaderClockSizeTest {
    @Test fun largeHeaderPreservesOriginalClock() {
        assertEquals(150f, headerClockSize(280f), 0.001f)
    }

    @Test fun mediumHeaderLeavesDateLineAndBottomMargin() {
        for (height in listOf(150f, 175f, 190f, 205f)) {
            assertTrue("La data ha de cabre quan alçada=$height",
                headerClockSize(height) + 42f + 10f <= height)
        }
    }

    @Test fun compactHeaderLeavesSmallerDateLineAndMargin() {
        for (height in listOf(100f, 120f, 140f)) {
            assertTrue("La data compacta ha de cabre quan alçada=$height",
                headerClockSize(height) + 34f + 10f <= height)
        }
    }
}

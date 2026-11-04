package com.lito.planify.dashboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryTileLayoutTest {
    @Test fun showsAllFourShoppingItemsWhenTheyFit() {
        assertEquals(4, SummaryTileLayout.visibleItems(150, 36, 2, 4))
        assertEquals(4, SummaryTileLayout.visibleItems(150, 36, 2, 20))
    }
    @Test fun usesExtraHeightWithoutAnArbitraryRowLimit() {
        assertEquals(7, SummaryTileLayout.visibleItems(264, 36, 2, 20))
        assertEquals(3, SummaryTileLayout.visibleItems(264, 36, 2, 3))
    }
    @Test fun neverShowsAPartialRowAndAccountsForSpacing() {
        assertEquals(3, SummaryTileLayout.visibleItems(149, 36, 2, 4))
        assertEquals(1, SummaryTileLayout.visibleItems(36, 36, 2, 10))
        assertEquals(0, SummaryTileLayout.visibleItems(35, 36, 2, 10))
    }
    @Test fun respectsLargerMeasuredFontsAndEmptyLists() {
        assertEquals(2, SummaryTileLayout.visibleItems(150, 54, 2, 4))
        assertEquals(0, SummaryTileLayout.visibleItems(150, 36, 2, 0))
        assertEquals(0, SummaryTileLayout.visibleItems(0, 36, 2, 4))
    }
}

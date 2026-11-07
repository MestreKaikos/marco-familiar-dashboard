package com.lito.planify.dashboard.ui

import org.junit.Assert.*
import org.junit.Test

class NightClockConfigTest {
    @Test fun invalidPreferencesHaveSafeDefaults() {
        val c = NightClockConfig("unknown", "bad", "bad", "invalid", Float.NaN, Float.POSITIVE_INFINITY, Float.NaN).validated()
        assertEquals(NightClockConfig(), c)
    }
    @Test fun limitsProtectLayoutAndPanelBrightness() {
        val c = NightClockConfig(intensity = -1f, scale = 8f, brightness = 1f).validated()
        assertEquals(.08f, c.intensity, .0001f)
        assertEquals(1f, c.scale, .0001f)
        assertEquals(.30f, c.brightness, .0001f)
        assertEquals(.01f, NightClockConfig(brightness = -1f).validated().brightness, .0001f)
    }
    @Test fun customColorsAreStrictRgbWithoutAlphaOrNames() {
        assertEquals("#AB09EF", NightClockConfig.normalizeColor(" ab09ef "))
        assertNull(NightClockConfig.normalizeColor("red"))
        assertNull(NightClockConfig.normalizeColor("#FFFFFFFF"))
        assertNull(NightClockConfig.normalizeColor("#GG0000"))
        assertEquals("#B0B0B0", NightClockConfig(colorHex = "bad").validated().colorHex)
    }
    @Test fun allLayoutsAndFontChoicesArePersistentValues() {
        for (layout in listOf("digital", "stacked", "analog")) {
            val c = NightClockConfig(layout, "classic", "bold", "#FFAA00", .5f, .8f, .1f)
            assertEquals(c, c.validated())
        }
    }
}

package com.lito.planify.dashboard.ui

import org.junit.Assert.*
import org.junit.Test

class FrameCycleTest {
    private val config = FrameCycleConfig(enabled = true)
    @Test fun alternatesOnlyAfterEachConfiguredDeadline() {
        val initial = FrameCycle.interact(1_000)
        assertEquals(initial, FrameCycle.advance(initial, 120_999, config))
        val photos = FrameCycle.advance(initial, 121_000, config)
        assertEquals(FrameCyclePhase.PHOTOS, photos.phase)
        assertEquals(photos, FrameCycle.advance(photos, 720_999, config))
        val agenda = FrameCycle.advance(photos, 721_000, config)
        assertEquals(FrameCyclePhase.AGENDA, agenda.phase)
        assertEquals(agenda, FrameCycle.advance(agenda, 750_999, config))
        assertEquals(FrameCyclePhase.PHOTOS, FrameCycle.advance(agenda, 751_000, config).phase)
    }
    @Test fun touchingPhotosOrAgendaStartsAFreshManualInterval() {
        for (phase in listOf(FrameCyclePhase.PHOTOS, FrameCyclePhase.AGENDA)) {
            val playing = FrameCycleState(phase, 1_000, 0)
            assertEquals(phase, FrameCycle.advance(playing, 1_001, config).phase)
            val manual = FrameCycle.interact(5_000)
            assertEquals(FrameCyclePhase.MANUAL, FrameCycle.advance(manual, 124_999, config).phase)
            assertEquals(FrameCyclePhase.PHOTOS, FrameCycle.advance(manual, 125_000, config).phase)
        }
    }
    @Test fun neverShowsAnEmptySlideshowAndStopsForNightOrEditing() {
        val photos = FrameCycleState(FrameCyclePhase.PHOTOS, 1_000, 0)
        assertEquals(FrameCyclePhase.MANUAL, FrameCycle.advance(photos, 500_000, config, hasPhotos = false).phase)
        assertEquals(FrameCyclePhase.MANUAL, FrameCycle.advance(photos, 500_000, config, canRun = false).phase)
        assertEquals(FrameCyclePhase.MANUAL, FrameCycle.advance(photos, 500_000, config.copy(enabled = false)).phase)
    }
    @Test fun aDelayedTickStillShowsAgendaForItsFullDuration() {
        val photos = FrameCycleState(FrameCyclePhase.PHOTOS, 1_000, 0)
        val agenda = FrameCycle.advance(photos, 10_000_000, config)
        assertEquals(FrameCyclePhase.AGENDA, agenda.phase)
        assertEquals(10_000_000L, agenda.startedAt)
        assertEquals(agenda, FrameCycle.advance(agenda, 10_029_999, config))
    }
    @Test fun validatesSavedSettingsAndClockReset() {
        val invalid = FrameCycleConfig(idleMinutes = -1, photoMinutes = 999, agendaSeconds = 0, slideSeconds = 1).validated()
        assertEquals(FrameCycleConfig(), invalid)
        assertFalse(FrameCycleConfig().enabled)
        assertEquals(FrameCycle.interact(10), FrameCycle.advance(FrameCycleState(FrameCyclePhase.PHOTOS, 100, 100), 10, config))
    }
}

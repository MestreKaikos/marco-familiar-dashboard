package com.lito.planify.dashboard.ui

/** Temps del rellotge monòton del dispositiu; les dates del calendari no alteren el cicle. */
data class FrameCycleConfig(
    val enabled: Boolean = false,
    val idleMinutes: Int = 2,
    val photoMinutes: Int = 10,
    val agendaSeconds: Int = 30,
    val slideSeconds: Int = 20
) {
    fun validated() = copy(
        idleMinutes = idleMinutes.takeIf { it in IDLE_MINUTES } ?: 2,
        photoMinutes = photoMinutes.takeIf { it in PHOTO_MINUTES } ?: 10,
        agendaSeconds = agendaSeconds.takeIf { it in AGENDA_SECONDS } ?: 30,
        slideSeconds = slideSeconds.takeIf { it in SLIDE_SECONDS } ?: 20
    )
    companion object {
        val IDLE_MINUTES = listOf(1, 2, 5, 10)
        val PHOTO_MINUTES = listOf(5, 10, 15, 30)
        val AGENDA_SECONDS = listOf(15, 30, 60)
        val SLIDE_SECONDS = listOf(10, 20, 30, 60)
    }
}

enum class FrameCyclePhase { MANUAL, PHOTOS, AGENDA }
data class FrameCycleState(
    val phase: FrameCyclePhase = FrameCyclePhase.MANUAL,
    val startedAt: Long = 0L,
    val lastInteractionAt: Long = 0L
)

object FrameCycle {
    fun interact(now: Long) = FrameCycleState(lastInteractionAt = now, startedAt = now)
    fun advance(state: FrameCycleState, now: Long, config: FrameCycleConfig,
                hasPhotos: Boolean = true, canRun: Boolean = true): FrameCycleState {
        if (!config.enabled || !canRun || !hasPhotos || now < state.lastInteractionAt || now < state.startedAt)
            return interact(now)
        val elapsed = now - if (state.phase == FrameCyclePhase.MANUAL) state.lastInteractionAt else state.startedAt
        val duration = when (state.phase) {
            FrameCyclePhase.MANUAL -> config.idleMinutes * 60_000L
            FrameCyclePhase.PHOTOS -> config.photoMinutes * 60_000L
            FrameCyclePhase.AGENDA -> config.agendaSeconds * 1_000L
        }
        if (elapsed < duration) return state
        return state.copy(phase = if (state.phase == FrameCyclePhase.PHOTOS) FrameCyclePhase.AGENDA else FrameCyclePhase.PHOTOS,
            startedAt = now)
    }
}

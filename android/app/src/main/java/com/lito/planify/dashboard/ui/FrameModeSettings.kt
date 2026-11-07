package com.lito.planify.dashboard.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Opcions pròpies del marc, desades entre actualitzacions i reinicis. */
class FrameModeSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("marc_frame_mode", Context.MODE_PRIVATE)
    var config by mutableStateOf(FrameCycleConfig(
        enabled = prefs.getBoolean("enabled", false),
        idleMinutes = prefs.getInt("idle_minutes", 2),
        photoMinutes = prefs.getInt("photo_minutes", 10),
        agendaSeconds = prefs.getInt("agenda_seconds", 30),
        slideSeconds = prefs.getInt("slide_seconds", 20)
    ).validated())
        private set
    fun update(value: FrameCycleConfig) {
        config = value.validated()
        prefs.edit().putBoolean("enabled", config.enabled)
            .putInt("idle_minutes", config.idleMinutes).putInt("photo_minutes", config.photoMinutes)
            .putInt("agenda_seconds", config.agendaSeconds).putInt("slide_seconds", config.slideSeconds).apply()
    }
}

@Composable
fun rememberFrameModeSettings(): FrameModeSettings {
    val context = LocalContext.current
    return remember(context) { FrameModeSettings(context) }
}

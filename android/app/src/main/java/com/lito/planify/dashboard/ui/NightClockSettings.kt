package com.lito.planify.dashboard.ui

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class NightClockSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("marc_night_clock", Context.MODE_PRIVATE)
    var config by mutableStateOf(NightClockConfig(
        layout = prefs.getString("layout", "digital") ?: "digital",
        font = prefs.getString("font", "modern") ?: "modern",
        weight = prefs.getString("weight", "light") ?: "light",
        colorHex = prefs.getString("color", "#B0B0B0") ?: "#B0B0B0",
        intensity = prefs.getFloat("intensity", 0.28f),
        scale = prefs.getFloat("scale", 0.65f),
        brightness = prefs.getFloat("brightness", 10f / 255f)
    ).validated())
        private set
    private val changes = MutableStateFlow(config)
    val flow = changes.asStateFlow()
    fun update(value: NightClockConfig) {
        config = value.validated()
        changes.value = config
        prefs.edit().putString("layout", config.layout).putString("font", config.font)
            .putString("weight", config.weight).putString("color", config.colorHex)
            .putFloat("intensity", config.intensity).putFloat("scale", config.scale)
            .putFloat("brightness", config.brightness).apply()
    }
}

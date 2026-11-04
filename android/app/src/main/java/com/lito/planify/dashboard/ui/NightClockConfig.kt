package com.lito.planify.dashboard.ui

/** Valors independents d'Android, validats també en llegir preferències antigues. */
data class NightClockConfig(
    val layout: String = "digital",
    val font: String = "modern",
    val weight: String = "light",
    val colorHex: String = "#B0B0B0",
    val intensity: Float = 0.28f,
    val scale: Float = 0.65f,
    val brightness: Float = 10f / 255f
) {
    fun validated() = copy(
        layout = layout.takeIf { it in listOf("digital", "stacked", "analog") } ?: "digital",
        font = font.takeIf { it in listOf("modern", "classic", "mono", "compact") } ?: "modern",
        weight = weight.takeIf { it in listOf("light", "normal", "bold") } ?: "light",
        colorHex = normalizeColor(colorHex) ?: "#B0B0B0",
        intensity = intensity.safe(0.08f, 1f, 0.28f),
        scale = scale.safe(0.35f, 1f, 0.65f),
        brightness = brightness.safe(0.01f, 0.30f, 10f / 255f)
    )
    companion object {
        fun normalizeColor(value: String): String? = value.trim().uppercase().let {
            val hex = if (it.startsWith("#")) it else "#$it"
            hex.takeIf { Regex("#[0-9A-F]{6}").matches(it) }
        }
    }
}
private fun Float.safe(min: Float, max: Float, fallback: Float) = if (isFinite()) coerceIn(min, max) else fallback

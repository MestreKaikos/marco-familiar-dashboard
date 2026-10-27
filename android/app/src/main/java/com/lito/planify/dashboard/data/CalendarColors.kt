package com.lito.planify.dashboard.data

/**
 * Colors de calendaris disponibles al Marc Familiar.
 * El color és una propietat persistent del calendari, no depèn de la seva posició.
 */
data class CalendarSwatch(val name: String, val hex: String)

object CalendarColors {
    val options = listOf(
        CalendarSwatch("Blau", "#3977CB"),
        CalendarSwatch("Lila", "#B965AF"),
        CalendarSwatch("Verd", "#36A070"),
        CalendarSwatch("Taronja", "#E09132"),
        CalendarSwatch("Vermell", "#BF5960"),
        CalendarSwatch("Turquesa", "#1696A8"),
        CalendarSwatch("Porpra", "#7857BC"),
        CalendarSwatch("Gris", "#657484")
    )

    private val valid = Regex("^#[0-9A-Fa-f]{6}$")
    fun normalize(value: String?): String? =
        value?.takeIf { valid.matches(it) }?.uppercase(java.util.Locale.ROOT)

    /** Respecta l'ordre antic només en la migració; després cada color queda guardat. */
    fun legacy(index: Int): String = options[index % 5].hex

    /** Un calendari nou rep preferentment un color diferent dels que ja s'usen. */
    fun nextAvailable(calendars: List<PersonalCalendar>): String {
        val used = calendars.mapNotNull { normalize(it.color) }.toSet()
        return options.firstOrNull { it.hex !in used }?.hex ?: options.first().hex
    }
}

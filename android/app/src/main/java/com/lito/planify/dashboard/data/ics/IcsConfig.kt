package com.lito.planify.dashboard.data.ics

import android.util.Base64
import com.lito.planify.dashboard.BuildConfig
import com.lito.planify.dashboard.data.PersonalCalendar
import java.io.InputStreamReader
import java.util.Properties

/** Papel de cada calendario en el dashboard. */
enum class CalendarRole {
    /** Citas: Hoy (siguiente evento, próximos eventos, calendario del mes) y la semana del Calendario. */
    EVENTS,
    /** Turnos: en lugar de las rutinas de Planify (Hoy y la banda de la semana), con el color de cada tipo de turno. */
    SHIFTS,
    /** Menú del cole: un evento de día completo por día, en su propia tarjeta. */
    MENU
}

data class IcsCalendar(val id: Int, val key: String, val name: String, val color: String, val role: CalendarRole, val url: String)

/**
 * Calendarios iCal y colores de los turnos de `android/dashboard.properties` (ver scripts/make_dashboard_props.py),
 * incrustado en BuildConfig. Sin él, [enabled] es false y la app funciona con Planify como siempre.
 */
class IcsConfig(private val props: Properties) {
    constructor() : this(Properties().apply {
        val raw = BuildConfig.DASHBOARD_CONFIG
        if (raw.isNotEmpty()) InputStreamReader(Base64.decode(raw, Base64.DEFAULT).inputStream(), Charsets.UTF_8).use { load(it) }
    })

    private val builtInCalendars: List<IcsCalendar> = props.stringPropertyNames()
        .mapNotNull { Regex("""calendar\.([^.]+)\.url""").matchEntire(it)?.groupValues?.get(1) }
        .sorted()
        .mapIndexedNotNull { i, key ->
            val url = props.getProperty("calendar.$key.url").orEmpty().trim()
            val role = runCatching { CalendarRole.valueOf(props.getProperty("calendar.$key.role").orEmpty().uppercase()) }.getOrNull()
            if (url.isEmpty() || role == null) null
            else IcsCalendar(i + 1, key, props.getProperty("calendar.$key.name") ?: key, props.getProperty("calendar.$key.color") ?: "#888888", role, url)
        }

    /** Els calendaris introduïts al marc substitueixen els de compilació quan se'n desa algun. */
    private var personalCalendars: List<IcsCalendar>? = null
    val calendars: List<IcsCalendar> get() = personalCalendars ?: builtInCalendars

    fun setPersonalCalendars(calendars: List<PersonalCalendar>) {
        val colors = listOf("#3977CB", "#B965AF", "#36A070", "#E09132", "#BF5960")
        personalCalendars = calendars.take(5).mapIndexed { i, calendar ->
            IcsCalendar(
                id = i + 1,
                key = calendar.id,
                name = calendar.name.take(50),
                color = colors[i % colors.size],
                role = CalendarRole.EVENTS,
                url = calendar.url
            )
        }
    }

    /** Color de cada tipo de turno por su nombre (los espacios del nombre van como «_» en el fichero). */
    val shiftColors: Map<String, String> = props.stringPropertyNames()
        .filter { it.startsWith("shift.color.") }
        .associate { it.removePrefix("shift.color.").replace('_', ' ') to props.getProperty(it) }

    /** Lista de la compra (compra.json del servidor, ruta secreta); null si no hay. */
    val shoppingUrl: String? = props.getProperty("compra.url")?.trim()?.ifEmpty { null }

    /** Tareas de los niños (familia-tareas del servidor): base «…/tareas/» y token del marco; null si no hay. */
    val choresUrl: String? = props.getProperty("tareas.url")?.trim()?.ifEmpty { null }
    val choresToken: String? = props.getProperty("tareas.token")?.trim()?.ifEmpty { null }

    /** Modo demostración (capturas para compartir): solo si hay líneas `demo.replace.N`; en el marco, null. */
    val demo: com.lito.planify.dashboard.data.Demo? = com.lito.planify.dashboard.data.Demo.from(props)

    val enabled: Boolean get() = calendars.isNotEmpty()
}

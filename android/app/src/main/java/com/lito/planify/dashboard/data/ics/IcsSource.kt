package com.lito.planify.dashboard.data.ics

import biweekly.Biweekly
import biweekly.ICalendar
import biweekly.component.VEvent
import biweekly.util.ICalDate
import com.lito.planify.dashboard.data.Event
import com.lito.planify.dashboard.data.HttpError
import com.lito.planify.dashboard.data.SQL_DATE_TIME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Eventos de los calendarios iCal configurados ([IcsConfig]), convertidos a los [Event] que ya pinta la app.
 *
 * - Expande los eventos repetidos (RRULE/RDATE menos EXDATE) en la ventana pedida y aplica las instancias
 *   modificadas (RECURRENCE-ID); las canceladas no salen.
 * - Hora local del marco para los eventos con hora; los de día completo se quedan en su fecha, a las 00:00, con
 *   `all_day`.
 * - Turnos ([CalendarRole.SHIFTS]): con el color de su tipo (por el título) y `routine_id`, para que la app los trate
 *   como las rutinas de Planify.
 * - Cada calendario se descarga como mucho una vez cada [MAX_AGE_MS]: Hoy y las semanas del Calendario comparten la
 *   descarga.
 */
class IcsSource(
    private val config: IcsConfig,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
    private val download: suspend (String) -> String = ::httpGet
) {
    private class Parsed(val fetchedAt: Long, val calendars: List<ICalendar>)

    private val parsed = mutableMapOf<String, Parsed>()
    private val mutex = Mutex()

    /** Ocurrencias que empiezan en [from, to) de los calendarios con alguno de los [roles], ordenadas. */
    suspend fun events(from: LocalDate, to: LocalDate, vararg roles: CalendarRole): List<Event> {
        val out = mutableListOf<Event>()
        for (cal in config.calendars.filter { it.role in roles }) {
            val icals = calendar(cal.url)
            withContext(Dispatchers.Default) { icals.forEach { out += expand(cal, it, from, to) } }
        }
        return out.sortedWith(compareBy({ it.date_at }, { it.title }))
    }

    /** Vuelve a descargar todo en la próxima petición (botón de sincronizar). */
    suspend fun invalidate() = mutex.withLock { parsed.clear() }

    private suspend fun calendar(url: String): List<ICalendar> = mutex.withLock {
        parsed[url]?.takeIf { now() - it.fetchedAt < MAX_AGE_MS }?.let { return it.calendars }
        val text = download(url)
        val calendars = withContext(Dispatchers.Default) { Biweekly.parse(text).all() }
        if (calendars.isEmpty()) throw IllegalStateException("iCal vacío o ilegible")
        parsed[url] = Parsed(now(), calendars)
        calendars
    }

    private fun expand(cal: IcsCalendar, ical: ICalendar, from: LocalDate, to: LocalDate): List<Event> {
        val events = ical.events
        // Instancias modificadas de un evento repetido: sustituyen a la suya (uid + inicio original)
        val overridden = events.filter { it.recurrenceId != null }
            .mapNotNull { e -> e.uid?.value?.let { uid -> uid to e.recurrenceId.value.time } }
            .toSet()
        val out = mutableListOf<Event>()
        for (e in events) {
            if (e.status?.isCancelled == true) continue
            val start = e.dateStart?.value ?: continue
            val allDay = !start.hasTime()
            val tz = timezoneOf(ical, e)
            val length = lengthMillis(e, start, allDay)
            val uid = e.uid?.value.orEmpty()
            if (e.recurrenceRule == null && e.recurrenceDates.isEmpty() || e.recurrenceId != null) {
                occurrence(cal, e, start, allDay, length, from, to)?.let(out::add)
                continue
            }
            val it = e.getDateIterator(tz)
            it.advanceTo(Date.from(from.minusDays(1).atStartOfDay(zone).toInstant()))
            var guard = 0
            while (it.hasNext() && guard++ < MAX_OCCURRENCES) {
                val date = it.next()
                if ((uid to date.time) in overridden) continue
                val instance = ICalDate(date, start.hasTime())
                if (local(instance, allDay, tz) >= to.atStartOfDay()) break
                occurrence(cal, e, instance, allDay, length, from, to, tz)?.let(out::add)
            }
        }
        return out
    }

    private fun occurrence(
        cal: IcsCalendar, e: VEvent, start: ICalDate, allDay: Boolean, length: Long, from: LocalDate, to: LocalDate,
        tz: TimeZone? = null
    ): Event? {
        val at = local(start, allDay, tz)
        if (at < from.atStartOfDay() || at >= to.atStartOfDay()) return null
        // Con hora, sobre el instante: una noche que cruza el cambio de hora dura 11 h pero acaba a las 08:00
        val end = if (allDay) at.plusDays((length / DAY_MS).coerceAtLeast(1))
        else Instant.ofEpochMilli(start.time + length).atZone(zone).toLocalDateTime()
        val title = e.summary?.value?.trim().orEmpty().ifEmpty { "(sin título)" }
        val shift = cal.role == CalendarRole.SHIFTS
        return Event(
            id = "${cal.key}|${e.uid?.value}|$at".hashCode(),
            calendar_id = cal.id,
            calendar_name = cal.name,
            calendar_color = if (shift) config.shiftColors[title] ?: cal.color else cal.color,
            title = title,
            date_at = at.format(SQL_DATE_TIME),
            routine_id = if (shift) cal.id else null,
            persons = null,
            all_day = allDay,
            end_at = end.format(SQL_DATE_TIME),
            location = e.location?.value?.trim()?.ifEmpty { null },
            description = e.description?.value?.trim()?.ifEmpty { null }
        )
    }

    /** Día completo: su fecha tal cual (sin zona). Con hora: el instante en la zona del marco. */
    private fun local(date: ICalDate, allDay: Boolean, tz: TimeZone? = null): LocalDateTime =
        if (allDay) {
            val c = date.rawComponents
            if (c != null && tz == null) LocalDate.of(c.year, c.month, c.date).atStartOfDay()
            else date.toInstant().atZone(tz?.toZoneId() ?: zone).toLocalDate().atStartOfDay()
        } else {
            date.toInstant().atZone(zone).toLocalDateTime()
        }

    private fun lengthMillis(e: VEvent, start: ICalDate, allDay: Boolean): Long {
        e.dateEnd?.value?.let { return (it.time - start.time).coerceAtLeast(0) }
        e.duration?.value?.let { return it.toMillis() }
        return if (allDay) DAY_MS else 0
    }

    /** Zona del DTSTART (VTIMEZONE o TZID conocido); los de día completo y sin zona, la del marco. */
    private fun timezoneOf(ical: ICalendar, e: VEvent): TimeZone {
        val info = ical.timezoneInfo
        return (if (info.isFloating(e.dateStart)) null else info.getTimezone(e.dateStart)?.timeZone)
            ?: info.defaultTimezone?.timeZone
            ?: TimeZone.getTimeZone(zone)
    }

    companion object {
        const val MAX_AGE_MS = 5 * 60_000L
        private const val DAY_MS = 86_400_000L
        /** Tope de repeticiones por evento y ventana (un evento diario sin fin en 25 meses son ~760). */
        private const val MAX_OCCURRENCES = 2000

        private val http by lazy {
            OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
        }

        private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw HttpError(response.code)
                response.body?.string() ?: throw HttpError(response.code)
            }
        }
    }
}

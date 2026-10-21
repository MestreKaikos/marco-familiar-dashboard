package com.lito.planify.dashboard.data.repository

import com.lito.planify.dashboard.data.Api
import com.lito.planify.dashboard.data.Content
import com.lito.planify.dashboard.data.bodyOrThrow
import com.lito.planify.dashboard.data.cache.Cached
import com.lito.planify.dashboard.data.cache.CachedResource
import com.lito.planify.dashboard.data.cache.DiskCache
import com.lito.planify.dashboard.data.ics.CalendarRole
import com.lito.planify.dashboard.data.ics.IcsSource
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Contenido de Hoy: todos los eventos desde el día 1 del mes de hace [PAST_MONTHS] meses (el calendario de Hoy marca
 * también los días pasados; Próximos eventos, desde hoy y sin límite), las sesiones de rutina de esta semana y las tareas pendientes, de los
 * calendarios y listas marcados para el dashboard.
 *
 * Con calendarios iCal ([ics] no nulo), en lugar de Planify: las citas en la misma ventana (hasta [FUTURE_MONTHS]
 * meses por delante, que es lo que enseña el calendario del mes), los turnos en lugar de las rutinas y el menú del
 * cole, ambos desde el lunes de esta semana hasta dentro de [WEEKS_AHEAD_DAYS] días (las semanas de la portada y del
 * Calendario), y sin tareas.
 */
class ContentRepository(private val api: Api, cache: DiskCache, private val ics: IcsSource? = null) {
    private val resource = CachedResource<Content>(cache, "content", Content::class.java, MAX_AGE_MS)

    val content: StateFlow<Cached<Content>?> = resource.data

    fun isStale() = resource.isStale()

    suspend fun refresh(token: String, today: LocalDate): Result<Content> = resource.refresh {
        if (ics != null) return@refresh icsContent(ics, today)
        val auth = bearer(token)
        val zone = TimeZone.getDefault().id
        // Igual que la app: la hora local se envía como segundos epoch en UTC.
        val start = today.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val monthStart = today.withDayOfMonth(1).minusMonths(PAST_MONTHS).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val end = today.plusDays(ROUTINE_DAYS).atStartOfDay().toEpochSecond(ZoneOffset.UTC) - 1
        Content(
            events = api.events(auth, monthStart, null, routine = false, timezone = zone).bodyOrThrow().sortedBy { it.date_at },
            sessions = api.events(auth, start, end, routine = true, timezone = zone).bodyOrThrow().sortedBy { it.date_at },
            tasks = api.tasks(auth).bodyOrThrow().filter { it.completed_at == null }
        )
    }

    private suspend fun icsContent(ics: IcsSource, today: LocalDate) = Content(
        events = ics.events(today.withDayOfMonth(1).minusMonths(PAST_MONTHS), today.withDayOfMonth(1).plusMonths(FUTURE_MONTHS + 1), CalendarRole.EVENTS),
        sessions = ics.events(today.with(java.time.DayOfWeek.MONDAY), today.plusDays(WEEKS_AHEAD_DAYS), CalendarRole.SHIFTS),
        tasks = emptyList(),
        menu = ics.events(today.with(java.time.DayOfWeek.MONDAY), today.plusDays(WEEKS_AHEAD_DAYS), CalendarRole.MENU)
    )

    fun clear() = resource.clear()

    companion object {
        /** Días de rutinas que se descargan: hoy y los 6 siguientes. */
        const val ROUTINE_DAYS = 7L
        /** Meses de eventos pasados que se descargan, para el calendario de Hoy. */
        const val PAST_MONTHS = 12L
        /** Con iCal, meses por delante (los mismos que se pueden ver en el calendario de Hoy). */
        const val FUTURE_MONTHS = 12L
        /** Con iCal, días por delante de turnos y menú del cole: las 13 semanas (unos 3 meses) del Calendario. */
        const val WEEKS_AHEAD_DAYS = 91L
        const val MAX_AGE_MS = 5 * 60_000L
    }
}

internal fun bearer(token: String) = "Bearer $token"

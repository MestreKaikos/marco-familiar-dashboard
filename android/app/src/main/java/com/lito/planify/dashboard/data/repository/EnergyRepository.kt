package com.lito.planify.dashboard.data.repository

import com.lito.planify.dashboard.data.Api
import com.lito.planify.dashboard.data.Energy
import com.lito.planify.dashboard.data.HourPrice
import com.lito.planify.dashboard.data.ReeResponse
import com.lito.planify.dashboard.data.SQL_DATE_TIME
import com.lito.planify.dashboard.data.bodyOrThrow
import com.lito.planify.dashboard.data.cache.Cached
import com.lito.planify.dashboard.data.cache.CachedResource
import com.lito.planify.dashboard.data.cache.DiskCache
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Precio de la luz (PVPC) de hoy y mañana, de la API pública de Red Eléctrica (sin clave). Mañana sale sobre las
 * 20:30; hasta entonces REE devuelve solo hoy. Caduca a los [MAX_AGE_MS], así que lo de mañana llega solo.
 */
class EnergyRepository(private val api: Api, cache: DiskCache, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val resource = CachedResource<Energy>(cache, "energy", Energy::class.java, MAX_AGE_MS)

    val energy: StateFlow<Cached<Energy>?> = resource.data

    fun isStale() = resource.isStale()

    suspend fun refresh(today: LocalDate): Result<Energy> = resource.refresh {
        parse(api.ree(Api.reeUrl(today, today.plusDays(1))).bodyOrThrow(), zone)
    }

    companion object {
        const val MAX_AGE_MS = 30 * 60_000L

        /** La serie «PVPC» en €/kWh y en hora local; sin ella (REE sin datos) es un error y se queda la copia. */
        fun parse(response: ReeResponse, zone: ZoneId): Energy {
            val pvpc = response.included.orEmpty().firstOrNull { it.type.equals("PVPC", ignoreCase = true) }
                ?: throw IllegalStateException("REE sin PVPC")
            return Energy(pvpc.attributes.values.map {
                HourPrice(
                    at = OffsetDateTime.parse(it.datetime).atZoneSameInstant(zone).toLocalDateTime().format(SQL_DATE_TIME),
                    eur_kwh = it.value / 1000.0
                )
            }.sortedBy { it.at })
        }
    }
}

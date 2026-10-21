package com.lito.planify.dashboard.data.repository

import com.lito.planify.dashboard.data.ReeAttributes
import com.lito.planify.dashboard.data.ReeResponse
import com.lito.planify.dashboard.data.ReeSeries
import com.lito.planify.dashboard.data.ReeValue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class EnergyRepositoryTest {
    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun parsesPvpcToEurosPerKwhInLocalTime() {
        val response = ReeResponse(
            listOf(
                ReeSeries("Precio mercado spot", ReeAttributes(listOf(ReeValue(235.0, "2026-10-20T00:00:00.000+02:00")))),
                ReeSeries(
                    "PVPC", ReeAttributes(
                        listOf(
                            ReeValue(200.97, "2026-10-20T01:00:00.000+02:00"),
                            ReeValue(208.94, "2026-10-20T00:00:00.000+02:00"),
                            // el día del cambio de hora llega con otra zona: se pasa a la del marco
                            ReeValue(150.0, "2026-10-25T02:00:00.000+01:00")
                        )
                    )
                )
            )
        )
        val energy = EnergyRepository.parse(response, madrid)
        assertEquals(listOf("2026-10-20 00:00:00", "2026-10-20 01:00:00", "2026-10-25 02:00:00"), energy.prices.map { it.at })
        assertEquals(0.20894, energy.prices[0].eur_kwh, 1e-9)
    }

    @Test(expected = IllegalStateException::class)
    fun withoutPvpcIsAnError() {
        EnergyRepository.parse(ReeResponse(null), madrid)
    }
}

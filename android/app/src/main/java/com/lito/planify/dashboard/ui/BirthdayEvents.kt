package com.lito.planify.dashboard.ui

import com.lito.planify.dashboard.data.Event
import java.time.LocalDate
import java.util.Locale

/**
 * Reconeix només aniversaris de persones identificats pel títol del calendari.
 * No amaga altres cites de tot el dia (festius, vacances, reunions, etc.).
 *
 * Exemples: «Aniversari de Marc», «Aniversari d'Anna» i «ANIVERSARI D’ÈLIA».
 */
private val birthdayTitle = Regex(
    """^\s*aniversari\s+d(?:e\s+|['’]\s*)(.+?)\s*$""",
    RegexOption.IGNORE_CASE
)

internal fun birthdayPerson(title: String): String? =
    birthdayTitle.matchEntire(title.trim())?.groupValues?.getOrNull(1)
        ?.trim()?.takeIf { it.isNotEmpty() }

internal fun isBirthdayEvent(event: Event): Boolean = birthdayPerson(event.title) != null

/** En mostra només els d'avui, perquè els futurs no omplin l'agenda de 7 dies. */
internal fun birthdaysToday(events: List<Event>, today: LocalDate): List<String> =
    events.asSequence()
        .filter { it.at.toLocalDate() == today }
        .mapNotNull { birthdayPerson(it.title) }
        .distinctBy { it.lowercase(Locale.ROOT) }
        .toList()

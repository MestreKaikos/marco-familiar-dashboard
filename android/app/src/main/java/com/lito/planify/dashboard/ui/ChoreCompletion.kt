package com.lito.planify.dashboard.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Converteix dates del full, també si Sheets ha convertit una cel·la en Date. */
internal fun normalizeChoreDate(raw: String): String? {
    val value = raw.trim()
    if (Regex("^\\d{4}-\\d{2}-\\d{2}(?:$|[T ])").containsMatchIn(value)) {
        return runCatching { LocalDate.parse(value.take(10)).toString() }.getOrNull()
    }
    // Google Apps Script: String(Date) → «Mon Nov 09 2026 00:00:00 GMT+0100 (...)».
    val jsDate = Regex("^[A-Za-z]{3} [A-Za-z]{3} \\d{1,2} \\d{4} \\d\\d:\\d\\d:\\d\\d GMT[+-]\\d{4}")
        .find(value)?.value ?: return null
    return runCatching {
        LocalDate.from(DateTimeFormatter.ofPattern(
            "EEE MMM dd yyyy HH:mm:ss 'GMT'Z", Locale.ENGLISH
        ).parse(jsDate)).toString()
    }.getOrNull()
}

/**
 * Estat per data: aprovada avui no torna a oferir-se avui, independentment
 * de les sincronitzacions. «once» queda aprovada per sempre.
 *
 * Per franges només es mostra «feta» quan s'han registrat TOTES les franges
 * del dia, mai després de marcar-ne tan sols una.
 */
internal fun choreStatusOnDate(task: LocalChore, today: LocalDate): String {
    val schedule = task.frequency
    if (schedule == "once") {
        return when (task.status) {
            "approved" -> "approved"
            "pending" -> "pending"
            else -> "todo"
        }
    }
    val matchesDate = task.date == today.toString()
    if ((schedule == "weekly" || schedule == "weekdays") &&
        today.dayOfWeek.value.toString() !in task.days.split(",")) return "not_due"
    if (schedule == "slots") {
        val configured = task.slots.split(",").map(String::trim).filter(String::isNotEmpty)
        return if (matchesDate && configured.isNotEmpty() &&
            task.completedSlots.containsAll(configured)) "approved" else "slots"
    }
    return if (matchesDate) task.status else "todo"
}

/** Ordena primer les pendents, després les que esperen validació, i al final les fetes. */
internal fun orderedRaiChores(tasks: List<LocalChore>, today: LocalDate): List<LocalChore> =
    tasks.withIndex().sortedWith(compareBy<IndexedValue<LocalChore>> {
        when (choreStatusOnDate(it.value, today)) {
            "todo", "slots" -> 0
            "pending" -> 1
            "approved" -> 2
            else -> 3
        }
    }.thenBy { it.index }).map { it.value }

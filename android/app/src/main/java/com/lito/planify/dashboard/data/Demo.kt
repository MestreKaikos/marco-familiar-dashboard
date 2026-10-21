package com.lito.planify.dashboard.data

import com.lito.planify.dashboard.UiState

/**
 * Modo demostración, para sacar capturas que se puedan compartir: cambia nombres de personas, equipos y lugares en
 * todo lo que se pinta. Se activa solo si dashboard.properties trae líneas `demo.replace.N=original=>nuevo` (y,
 * opcionales, `demo.rival=Rival FC` para el equipo contrario de los partidos de los niños, `demo.turnos.dias=3` para
 * mover los turnos esos días (siguen siendo turnos con sentido, pero no los de verdad) y `demo.coche=…` para el nombre
 * del coche, y `demo.ficticio=true` para cambiar del todo citas, turnos, menús y listas por una familia inventada
 * ([FakeFamily]), y `demo.cargando=true` para enseñar el coche cargando. La versión del marco no las lleva: entonces [Demo.from] devuelve null y no se toca nada.
 */
class Demo(
    private val replacements: List<Pair<Regex, String>>,
    private val rival: String?,
    private val shiftDays: Long = 0,
    private val car: String? = null,
    private val fictitious: Boolean = false,
    private val charging: Boolean = false
) {
    private val kids = replacements.map { it.second }

    private fun opt(s: String?): String? = s?.let(::text)

    fun text(s: String): String {
        var out = replacements.fold(s) { acc, (re, to) -> re.replace(acc, to) }
        // «⚽ Leo vs Sporting…»: el rival, cambiado (solo en los partidos de los niños)
        if (rival != null && out.startsWith("⚽") && kids.any { out.contains("$it vs") }) {
            out = Regex("""(⚽\s*\S+ vs ).*""").replace(out) { it.groupValues[1] + rival }
        }
        return out
    }

    private fun event(e: Event) = e.copy(title = text(e.title), location = opt(e.location), description = opt(e.description))

    /** Un turno, movido [shiftDays] días. */
    private fun shift(e: Event) = if (shiftDays == 0L) event(e) else event(e).copy(
        date_at = e.at.plusDays(shiftDays).format(SQL_DATE_TIME),
        end_at = e.end?.plusDays(shiftDays)?.format(SQL_DATE_TIME)
    )

    fun apply(state: UiState): UiState = replace(
        if (!fictitious) state
        else state.copy(content = FakeFamily.apply(state.content, state.now.toLocalDate()), weeks = emptyMap(), shopping = FakeFamily.shopping(state.shopping))
    )

    private fun replace(state: UiState): UiState = state.copy(
        content = state.content?.let { c ->
            c.copy(events = c.events.map(::event), sessions = c.sessions.map(::shift).sortedBy { it.date_at }, menu = c.menu?.map(::event))
        },
        weeks = state.weeks.mapValues { (_, w) -> w.copy(events = w.events.map(::event), sessions = w.sessions.map(::shift)) },
        chores = state.chores?.let { ch ->
            ch.copy(
                ninos = ch.ninos?.map { it.copy(nombre = text(it.nombre)) },
                tareas = ch.tareas?.map { t -> t.copy(hechas = t.hechas?.map { it.copy(nombre = opt(it.nombre)) }) },
                pedidas = ch.pedidas?.map { it.copy(nombre = opt(it.nombre)) }
            )
        },
        shopping = state.shopping?.let { sh ->
            fun items(l: List<ShoppingItem>?) = l?.map { it.copy(producto = text(it.producto), detalle = opt(it.detalle)) }
            sh.copy(items = items(sh.items), listas = sh.listas?.map { it.copy(items = items(it.items)) }, coche = sh.coche?.let { c ->
                (car?.let { c.copy(modelo = it) } ?: c).let { if (charging) it.copy(cargando = true, enchufado = true, potencia_kw = it.potencia_kw ?: 3.2) else it }
            })
        },
        location = state.location?.let { it.copy(name = text(it.name), detail = text(it.detail)) }
    )

    companion object {
        fun from(props: java.util.Properties): Demo? {
            val pairs = props.stringPropertyNames().filter { it.startsWith("demo.replace.") }.sorted().mapNotNull { key ->
                val (from, to) = props.getProperty(key).split("=>", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                Regex("""\b${Regex.escape(from.trim())}\b""", RegexOption.IGNORE_CASE) to to.trim()
            }
            val any = props.stringPropertyNames().any { it.startsWith("demo.") }
            return if (!any) null else Demo(
                pairs,
                props.getProperty("demo.rival")?.trim(),
                props.getProperty("demo.turnos.dias")?.trim()?.toLongOrNull() ?: 0,
                props.getProperty("demo.coche")?.trim(),
                props.getProperty("demo.ficticio")?.trim() == "true",
                props.getProperty("demo.cargando")?.trim() == "true"
            )
        }
    }
}

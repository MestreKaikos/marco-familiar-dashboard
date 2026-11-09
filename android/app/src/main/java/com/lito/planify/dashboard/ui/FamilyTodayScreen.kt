package com.lito.planify.dashboard.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.data.Event
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Días que enseña la franja de la semana (hoy incluido). */
private const val WEEK_DAYS = 7

/**
 * Portada de la familia (calendarios iCal), pensada para verse de lejos:
 * - arriba, un bloque ancho con la hora, la fecha y el tiempo de hoy y de los próximos días, y a la derecha lo
 *   siguiente que toca (con cuánto falta);
 * - en el centro, la semana: hoy y los 6 días siguientes, cada uno con los turnos (Shifter) en una pastilla, todas las
 *   cites familiars i recordatoris amb data límit;
 * - a baix, recordatoris urgents, compra pendent i feines d'en Rai, amb accés al panell adequat.
 */
@Composable
fun FamilyTodayScreen(state: UiState, reminders: FamilyReminders, family: LocalFamilyStore, onOpen: (Page) -> Unit) {
    val now = state.now
    val today = now.toLocalDate()
    val events = state.content?.events.orEmpty()
    val shifts = state.content?.sessions.orEmpty()
    val birthdayNames = birthdaysToday(events, today)
    // A la capçalera, només les cites del proper dia amb activitat (avui si n'hi ha).
    val upcoming = nextCalendarDayEvents(events + shifts, now)
    Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth().weight(0.32f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // Hora, fecha y tiempo en un solo bloque ancho (dos tercios); lo siguiente, a la derecha
            HeaderBlock(state, Modifier.weight(2f).fillMaxHeight())
            UpcomingEventsCard(
                upcoming, now,
                noCalendar = state.token == null && !state.ics,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        if (state.personalCalendars.isNotEmpty() || birthdayNames.isNotEmpty()) {
            CalendarLegend(state.personalCalendars, birthdays = birthdayNames)
        }
        Row(Modifier.fillMaxWidth().weight(0.46f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            (0 until WEEK_DAYS).forEach { i ->
                val day = today.plusDays(i.toLong())
                WeekDay(
                    day, today, now,
                    events.filter { it.at.toLocalDate() == day && !isBirthdayEvent(it) },
                    shifts.filter { it.at.toLocalDate() == day },
                    reminders.dueOn(day).take(2).joinToString(" · ") { it.title }.ifBlank { null },
                    Modifier.weight(1f).fillMaxHeight(),
                    onDinnerClick = { onOpen(Page.Reminders) },
                    footerTitle = "📌 RECORDATORIS",
                    showEmptyFooter = false
                )
            }
        }
        Row(Modifier.fillMaxWidth().weight(0.29f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ReminderTile(reminders, today, Modifier.weight(1.3f).fillMaxHeight()) { onOpen(Page.Reminders) }
            LocalShoppingTile(family, Modifier.weight(1f).fillMaxHeight()) { onOpen(Page.Reminders) }
            LocalRaiTile(family, today, Modifier.weight(1.1f).fillMaxHeight()) { onOpen(Page.Tasks) }
        }
    }

}

// --- Arriba ---

/**
 * Totes les cites de la setmana que encara no han acabat, ordenades per inici.
 * Una cita de tot el dia també és vigent fins a final de dia, i les de diversos
 * dies es conserven mentre siguin actives. S'inclouen calendari i torns.
 */
internal fun nextCalendarEvents(events: List<Event>, now: LocalDateTime): List<Event> {
    val lastDay = now.toLocalDate().plusDays(WEEK_DAYS.toLong())
    return events.asSequence().filterNot(::isBirthdayEvent).filter { event ->
        val end = event.end ?: if (event.all_day) event.at.plusDays(1) else event.at.plusHours(1)
        end.isAfter(now) && event.at.toLocalDate().isBefore(lastDay)
    }.distinctBy { listOf(it.calendar_id.toString(), it.id.toString(), it.date_at, it.title) }
        .sortedWith(compareBy<Event> {
            // Les cites actuals es mostren al davant de les futures.
            if (it.at.isBefore(now)) now else it.at
        }.thenBy { it.at })
        .toList()
}

/**
 * Agrupa totes les cites pendents al primer dia que té activitat. No s'aturen
 * arbitràriament al setè dia: si el proper compromís és més endavant, es mostra.
 * Les cites que ja estan en curs avui també es consideren d'avui.
 * No inclou aniversaris (es mostren a la franja del rellotge).
 */
internal fun nextCalendarDayEvents(events: List<Event>, now: LocalDateTime): List<Event> {
    val today = now.toLocalDate()
    val pending = events.asSequence().filterNot(::isBirthdayEvent).filter { event ->
        val end = event.end ?: if (event.all_day) event.at.plusDays(1) else event.at.plusHours(1)
        end.isAfter(now)
    }.distinctBy { listOf(it.calendar_id.toString(), it.id.toString(), it.date_at, it.title) }.toList()
    val nearest = pending.minOfOrNull { maxOf(it.at.toLocalDate(), today) }
        ?: return emptyList()
    return pending.filter { maxOf(it.at.toLocalDate(), today) == nearest }
        .sortedWith(compareBy<Event> { if (it.at.isBefore(now)) now else it.at }
            .thenBy { it.at })
}

/** Un únic dia amb activitat; les seves targetes reparteixen tota l'alçada disponible. */
@Composable
private fun UpcomingEventsCard(
    events: List<Event>, now: LocalDateTime, noCalendar: Boolean, modifier: Modifier = Modifier
) {
    val today = now.toLocalDate()
    val nearestDay = events.firstOrNull()?.let { maxOf(it.at.toLocalDate(), today) }
    Column(
        modifier.background(Palette.Card, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (events.size == 1) "EL SEGÜENT" else "ELS SEGÜENTS",
                style = Type.Label.copy(color = Palette.Accent, fontSize = 17.sp),
                modifier = Modifier.weight(1f), maxLines = 1
            )
            if (nearestDay != null) Text(
                relativeDay(nearestDay, today) + " · " + events.size +
                    if (events.size == 1) " cita" else " cites",
                style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = 16.sp),
                maxLines = 1
            )
        }
        if (events.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (noCalendar) "Calendaris per configurar" else "No hi ha cites properes",
                    style = Type.Body.copy(color = Palette.InkMuted)
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(if (events.size > 5) 3.dp else 7.dp)
            ) {
                events.forEach { event ->
                    val tint = parseColor(event.calendar_color, Palette.Accent)
                    val ongoing = !event.at.isAfter(now)
                    val whenText = when {
                        event.all_day -> "Tot el dia"
                        ongoing -> "Ara · " + (event.rangeLabel() ?: event.timeLabel())
                        else -> event.rangeLabel() ?: event.timeLabel()
                    }
                    EventTapTarget(event, Modifier.fillMaxWidth().weight(1f)) {
                        BoxWithConstraints(
                            Modifier.fillMaxSize()
                                .background(tint.copy(alpha = 0.13f), RoundedCornerShape(15.dp))
                        ) {
                            // Tipografia segons l'espai REAL de cada subtargeta.
                            val tileHeight = maxHeight
                            val compact = tileHeight < 70.dp
                            val titleSize = when {
                                tileHeight >= 155.dp -> 31
                                tileHeight >= 110.dp -> 27
                                tileHeight >= 85.dp -> 24
                                tileHeight >= 65.dp -> 21
                                tileHeight >= 45.dp -> 18
                                else -> 15
                            }
                            Row(
                                Modifier.fillMaxSize().padding(
                                    horizontal = if (compact) 8.dp else 13.dp,
                                    vertical = if (compact) 3.dp else 7.dp
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    Modifier.width(if (compact) 4.dp else 6.dp)
                                        .fillMaxHeight()
                                        .background(tint, RoundedCornerShape(4.dp))
                                )
                                if (compact) {
                                    Text(
                                        whenText + " · " + event.title,
                                        style = Type.Small.copy(
                                            color = Palette.Ink, fontWeight = FontWeight.SemiBold,
                                            fontSize = titleSize.sp, lineHeight = (titleSize + 3).sp
                                        ),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                                        Text(
                                            event.title,
                                            style = Type.Small.copy(
                                                color = Palette.Ink, fontWeight = FontWeight.SemiBold,
                                                fontSize = titleSize.sp, lineHeight = (titleSize + 4).sp
                                            ),
                                            maxLines = when {
                                                tileHeight >= 155.dp -> 3
                                                tileHeight >= 110.dp -> 2
                                                else -> 1
                                            },
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            whenText,
                                            style = Type.Tiny.copy(
                                                color = Palette.InkSoft,
                                                fontSize = if (tileHeight >= 100.dp) 19.sp else 16.sp
                                            ),
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Centro: la semana ---

/** A Avui no repetim «Sense recordatoris» a cadascun dels set dies buits. */
internal fun shouldShowWeekFooter(showEmptyFooter: Boolean, detail: String?): Boolean =
    showEmptyFooter || !detail.isNullOrBlank()

/** Un día de la semana: cabecera (hoy resaltado), el turno en una pastilla y las citas con su color. */
@Composable
internal fun WeekDay(
    day: LocalDate, today: LocalDate, now: LocalDateTime, events: List<Event>, shifts: List<Event>, dinner: String?, modifier: Modifier,
    onDinnerClick: () -> Unit = {},
    forecast: com.lito.planify.dashboard.data.Forecast? = null,
    small: Boolean = false,
    footerTitle: String = "🍽️ SOPAR",
    showEmptyFooter: Boolean = true
) {
    val isToday = day == today
    Column(modifier.background(if (isToday) Palette.Card else Palette.Card.copy(alpha = 0.6f), RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 14.dp).alpha(if (day < today) 0.55f else 1f)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (isToday) "Avui" else day.dayOfWeek.getDisplayName(TextStyle.SHORT, Spanish).trimEnd('.').capitalized(),
                style = Type.Small.copy(color = if (isToday) Palette.Accent else Palette.InkSoft, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f)
            )
            // Calendario: el mes en el lunes de cada semana y, resaltado, en el día 1 (donde cambia de mes)
            if (small && (day.dayOfWeek == DayOfWeek.MONDAY || day.dayOfMonth == 1)) Text(
                day.month.getDisplayName(TextStyle.SHORT, Spanish).trimEnd('.').removePrefix("d'").removePrefix("d’").removePrefix("de "),
                style = Type.Small.copy(color = if (day.dayOfMonth == 1) Palette.Accent else Palette.InkSoft, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(end = 8.dp, bottom = 3.dp)
            )
            Text("${day.dayOfMonth}", style = Type.Large.copy(color = if (isToday) Palette.Accent else Palette.Ink, fontWeight = FontWeight.SemiBold))
        }
        // Calendario: el tiempo del día (icono, máx/mín y lluvia) bajo la cabecera, si hay previsión
        forecast?.dayIndex(day)?.takeIf { it >= 0 }?.let { k ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                DayWeatherIcon(forecast, day, today, 38)
                Text(
                    "${forecast.daily.temperature_2m_max[k].degrees()} ${forecast.daily.temperature_2m_min[k].degrees()}",
                    style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = 17.sp),
                    modifier = Modifier.padding(start = 6.dp).weight(1f)
                )
                Text("💧${forecast.daily.precipitation_probability_max[k] ?: 0}%", style = Type.Tiny.copy(color = if ((forecast.daily.precipitation_probability_max[k] ?: 0) >= 40) Palette.Rain else Palette.InkMuted, fontSize = 15.sp))
            }
        }
        shifts.forEach { s ->
            val c = parseColor(s.calendar_color)
            EventTapTarget(s, Modifier.padding(top = 8.dp)) {
                Text(
                    "${s.title} ${s.rangeLabel().orEmpty()}",
                    style = Type.Tiny.copy(color = onColor(c), fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().background(c, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
        Hairline(Modifier.padding(vertical = 10.dp))
        Column(Modifier.weight(1f).fadingScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (events.isEmpty()) Text("—", style = Type.Small.copy(color = Palette.InkMuted))
            events.forEach { e -> EventTapTarget(e) { WeekItem(e, past = e.isPast(now), small = small) } }
        }
        if (shouldShowWeekFooter(showEmptyFooter, dinner)) {
            DinnerPill(dinner, Modifier.padding(top = 8.dp), onDinnerClick, small, footerTitle)
        }
    }
}

/** El sopar previst, sempre editable tocant la pastilla de cada dia. */
@Composable
private fun DinnerPill(dinner: String?, modifier: Modifier, onClick: () -> Unit, small: Boolean, footerTitle: String) {
    Column(
        modifier.fillMaxWidth()
            .background(Palette.Warm.copy(alpha = 0.13f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(footerTitle, style = Type.Tiny.copy(color = Palette.InkSoft, fontWeight = FontWeight.SemiBold, fontSize = 15.sp))
        Text(
            dinner ?: if(footerTitle.contains("RECORDATORIS")) "Sense recordatoris" else "＋ Afegir sopar",
            style = Type.Tiny.copy(
                color = if (dinner == null) Palette.InkMuted else Palette.Ink,
                fontSize = if (small) 17.sp else 19.sp,
                lineHeight = if (small) 21.sp else 24.sp
            ),
            maxLines = if (small) 3 else 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WeekItem(e: Event, past: Boolean, small: Boolean = false) {
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).alpha(if (past) 0.4f else 1f)) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(parseColor(e.calendar_color), RoundedCornerShape(3.dp)))
        Column(Modifier.padding(start = 8.dp)) {
            Text(if (e.all_day) "Tot el dia" else e.at.format(HourMinute), style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = if (small) 17.sp else 19.sp, fontWeight = FontWeight.SemiBold))
            Text(e.title, style = Type.Small.copy(color = Palette.Ink, fontSize = if (small) 20.sp else 23.sp, lineHeight = if (small) 25.sp else 28.sp), maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

// --- Abajo: resúmenes ---

@Composable
private fun Tile(title: String, emoji: String, tint: Color, modifier: Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .background(Palette.Card, RoundedCornerShape(28.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = Type.Body)
            Text(title.uppercase(Spanish), style = Type.Label.copy(color = tint, fontSize = 18.sp), modifier = Modifier.padding(start = 10.dp))
        }
        content()
    }
}

/** Mesura la capçalera i una fila real: mostra totes les files completes que hi caben. */
@Composable
private fun HomeSummaryTile(
    title: String, emoji: String, tint: Color, summary: String,
    items: List<HomeSummaryItem>, emptyMessage: String, modifier: Modifier,
    alert: Boolean = false, pointsBadge: Int? = null, onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val accent = if (alert) Palette.Accent else tint
    SubcomposeLayout(
        modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(Palette.Card, tint.copy(alpha = 0.07f).compositeOver(Palette.Card))))
            .border(1.dp, tint.copy(alpha = 0.20f), shape)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) { constraints ->
        val width = constraints.maxWidth
        val childConstraints = constraints.copy(minHeight = 0)
        val header = subcompose("headerMeasure") {
            HomeSummaryHeader(title, emoji, accent, summary, hiddenCount = 0, pointsBadge = pointsBadge, onClick = onClick)
        }.single().measure(childConstraints)
        val headerGap = 6.dp.roundToPx()
        val rowGap = 2.dp.roundToPx()
        val bodyHeight = (constraints.maxHeight - header.height - headerGap).coerceAtLeast(0)
        val bodyConstraints = Constraints(minWidth = width, maxWidth = width, maxHeight = bodyHeight)
        val visibleCount = if (items.isEmpty()) 0 else {
            val sample = subcompose("rowMeasure") {
                HomeSummaryRow(items.first(), accent)
            }.single().measure(childConstraints)
            SummaryTileLayout.visibleItems(bodyHeight, sample.height, rowGap, items.size)
        }
        val finalHeader = subcompose("header") {
            HomeSummaryHeader(title, emoji, accent, summary, hiddenCount = items.size - visibleCount, pointsBadge = pointsBadge, onClick = onClick)
        }.single().measure(childConstraints)
        val body = subcompose("items") {
            if (items.isEmpty()) {
                Box(Modifier.fillMaxSize().clickable(onClick = onClick), contentAlignment = Alignment.CenterStart) {
                    Text(emptyMessage, style = Type.Body.copy(fontSize = 22.sp, lineHeight = 28.sp,
                        color = Palette.InkSoft), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items.take(visibleCount).forEach { HomeSummaryRow(it, accent) }
                }
            }
        }.single().measure(bodyConstraints)
        layout(width, constraints.maxHeight) {
            finalHeader.placeRelative(0, 0)
            body.placeRelative(0, header.height + headerGap)
        }
    }
}

@Composable
private fun HomeSummaryHeader(title: String, emoji: String, tint: Color, summary: String, hiddenCount: Int, pointsBadge: Int?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center) {
            Text(emoji, style = Type.Tiny.copy(fontSize = 22.sp, lineHeight = 26.sp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Small.copy(fontSize = 19.sp, lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold, color = Palette.Ink, letterSpacing = 0.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(summary, style = Type.Tiny.copy(fontSize = 17.sp, lineHeight = 22.sp,
                fontWeight = FontWeight.Normal, color = Palette.InkSoft),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (pointsBadge != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text("⭐ $pointsBadge", style = Type.Small.copy(
                    fontSize = 21.sp, lineHeight = 25.sp,
                    fontWeight = FontWeight.Bold, color = tint),
                    modifier = Modifier.background(tint.copy(alpha = 0.11f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                    maxLines = 1)
                if (hiddenCount > 0) Text("+$hiddenCount més",
                    style = Type.Tiny.copy(color = tint, fontSize = 13.sp, lineHeight = 17.sp),
                    maxLines = 1)
            }
        } else {
            Text(if (hiddenCount > 0) "+$hiddenCount més ›" else "›",
                style = Type.Small.copy(fontSize = if (hiddenCount > 0) 16.sp else 28.sp,
                    lineHeight = 28.sp, fontWeight = FontWeight.Medium, color = tint),
                modifier = Modifier.background(tint.copy(alpha = 0.09f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                maxLines = 1)
        }
    }
}

/**
 * Cada línia pot completar l'element sense abandonar «Avui».
 * Només la capçalera de la targeta obre la pantalla de gestió.
 */
private data class HomeSummaryItem(
    val title: String,
    val detail: String? = null,
    val glyph: String = "☐",
    val onMark: (() -> Unit)? = null,
    val actionLabel: String = "Marcar com fet"
)

@Composable
private fun HomeSummaryRow(item: HomeSummaryItem, tint: Color) {
    val action = item.onMark
    Row(Modifier.fillMaxWidth()
        .background(tint.copy(alpha = 0.055f), RoundedCornerShape(8.dp))
        .then(if (action != null) Modifier.clickable(onClickLabel = item.actionLabel, onClick = action) else Modifier)
        .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(item.glyph, style = Type.Small.copy(fontSize = 22.sp, lineHeight = 28.sp,
            color = tint, fontWeight = FontWeight.Medium))
        Text(item.title, style = Type.Body.copy(fontSize = 21.sp, lineHeight = 28.sp,
            color = Palette.Ink, fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        item.detail?.let {
            Text(it, style = Type.Tiny.copy(color = Palette.InkMuted, fontSize = 15.sp,
                lineHeight = 19.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Compra pendent: els noms dels productes són la informació principal. */
@Composable
private fun LocalShoppingTile(store: LocalFamilyStore, modifier: Modifier, onClick: () -> Unit) {
    val pending = store.groceries.filterNot { it.bought }
    HomeSummaryTile("Compra", "🛒", Palette.Online,
        summary = if (pending.isEmpty()) "No falta res" else "${pending.size} producte${if (pending.size == 1) " pendent" else "s pendents"}",
        items = pending.map { product ->
            HomeSummaryItem(title = product.name, onMark = { store.toggleGrocery(product.id) })
        }, emptyMessage = "Toca per afegir productes",
        modifier = modifier, onClick = onClick)
}

/** Primer les feines accionables; les que esperen validació no han de tapar-les. */
@Composable
private fun LocalRaiTile(store: LocalFamilyStore, today: LocalDate, modifier: Modifier, onClick: () -> Unit) {
    val tasks = store.chores.filter { it.owner == "Rai" }
    val awaiting = tasks.filter { store.status(it, today) == "pending" }
    val remaining = tasks.filter { store.status(it, today) in setOf("todo", "slots") }
    val items = remaining.map { task ->
        if (store.status(task, today) == "slots") {
            // Les feines per franges necessiten escollir una franja a la vista de gestió.
            HomeSummaryItem(title = task.title, detail = "Per franges", glyph = "›",
                onMark = onClick, actionLabel = "Obrir feines per franges")
        } else {
            HomeSummaryItem(title = task.title, detail = summaryShortDate(today),
                onMark = { store.requestDone(task.id, today) })
        }
    } + awaiting.map {
        HomeSummaryItem(title = it.title, detail = "Per validar", glyph = "⏳")
    }
    HomeSummaryTile("Feines d'en Rai", "⭐", parseColor("#7857BC"),
        summary = "${items.size} ${if (items.size == 1) "feina" else "feines"} per fer o validar",
        items = items, emptyMessage = if (tasks.isEmpty()) "Toca per afegir feines" else "Tot fet per avui!",
        modifier = modifier, alert = awaiting.isNotEmpty(), pointsBadge = store.stars, onClick = onClick)
}

/** Data curta que es veu al costat de les feines i recordatoris de la portada. */
internal fun summaryShortDate(day: LocalDate): String =
    "${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("ca-ES")).trimEnd('.')}"

/** Data de venciment compacta, visible però més discreta que el títol. */
internal fun summaryReminderDate(reminder: FamilyReminder, today: LocalDate): String {
    val deadline = reminder.deadline ?: return "Sense data"
    val day = summaryShortDate(deadline)
    return when {
        deadline.isBefore(today) -> "$day · vençut"
        deadline == today -> "Avui · $day"
        deadline == today.plusDays(1) -> "Demà · $day"
        else -> day
    }
}

/** Recordatoris prioritzats: vençuts, avui, futurs i sense data. */
@Composable
private fun ReminderTile(store: FamilyReminders, today: LocalDate, modifier: Modifier, onClick: () -> Unit) {
    val pending = store.pending()
    val overdue = pending.filter { it.deadline?.isBefore(today) == true }
    val todayReminders = pending.filter { it.date == today.toString() }
    val upcoming = pending.filter { it.date.isNotBlank() && it.date > today.toString() }.sortedBy { it.date }
    val withoutDate = pending.filter { it.date.isBlank() }
    val priority = overdue + todayReminders + upcoming + withoutDate
    val summary = when {
        overdue.isNotEmpty() -> "⚠ ${overdue.size} vençut${if (overdue.size == 1) "" else "s"}"
        todayReminders.isNotEmpty() -> "${todayReminders.size} per avui"
        pending.isNotEmpty() -> "${pending.size} pendents"
        else -> "Tot al dia!"
    }
    HomeSummaryTile("Recordatoris", "📌", Palette.Warm, summary,
        items = priority.map { reminder ->
            HomeSummaryItem(title = reminder.title,
                detail = summaryReminderDate(reminder, today),
                onMark = { store.toggle(reminder.id, true) })
        }, emptyMessage = "Toca per afegir-ne un",
        modifier = modifier, alert = overdue.isNotEmpty(), onClick = onClick)
}

@Composable
private fun KidsTile(state: UiState, modifier: Modifier, onClick: () -> Unit) {
    val chores = state.chores
    Tile("Infants", "🐾", Palette.Warm, modifier, onClick) {
        if (chores == null) return@Tile
        val left = chores.pending()
        Text(if (left == 0) "🎉 Tot fet" else "$left ${if (left == 1) "feina" else "feines"} per fer", style = Type.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            chores.ninos.orEmpty().forEach { k ->
                Text(
                    "${k.nombre} ⭐ ${k.estrellas}",
                    style = Type.Small.copy(color = Palette.Ink, fontWeight = FontWeight.Medium),
                    modifier = Modifier.background(parseColor(k.color, Palette.Warm).copy(alpha = 0.14f), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
        }
        chores.pedidas?.size?.takeIf { it > 0 }?.let { Text("🎁 $it ${if (it == 1) "premi" else "premis"} per aprovar", style = Type.Tiny.copy(color = Palette.Accent, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun ShoppingTile(state: UiState, modifier: Modifier, onClick: () -> Unit) {
    val items = state.shopping?.shopping().orEmpty()
    Tile("Compra", "🛒", Palette.Online, modifier, onClick) {
        Text(if (items.isEmpty()) "Res per comprar" else "${items.size} ${if (items.size == 1) "cosa" else "cosas"}", style = Type.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 8.dp))
        Text(items.take(4).joinToString(" · ") { it.producto }, style = Type.Small.copy(color = Palette.InkSoft), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun EnergyTile(state: UiState, modifier: Modifier, onClick: () -> Unit) {
    val now = state.now
    val window = priceWindow(state.energy?.prices.orEmpty(), now)
    val current = window.lastOrNull { !it.time.isAfter(now) }
    val min = window.minOfOrNull { it.eur_kwh } ?: 0.0
    val max = window.maxOfOrNull { it.eur_kwh } ?: 0.0
    val car = state.shopping?.coche
    Tile("Llum i cotxe", "⚡", Amber, modifier, onClick) {
        Row(Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp)) {
            car?.bateria?.let { BatteryGauge(it, car, Modifier.width(170.dp).fillMaxHeight().padding(end = 18.dp)) }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                current?.let {
                    Text(
                        "${euros(it.eur_kwh)} €/kWh",
                        style = Type.Small.copy(color = Color.White, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.background(priceColor(it.eur_kwh, min, max), RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
                if (window.isNotEmpty()) {
                    PriceBars(window, now, min, max, Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp, bottom = 4.dp))
                    PriceHours(window, fontSize = 13)
                }
            }
        }
    }
}

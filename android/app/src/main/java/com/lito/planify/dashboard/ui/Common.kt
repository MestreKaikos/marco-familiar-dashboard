package com.lito.planify.dashboard.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.data.Person
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val Catalan: Locale = Locale.forLanguageTag("ca-ES")
// Àlies temporal mentre acabem de traduir les pantalles heretades.
val Spanish: Locale = Catalan
val HourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun parseColor(hex: String?, fallback: Color = Palette.InkMuted): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

fun String.capitalized() = replaceFirstChar { it.titlecase(Spanish) }

fun LocalDate.weekdayName(style: TextStyle = TextStyle.FULL) = dayOfWeek.getDisplayName(style, Spanish).capitalized()

fun LocalDate.monthWithArticle(): String {
    val name = month.getDisplayName(TextStyle.FULL, Catalan)
    val vowel = name.firstOrNull()?.lowercaseChar()?.let { it in "aeiou" } == true
    return if (vowel) "d'$name" else "de $name"
}

/** Exemple: DIMECRES 21 D'OCTUBRE. */
fun LocalDate.longLabel() = "${dayOfWeek.getDisplayName(TextStyle.FULL, Catalan)} $dayOfMonth ${monthWithArticle()}".uppercase(Catalan)

/** Els 7 dies des de [from]: «13 – 19 d'octubre» o «29 de setembre – 5 d'octubre». */
fun weekRange(from: LocalDate): String {
    val to = from.plusDays(6)
    val year = if (to.year != LocalDate.now().year) " de ${to.year}" else ""
    return if (from.month == to.month) "${from.dayOfMonth} – ${to.dayOfMonth} ${to.monthWithArticle()}$year"
    else "${from.dayOfMonth} ${from.monthWithArticle()} – ${to.dayOfMonth} ${to.monthWithArticle()}$year"
}

/** "MIÉ 14 OCT", como en el widget de la app. */
fun LocalDate.shortLabel() =
    "${dayOfWeek.getDisplayName(TextStyle.SHORT, Spanish).trimEnd('.')} $dayOfMonth ${month.getDisplayName(TextStyle.SHORT, Spanish).trimEnd('.')}".uppercase(Spanish)

/**
 * Tiempo que falta, como en el widget de la app (`DateTimeUtils.getRelativeTime`): "AHORA", "25M", "2H 30M"
 * para hoy; días y horas hasta pasado mañana ("1D 3H"); después, días naturales ("5D") o meses ("1M 5D").
 */
fun relativeBadge(now: LocalDateTime, at: LocalDateTime): String {
    val minutes = kotlin.math.abs(java.time.Duration.between(now, at).toMinutes())
    if (minutes < 1) return "ARA"
    val hours = minutes / 60
    val days = kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())).toInt()
    fun pair(a: Long, unitA: String, b: Long, unitB: String) = if (b > 0) "$a$unitA $b$unitB" else "$a$unitA"
    return when {
        days >= 30 -> pair((days / 30).toLong(), "M", (days % 30).toLong(), "D")
        days > 2 -> "${days}D"
        hours == 0L -> "${minutes}M"
        days > 0 && hours >= 24 -> pair(hours / 24, "D", hours % 24, "H")
        else -> pair(hours, "H", minutes % 60, "M")
    }
}

/** "Hoy", "Mañana" o "Jue 16". */
fun relativeDay(date: LocalDate, today: LocalDate) = when (date) {
    today -> "Avui"
    today.plusDays(1) -> "Demà"
    else -> "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Spanish).capitalized().trimEnd('.')} ${date.dayOfMonth}"
}

/** "en 25 min" / "en 1 h 10 min"; null si falta más de [maxMinutes]. */
fun countdown(from: LocalDateTime, to: LocalDateTime, maxMinutes: Long = 120): String? {
    val minutes = java.time.Duration.between(from, to).toMinutes()
    if (minutes < 0 || minutes > maxMinutes) return null
    return when {
        minutes < 1 -> "ara"
        minutes < 60 -> "en $minutes min"
        minutes % 60 == 0L -> "en ${minutes / 60} h"
        else -> "en ${minutes / 60} h ${minutes % 60} min"
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(Spanish), style = Type.Label, maxLines = 1, modifier = modifier)
}

/**
 * Desplazamiento vertical con los bordes desvanecidos: abajo mientras queda contenido por ver y arriba en
 * cuanto se ha desplazado, para que el texto no se corte en seco contra el borde de la tarjeta.
 */
fun Modifier.fadingScroll(state: ScrollState, edge: Dp = 56.dp): Modifier = this
    // La capa propia permite que DstIn recorte solo el contenido de la lista
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = edge.toPx().coerceAtMost(size.height / 3)
        if (state.canScrollForward) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - fade, endY = size.height),
                topLeft = Offset(0f, size.height - fade),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn
            )
        }
        if (state.canScrollBackward) {
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = fade),
                size = Size(size.width, fade),
                blendMode = BlendMode.DstIn
            )
        }
    }
    .verticalScroll(state)

/**
 * Cabecera de las tarjetas: icono en un cuadro tintado, título y, si hay elementos, el total en una píldora
 * del mismo color. Debajo, una línea que la separa del contenido. [compact]: menor, para apilar tarjetas.
 */
@Composable
fun CardHeader(title: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier, count: Int? = null, compact: Boolean = false) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(if (compact) 36.dp else 44.dp).background(tint.copy(alpha = 0.13f), RoundedCornerShape(if (compact) 12.dp else 14.dp))
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(if (compact) 22.dp else 26.dp))
            }
            // Un poco menor que Type.Large: así caben títulos como «Lista de la compra» en las columnas estrechas
            Text(
                title,
                style = Type.Large.copy(fontSize = if (compact) 26.sp else 30.sp, lineHeight = if (compact) 32.sp else 38.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = if (compact) 12.dp else 14.dp).weight(1f)
            )
            if (count != null && count > 0) {
                Text(
                    "$count",
                    style = (if (compact) Type.Tiny else Type.Small).copy(color = tint, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.background(tint.copy(alpha = 0.13f), CircleShape).padding(horizontal = if (compact) 12.dp else 16.dp, vertical = 4.dp)
                )
            }
        }
        Hairline(Modifier.padding(top = if (compact) 12.dp else 18.dp))
    }
}

/**
 * Estado vacío de una tarjeta o pantalla: el icono dentro de dos círculos concéntricos del color de la tarjeta,
 * un título y una línea de texto, centrados en el espacio que se le dé.
 */
@Composable
fun EmptyState(icon: ImageVector, tint: Color, title: String, subtitle: String, modifier: Modifier = Modifier, large: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(if (large) 168.dp else 116.dp).background(tint.copy(alpha = 0.08f), CircleShape)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(if (large) 116.dp else 80.dp).background(tint.copy(alpha = 0.16f), CircleShape)
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(if (large) 60.dp else 42.dp))
            }
        }
        Text(
            title,
            style = (if (large) Type.Title else Type.Large).copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = if (large) 28.dp else 20.dp)
        )
        Text(
            subtitle,
            style = (if (large) Type.Body else Type.Small).copy(color = Palette.InkMuted),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(2.dp).background(Palette.Line))
}

/** Tarjeta de pantalla completa; [compact], con menos margen, para apilar varias en una columna. */
@Composable
fun Panel(modifier: Modifier = Modifier, compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .background(Palette.Card, RoundedCornerShape(if (compact) 30.dp else 36.dp))
            .padding(horizontal = if (compact) 32.dp else 40.dp, vertical = if (compact) 22.dp else 34.dp),
        content = content
    )
}

@Composable
fun Dot(color: Color, size: Int = 14) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}

@Composable
fun PersonChips(persons: List<Person>?, modifier: Modifier = Modifier) {
    if (persons.isNullOrEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        persons.forEach { person ->
            val color = parseColor(person.color)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .background(color.copy(alpha = 0.14f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Dot(color, 12)
                Text(person.name, style = Type.Tiny.copy(color = Palette.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Iniciales en círculos del color de cada persona: para filas donde no caben los nombres. */
@Composable
fun PersonInitials(persons: List<Person>?, modifier: Modifier = Modifier, size: Int = 36) {
    if (persons.isNullOrEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp), modifier = modifier) {
        persons.forEach { person ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size.dp)
                    .border(3.dp, Palette.Background, CircleShape)
                    .background(parseColor(person.color), CircleShape)
            ) {
                Text(person.name.take(1).uppercase(Spanish), style = Type.Tiny.copy(color = Color.White, fontSize = Type.Tiny.fontSize * (size / 40f)))
            }
        }
    }
}

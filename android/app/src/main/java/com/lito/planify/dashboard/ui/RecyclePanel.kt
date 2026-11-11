package com.lito.planify.dashboard.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** Calendari setmanal del document municipal de recollida porta a porta. */
internal data class RecyclePickup(val key: String, val labels: List<String>, val isGlass: Boolean = false)

internal fun recyclePickup(now: LocalDateTime): RecyclePickup? {
    val date = now.toLocalDate()
    // El dijous al vespre continua sent orgànica i envasos; el vidre es treu divendres al matí.
    if (date.dayOfWeek == DayOfWeek.THURSDAY && now.hour >= 20)
        return RecyclePickup(date.plusDays(1).toString() + "-vidre", listOf("🟢 Vidre"), true)
    if (date.dayOfWeek == DayOfWeek.FRIDAY && now.hour < 8)
        return RecyclePickup(date.toString() + "-vidre", listOf("🟢 Vidre"), true)
    val items = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> listOf("⚫ Resta", "🔵 Paper i cartró", "🩷 Tèxtil sanitari")
        DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY ->
            listOf("🟤 Orgànica", "🟡 Envasos", "🩷 Tèxtil sanitari")
        DayOfWeek.FRIDAY -> listOf("🟤 Orgànica", "🔵 Paper i cartró", "🩷 Tèxtil sanitari")
        else -> emptyList()
    }
    return if (items.isEmpty()) null else RecyclePickup(date.toString() + "-vespre", items)
}

internal fun recycleVisible(now: LocalDateTime, pickup: RecyclePickup): Boolean =
    if (pickup.isGlass) true else now.hour >= 17

internal fun recycleAttention(now: LocalDateTime, pickup: RecyclePickup): Boolean =
    if (pickup.isGlass) now.dayOfWeek == DayOfWeek.FRIDAY && now.hour >= 7
    else now.hour >= 19

/** Desat local: una confirmació no es reutilitza en recollides futures. */
internal fun recycleIsDone(context: Context, pickup: RecyclePickup): Boolean =
    context.getSharedPreferences("marc_reciclatge", Context.MODE_PRIVATE).getBoolean(pickup.key, false)

internal fun recycleSetDone(context: Context, pickup: RecyclePickup, done: Boolean) {
    context.getSharedPreferences("marc_reciclatge", Context.MODE_PRIVATE).edit()
        .putBoolean(pickup.key, done).apply()
}

@Composable
internal fun RecyclePanel(pickup: RecyclePickup?, done: Boolean, onDone: (Boolean) -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(Modifier.width(390.dp).background(Palette.Card, shape)
        .border(2.dp, Palette.CardBorder, shape).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("♻ Porta a porta", style = Type.Body.copy(fontWeight = FontWeight.Bold))
            Text("✕", style = Type.Body, modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
        }
        if (pickup == null) {
            Text("Avui no hi ha cap recollida prevista.", style = Type.Small)
        } else {
            Text(if (pickup.isGlass) "Vidre · divendres abans de les 8 h" else "Treure de 19 a 20 h", style = Type.Small)
            pickup.labels.forEach { Text(it, style = Type.Small.copy(color = Palette.Ink)) }
            val tint = if (done) Palette.Online else Palette.Accent
            Box(Modifier.fillMaxWidth().background(tint, RoundedCornerShape(16.dp))
                .clickable { onDone(!done) }.padding(14.dp), contentAlignment = Alignment.Center) {
                Text(if (done) "✓ Fet · desfer" else "✓ Ja ho hem tret",
                    style = Type.Small.copy(color = Color.White, fontWeight = FontWeight.Bold))
            }
        }
    }
}

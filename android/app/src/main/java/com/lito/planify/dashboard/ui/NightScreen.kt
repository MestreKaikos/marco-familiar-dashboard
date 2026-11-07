package com.lito.planify.dashboard.ui

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.ui.theme.Geist
import com.lito.planify.dashboard.ui.theme.GeistMono
import java.time.LocalDateTime
import kotlin.math.cos
import kotlin.math.sin

/** Sense segons ni animació contínua. Un toc conserva el retorn temporal habitual a Avui. */
@Composable
fun NightScreen(state: UiState, config: NightClockConfig, onWake: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClickLabel = "Mostrar Avui", onClick = onWake)) {
        NightClock(state.now, config, Modifier.fillMaxSize())
    }
}

@Composable
fun NightClock(now: LocalDateTime, config: NightClockConfig, modifier: Modifier = Modifier, drift: Boolean = true) {
    val c = config.validated()
    val color = Color(android.graphics.Color.parseColor(c.colorHex)).copy(alpha = c.intensity)
    val font = when (c.font) {
        "classic" -> FontFamily.Serif
        "mono" -> GeistMono
        "compact" -> remember { FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL)) }
        else -> Geist
    }
    val weight = when (c.weight) { "bold" -> FontWeight.Bold; "normal" -> FontWeight.Normal; else -> FontWeight.Light }
    // Petits desplaçaments cada cinc minuts, sempre dins l'àrea reservada.
    val slot = (now.hour * 60 + now.minute) / 5
    val dx = if (drift) ((slot % 5) - 2) * 4 else 0
    val dy = if (drift) (((slot / 5) % 5) - 2) * 4 else 0
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val width = (maxWidth.value - 40f).coerceAtLeast(1f)
        val height = (maxHeight.value - 40f).coerceAtLeast(1f)
        val clockModifier = Modifier.offset(dx.dp, dy.dp)
        if (c.layout == "analog") {
            val diameter = minOf(width, height) * c.scale
            Canvas(clockModifier.size(diameter.dp)) {
                val radius = size.minDimension / 2
                val center = Offset(size.width / 2, size.height / 2)
                fun point(angle: Double, length: Float) = center + Offset(sin(angle).toFloat() * length, -cos(angle).toFloat() * length)
                for (i in 0..3) {
                    val angle = i * Math.PI / 2
                    drawLine(color, point(angle, radius * .90f), point(angle, radius * .98f), strokeWidth = radius * .015f, cap = StrokeCap.Round)
                }
                val thickness = radius * when(c.weight) { "bold" -> .045f; "normal" -> .026f; else -> .014f }
                drawLine(color, center, point((now.hour % 12 + now.minute / 60.0) * Math.PI / 6, radius * .52f), thickness, StrokeCap.Round)
                drawLine(color, center, point(now.minute * Math.PI / 30, radius * .78f), thickness * .7f, StrokeCap.Round)
                drawCircle(color, thickness, center)
            }
        } else {
            // La mida es calcula amb l'espai disponible, inclosa la previsualització i pantalles petites.
            val size = if (c.layout == "stacked") minOf(width / 1.65f, height / 2.55f) * c.scale
                else minOf(width / 3.9f, height / 1.35f) * c.scale
            val text = if (c.layout == "stacked") "%02d\n%02d".format(now.hour, now.minute) else now.format(HourMinute)
            Text(text, modifier = clockModifier, color = color,
                style = TextStyle(fontFamily = font, fontWeight = weight, fontSize = size.sp,
                    lineHeight = (size * 1.08f).sp, fontFeatureSettings = "tnum", textAlign = TextAlign.Center),
                softWrap = false, maxLines = if (c.layout == "stacked") 2 else 1)
        }
    }
}

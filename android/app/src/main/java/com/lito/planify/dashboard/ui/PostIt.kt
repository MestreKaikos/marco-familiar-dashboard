package com.lito.planify.dashboard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.json.JSONArray
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal data class FamilyPostIt(val id: String, val text: String, val showAt: Long, val runs: JSONArray? = null, val drawing: JSONArray? = null)

@Composable
internal fun PostItDialog(note: FamilyPostIt, onDismiss: () -> Unit) {
    val formattedText = remember(note) {
        buildAnnotatedString {
            append(note.text)
            val runs = note.runs
            for (index in 0 until (runs?.length() ?: 0)) {
                val run = runs?.optJSONObject(index) ?: continue
                val start = run.optInt("start", -1)
                val end = run.optInt("end", -1)
                if (start < 0 || end <= start || end > note.text.length) continue
                val font = when (run.optString("font")) {
                    "sans" -> FontFamily.SansSerif
                    "serif" -> FontFamily.Serif
                    else -> FontFamily.Cursive
                }
                val color = when (run.optString("color")) {
                    "red" -> Color(0xFF9E2424)
                    "blue" -> Color(0xFF18519B)
                    "green" -> Color(0xFF216338)
                    "purple" -> Color(0xFF66378C)
                    else -> Color(0xFF302811)
                }
                addStyle(SpanStyle(
                    color = color,
                    fontFamily = font,
                    fontStyle = if (run.optBoolean("italic")) FontStyle.Italic else FontStyle.Normal,
                    textDecoration = if (run.optBoolean("underline")) TextDecoration.Underline else TextDecoration.None,
                    fontWeight = if (run.optBoolean("bold")) FontWeight.Bold else FontWeight.Normal
                ), start, end)
            }
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier.fillMaxWidth(0.84f)
                .background(Color(0xFFFFEFA6), RoundedCornerShape(24.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (note.text.isNotBlank()) Text(
                    formattedText,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF302811),
                    fontFamily = FontFamily.Cursive,
                    fontSize = 48.sp,
                    lineHeight = 56.sp,
                    textAlign = TextAlign.Center
                )
                if (note.drawing != null) PostItDrawing(note.drawing)
            }
            Button(onClick = onDismiss) { Text("Entesos", fontSize = 24.sp) }
        }
    }
}


private data class PostItStroke(val color: Color, val width: Float, val points: List<Offset>)

@Composable
private fun PostItDrawing(drawing: JSONArray) {
    val strokes = remember(drawing) {
        (0 until minOf(drawing.length(), 32)).mapNotNull { index ->
            val stroke = drawing.optJSONObject(index) ?: return@mapNotNull null
            val color = stroke.optString("color").takeIf { it.length == 6 }
                ?.toIntOrNull(16) ?: return@mapNotNull null
            val points = stroke.optString("points").take(4800).chunked(4).mapNotNull points@ { point ->
                val xy = point.takeIf { it.length == 4 }?.toIntOrNull(16) ?: return@points null
                Offset((xy shr 8) / 255f, (xy and 255) / 255f)
            }
            if (points.isEmpty()) return@mapNotNull null
            PostItStroke(Color(0xFF000000.toInt() or color), stroke.optInt("width").coerceIn(1, 12).toFloat(), points)
        }
    }
    Canvas(Modifier.fillMaxWidth(0.65f).aspectRatio(384f / 224f)
        .background(Color.White, RoundedCornerShape(12.dp))) {
        for (stroke in strokes) {
            val width = stroke.width * size.width / 384f
            val first = stroke.points.first()
            if (stroke.points.size == 1) {
                drawCircle(stroke.color, width / 2, Offset(first.x * size.width, first.y * size.height))
            } else {
                val path = Path().apply {
                    moveTo(first.x * size.width, first.y * size.height)
                    for (point in stroke.points.drop(1)) lineTo(point.x * size.width, point.y * size.height)
                }
                drawPath(path, stroke.color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

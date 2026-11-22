package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
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

internal data class FamilyPostIt(val id: String, val text: String, val showAt: Long, val runs: JSONArray? = null)

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
            Text(
                formattedText,
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                color = Color(0xFF302811),
                fontFamily = FontFamily.Cursive,
                fontSize = 48.sp,
                lineHeight = 56.sp,
                textAlign = TextAlign.Center
            )
            Button(onClick = onDismiss) { Text("Entesos", fontSize = 24.sp) }
        }
    }
}

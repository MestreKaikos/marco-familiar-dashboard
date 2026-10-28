package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.data.CalendarColors
import com.lito.planify.dashboard.data.PersonalCalendar
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type

/** Paleta accessible al tacte: no requereix escriure codis hexadecimals. */
@Composable
fun CalendarColorPicker(current: String?, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        CalendarColors.options.forEach { swatch ->
            val hex = swatch.hex
            val selected = hex.equals(current, ignoreCase = true)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(if (selected) 39.dp else 34.dp)
                        .background(if (selected) Palette.Ink else Color.Transparent, CircleShape)
                        .padding(4.dp)
                        .background(parseColor(hex, Palette.Accent), CircleShape)
                        .clickable { onSelect(hex) }
                )
                if (selected) Text("✓", style = Type.Tiny.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

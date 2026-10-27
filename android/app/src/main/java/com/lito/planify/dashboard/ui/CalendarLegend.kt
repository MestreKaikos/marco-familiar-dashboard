package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.data.CalendarColors
import com.lito.planify.dashboard.data.PersonalCalendar
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type

/**
 * Llegenda compacta dels calendaris personals. Les etiquetes provenen de la
 * configuració local, sense mostrar mai els enllaços privats iCal.
 */
@Composable
fun CalendarLegend(calendars: List<PersonalCalendar>, modifier: Modifier = Modifier) {
    if (calendars.isEmpty()) return
    Row(
        modifier.fillMaxWidth()
            .background(Palette.Card.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(23.dp)
    ) {
        Text("CALENDARIS", style = Type.Tiny.copy(color = Palette.InkMuted, fontSize = 16.sp))
        calendars.take(5).forEach { calendar ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    Modifier.size(16.dp).background(
                        parseColor(CalendarColors.normalize(calendar.color), Palette.InkMuted),
                        CircleShape
                    )
                )
                Text(
                    calendar.name,
                    style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = 19.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

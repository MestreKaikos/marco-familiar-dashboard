package com.lito.planify.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lito.planify.dashboard.data.CalendarColors
import com.lito.planify.dashboard.data.PersonalCalendar
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type

/**
 * Franja sota el rellotge: 50 % llegenda de calendaris i 50 % aniversaris d'avui.
 * Totes dues meitats es poden desplaçar horitzontalment sense retallar la resta
 * de la pantalla, incloent els casos amb molts calendaris o aniversaris.
 */
@Composable
fun CalendarLegend(
    calendars: List<PersonalCalendar>,
    modifier: Modifier = Modifier,
    birthdays: List<String> = emptyList()
) {
    if (calendars.isEmpty() && birthdays.isEmpty()) return
    Row(
        modifier.fillMaxWidth()
            .background(Palette.Card.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState())
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Text(
                "CALENDARIS",
                style = Type.Tiny.copy(color = Palette.InkMuted, fontSize = 16.sp),
                maxLines = 1
            )
            calendars.forEach { calendar ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        Modifier.size(14.dp).background(
                            parseColor(CalendarColors.normalize(calendar.color), Palette.InkMuted),
                            CircleShape
                        )
                    )
                    Text(
                        calendar.name,
                        style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = 18.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 210.dp)
                    )
                }
            }
        }

        // Separador real de la barra, no dibuixat per sobre de les etiquetes.
        Box(Modifier.width(2.dp).height(31.dp).background(Palette.Line))

        Row(
            Modifier.weight(1f)
                .background(Palette.Warm.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 13.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("🎂", style = Type.Small.copy(fontSize = 22.sp), maxLines = 1)
            if (birthdays.isEmpty()) {
                Text(
                    "Avui no hi ha aniversaris",
                    style = Type.Tiny.copy(color = Palette.InkMuted, fontSize = 18.sp),
                    maxLines = 1
                )
            } else {
                Text(
                    if (birthdays.size == 1) "Avui és l’aniversari de:" else "Avui són els aniversaris de:",
                    style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = 17.sp),
                    maxLines = 1
                )
                Text(
                    birthdays.joinToString(" · "),
                    style = Type.Tiny.copy(
                        color = Palette.Ink, fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

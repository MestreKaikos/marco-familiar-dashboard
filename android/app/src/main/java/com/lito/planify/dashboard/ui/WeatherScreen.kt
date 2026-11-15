package com.lito.planify.dashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Duration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.UiState
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * El tiempo a pantalla completa, en dos páginas que se deslizan: esta semana (24 horas, de 4 antes de la
 * actual a 19 después, con iconos, y 7 días) y la siguiente (gráfica diaria de los días 8 a 14).
 */
@Composable
fun WeatherScreen(state: UiState) {
    val forecast = state.forecast
    val location = state.location
    ScreenPadding {
        if (forecast == null || location == null) {
            Text(if (location == null) "Tria una ubicació a Opcions" else "Carregant la previsió…", style = Type.Large.copy(color = Palette.InkMuted))
            return@ScreenPadding
        }
        val pager = rememberPagerState { 3 }
        val scope = rememberCoroutineScope()
        val today = state.now.toLocalDate()
        var radarOpen by remember { mutableStateOf(false) }
        val radarContext = LocalContext.current
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), pageSpacing = 40.dp) { page ->
                if (page == 0) {
                    // Al pasar del último día de la gráfica sigue la semana siguiente, y antes de la semana vuelve al día
                    Box(Modifier.fillMaxSize()) {
                        WeatherCard(forecast, location.name, state.now, days = 7, modifier = Modifier.fillMaxSize()) { edge ->
                            if (edge > 0) scope.launch { pager.animateScrollToPage(1) }
                        }
                        TextButton(
                            onClick = { radarOpen = true },
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 14.dp)
                                .background(Palette.Card.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
                        ) {
                            Text("◉ Radar", style = Type.Small.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                } else if (page == 1) {
                    WeekWeatherCard(forecast, location.name, "La setmana vinent", today.plusDays(7), state.now, Modifier.fillMaxSize()) { edge ->
                        if (edge < 0) scope.launch { pager.animateScrollToPage(0) }
                    }
                } else {
                    AstronomyCard(forecast, location.name, today, Modifier.fillMaxSize())
                }
            }
            if (radarOpen) RadarDialog { radarOpen = false }
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
            ) {
                repeat(3) { Dot(if (pager.currentPage == it) Palette.Ink else Palette.Line, 14) }
            }
        }
    }
}

/**
 * Tercera pàgina d'El temps: dades del sol per als propers dies i fases lunars.
 * Manté intactes els gràfics de les dues pàgines meteorològiques existents.
 */
@Composable
private fun AstronomyCard(
    forecast: com.lito.planify.dashboard.data.Forecast,
    location: String,
    today: LocalDate,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.background(Palette.Card, RoundedCornerShape(32.dp))
            .padding(horizontal = 36.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text("Sol i lluna", style = Type.Title.copy(fontWeight = FontWeight.SemiBold))
        Text(location, style = Type.Body.copy(color = Palette.InkMuted))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            AstronomyPanel("☀  Sol", "Avui", Modifier.weight(1f)) {
                Text("Sortida  ${forecast.sunrise(today) ?: "—"}", style = Type.Large)
                Text("Posta  ${forecast.sunset(today) ?: "—"}", style = Type.Large)
                val sunrise = forecast.sunrise(today)
                val sunset = forecast.sunset(today)
                if (sunrise != null && sunset != null) {
                    val minutes = Duration.between(
                        java.time.LocalTime.parse(sunrise),
                        java.time.LocalTime.parse(sunset)
                    ).toMinutes()
                    if (minutes >= 0) Text(
                        "Hores de llum  ${minutes / 60} h ${(minutes % 60).toString().padStart(2, '0')} min",
                        style = Type.Body.copy(color = Palette.InkSoft)
                    )
                }
            }
            AstronomyPanel("☾  Lluna", "Fase aproximada", Modifier.weight(1f)) {
                Text(moonPhaseLabel(today), style = Type.Large)
                Text("La fase és una estimació astronòmica. Les hores de sortida i posta de la lluna encara no estan disponibles.",
                    style = Type.Small.copy(color = Palette.InkMuted))
            }
        }
        Text("Propers dies", style = Type.Large.copy(fontWeight = FontWeight.SemiBold))
        (0 until 7).forEach { offset ->
            val date = today.plusDays(offset.toLong())
            if (forecast.dayIndex(date) >= 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (offset == 0) "Avui" else date.dayOfWeek.getDisplayName(
                        java.time.format.TextStyle.FULL, Catalan
                    ).replaceFirstChar { it.uppercaseChar() }, style = Type.Body, modifier = Modifier.weight(1f))
                    Text("☀  ${forecast.sunrise(date) ?: "—"}  ·  ${forecast.sunset(date) ?: "—"}",
                        style = Type.Body, modifier = Modifier.weight(1.5f))
                    Text("☾  ${moonPhaseLabel(date)}", style = Type.Body, modifier = Modifier.weight(1.2f))
                }
                Hairline()
            }
        }
    }
}

@Composable
private fun AstronomyPanel(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(modifier.background(Palette.Highlight, RoundedCornerShape(22.dp))
        .padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = Type.Large.copy(fontWeight = FontWeight.SemiBold))
        Text(subtitle, style = Type.Small.copy(color = Palette.InkMuted))
        content()
    }
}

/** Radar integrat: mapa OSM i capes de precipitació RainViewer; no abandona l'APK. */
@Composable
private fun RadarDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.94f).fillMaxSize(0.92f)
                .background(Palette.Card, RoundedCornerShape(26.dp)).padding(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Radar de Catalunya", style = Type.Title)
                TextButton(onClick = onDismiss) { Text("TANCAR") }
            }
            Text("Precipitació observada · RainViewer (no és una predicció)", style = Type.Small)
            AndroidView(factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            android.util.Log.e("MarcRadar", message.message())
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            android.util.Log.e("MarcRadar", "WebView error: "+error?.description)
                        }
                    }
                    loadDataWithBaseURL("https://api.rainviewer.com/", RADAR_HTML, "text/html", "UTF-8", null)
                }
            }, modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}

private val RADAR_HTML = """<!doctype html><html lang="ca"><head><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no"/><link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/><style>html,body,#map{height:100%;margin:0;background:#e6edf2}#msg{position:absolute;z-index:9999;top:10px;left:10px;background:white;padding:10px;border-radius:8px;font:16px sans-serif}#controls{position:absolute;z-index:9999;bottom:14px;left:14px;background:white;padding:10px;border-radius:8px;font:17px sans-serif}button{font-size:19px;padding:8px}</style></head><body><div id="map"></div><div id="msg">Carregant el radar…</div><div id="controls"><button id="play">▶</button> <span id="time"></span> · Dades: RainViewer</div><script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script><script>(async function(){const msg=document.getElementById('msg');try{if(!window.L)throw Error('No s’ha carregat el mapa');const map=L.map('map',{zoomControl:true}).setView([41.75,1.65],8);L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:18,attribution:'© OpenStreetMap'}).addTo(map);const res=await fetch('https://api.rainviewer.com/public/weather-maps.json');if(!res.ok)throw Error('Error HTTP '+res.status);const data=await res.json();const frames=(data.radar&&data.radar.past||[]).slice(-6);if(!frames.length)throw Error('No hi ha imatges de radar');let index=frames.length-1,overlay=null,playing=false,timer=null;function show(){if(overlay)map.removeLayer(overlay);overlay=L.tileLayer(data.host+frames[index].path+'/256/{z}/{x}/{y}/2/1_1.png',{opacity:.75,maxNativeZoom:7,maxZoom:18,attribution:'Radar: RainViewer'}).addTo(map);document.getElementById('time').textContent=new Date(frames[index].time*1000).toLocaleTimeString('ca-ES',{hour:'2-digit',minute:'2-digit'});}show();msg.style.display='none';const button=document.getElementById('play');button.onclick=function(){playing=!playing;button.textContent=playing?'Ⅱ':'▶';if(timer)clearInterval(timer);if(playing)timer=setInterval(()=>{index=(index+1)%frames.length;show()},900);};}catch(e){msg.textContent='Radar no disponible: '+e.message;}})();</script></body></html>"""

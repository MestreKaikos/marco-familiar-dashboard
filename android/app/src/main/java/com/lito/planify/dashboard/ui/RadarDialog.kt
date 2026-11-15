package com.lito.planify.dashboard.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val MAP_WIDTH = 340
private const val MAP_HEIGHT = 336

@Composable
internal fun RadarDialog(onDismiss: () -> Unit) {
    var radar by remember { mutableStateOf<RadarSequence?>(null) }
    var frameIndex by remember { mutableIntStateOf(0) }
    var animationStarted by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        failed = false
        playing = false
        animationStarted = false
        try {
            val sequence = MeteocatRadar.load()
            frameIndex = sequence.frames.indexOfLast { !it.forecast }
            radar = sequence
        } catch (error: IOException) {
            Log.w("MarcRadar", "No s’ha pogut carregar el radar de Meteocat", error)
            failed = true
        } finally {
            loading = false
        }
    }
    val frames = radar?.frames.orEmpty()
    val frame = frames.getOrNull(frameIndex)
    LaunchedEffect(playing, frames) {
        if (playing && frames.size > 1) {
            while (true) {
                delay(if (frameIndex == frames.lastIndex) 1400L else 700L)
                frameIndex = (frameIndex + 1) % frames.size
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val ratio = MAP_WIDTH.toFloat() / MAP_HEIGHT
            val mapWidth = minOf(maxWidth * 0.96f - 16.dp, (maxHeight - 88.dp) * ratio)
            Column(
                Modifier.width(mapWidth + 16.dp)
                    .background(Palette.Card, RoundedCornerShape(18.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(ratio),
                    contentAlignment = Alignment.Center
                ) {
                    val map = radar?.map
                    if (map != null && frame != null) {
                        val mapSize = Modifier.fillMaxSize()
                        Image(map, contentDescription = "Mapa de Catalunya", modifier = mapSize)
                        Image(frame.image, contentDescription = null, modifier = mapSize, alpha = 0.85f)
                    } else if (loading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text("Carregant el radar…", Modifier.padding(top = 12.dp))
                        }
                    } else {
                        Text("Radar no disponible. Comprova la connexió i torna-ho a provar.")
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("METEOCAT", style = Type.Small)
                    Text(
                        when {
                            loading && frame != null -> "Actualitzant…"
                            failed && frame != null -> "${frame.label} · Sense actualitzar"
                            frame?.forecast == true -> "${frame.label} · Predicció"
                            frame != null -> "${frame.label} · Observació"
                            else -> ""
                        },
                        color = if (frame?.forecast == true) Color(0xFF9A4700) else Palette.Ink,
                        style = Type.Small
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(enabled = frames.size > 1 && !loading, onClick = {
                        if (!playing && !animationStarted) frameIndex = 0
                        animationStarted = true
                        playing = !playing
                    }) { Text(if (playing) "PAUSA" else "ANIMA") }
                    TextButton(enabled = !loading, onClick = { reload++ }) {
                        Text(if (failed) "REINTENTA" else "ACTUALITZA")
                    }
                    TextButton(onClick = onDismiss) { Text("TANCAR") }
                }
            }
        }
    }
}

private data class RadarFrame(val label: String, val image: ImageBitmap, val forecast: Boolean)
private data class RadarSequence(val map: ImageBitmap, val frames: List<RadarFrame>)

private object MeteocatRadar {
    private const val TILES = "https://static-m.meteo.cat/tiles"
    private const val TILE_SIZE = 256
    private val timestamp = Regex("""dataDarreraRadar\s*:\s*['"](\d{2}/\d{2}/\d{4} \d{2}:\d{2})Z['"]""")
    private val forecastTimestamp = Regex("""dataDarreraAdveccio\s*:\s*['"]([^'"]+)['"]""")
    private val pageFormat = DateTimeFormatter.ofPattern("MM/dd/uuuu HH:mm", Locale.ROOT)
    private val tileDate = DateTimeFormatter.ofPattern("uuuu/MM/dd", Locale.ROOT).withZone(ZoneOffset.UTC)
    private val tileTime = DateTimeFormatter.ofPattern("HH/mm", Locale.ROOT).withZone(ZoneOffset.UTC)
    private val labelFormat = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ROOT)
        .withZone(ZoneId.of("Europe/Madrid"))
    private val client = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS).build()

    suspend fun load(): RadarSequence = withContext(Dispatchers.IO) {
        // Meteocat publica per separat l'hora de l'observació i la base de la predicció.
        val (observation, forecastBase) = request("https://www.meteo.cat/observacions/radar") { body ->
            val page = body.string()
            val stamp = timestamp.find(page)?.groupValues?.get(1)
                ?: throw IOException("Meteocat no ha publicat l'hora del radar")
            val observed = try {
                LocalDateTime.parse(stamp, pageFormat).toInstant(ZoneOffset.UTC)
            } catch (error: DateTimeParseException) {
                throw IOException("Hora del radar de Meteocat no vàlida", error)
            }
            val base = forecastTimestamp.find(page)?.groupValues?.get(1)?.let {
                try {
                    OffsetDateTime.parse(it).toInstant()
                } catch (error: DateTimeParseException) {
                    Log.w("MarcRadar", "Hora de la predicció de Meteocat no vàlida", error)
                    null
                }
            }
            observed to base
        }
        val map = async { mosaic("$TILES/fons/GoogleMapsCompatible/07") }
        val observedFrames = (5 downTo 0).map { step ->
            val time = observation.minus(step * 6L, ChronoUnit.MINUTES)
            async { loadFrame(time, "$TILES/radar/${tileDate.format(time)}/${tileTime.format(time)}/07", false) }
        }
        val predictedFrames = forecastBase?.let { base ->
            (1..5).map { step -> observation.plus(step * 6L, ChronoUnit.MINUTES) }
                .filter { it > base && it <= base.plus(60, ChronoUnit.MINUTES) }
                .map { time ->
                    val dates = "${tileDate.format(base)}/${tileDate.format(time)}"
                    val times = "${tileTime.format(base)}/${tileTime.format(time)}"
                    async { loadFrame(time, "$TILES/adveccio/$dates/$times/07", true) }
                }
        }.orEmpty()
        val observations = observedFrames.awaitAll().filterNotNull()
        if (observations.isEmpty()) throw IOException("Cap fotograma complet del radar de Meteocat")
        RadarSequence(map.await(), observations + predictedFrames.awaitAll().filterNotNull())
    }

    private suspend fun loadFrame(time: Instant, url: String, forecast: Boolean): RadarFrame? {
        return try {
            RadarFrame(labelFormat.format(time), mosaic(url), forecast)
        } catch (error: IOException) {
            Log.w("MarcRadar", "Fotograma de radar omès: $time UTC", error)
            null
        }
    }

    private suspend fun mosaic(base: String): ImageBitmap = coroutineScope {
        // Zoom 7, x 64–65 i files TMS 80–79 (de nord a sud), iguals per al fons i el radar.
        val paths = listOf("064/080", "065/080", "064/079", "065/079")
        val tiles = paths.map { path ->
            async {
                val (x, y) = path.split('/')
                request("$base/000/000/$x/000/000/$y.png") { body ->
                    BitmapFactory.decodeStream(body.byteStream())
                        ?.takeIf { it.width == TILE_SIZE && it.height == TILE_SIZE }
                        ?: throw IOException("Imatge de radar no vàlida")
                }
            }
        }.awaitAll()
        val image = Bitmap.createBitmap(TILE_SIZE * 2, TILE_SIZE * 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val paint = Paint()
        tiles.forEachIndexed { index, tile ->
            canvas.drawBitmap(tile, (index % 2 * TILE_SIZE).toFloat(), (index / 2 * TILE_SIZE).toFloat(), paint)
            tile.recycle()
        }
        // Mateix enquadrament per al fons i per a cada fotograma.
        val catalonia = Bitmap.createBitmap(image, 0, 0, MAP_WIDTH, MAP_HEIGHT)
        image.recycle()
        catalonia.asImageBitmap()
    }

    private fun <T> request(url: String, read: (okhttp3.ResponseBody) -> T): T {
        return client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} de ${response.request.url}")
            read(response.body ?: throw IOException("Resposta buida de Meteocat"))
        }
    }
}

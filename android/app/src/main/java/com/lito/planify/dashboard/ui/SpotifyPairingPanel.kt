package com.lito.planify.dashboard.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import kotlinx.coroutines.delay

/** La clau d'accés a Spotify mai no és dins del QR. */
@Composable
internal fun SpotifyPairingPanel(controller: SpotifyController) {
    var pair by remember { mutableStateOf(SpotifyPairCode()) }
    val image = remember(pair) { spotifyQr(pair.url).asImageBitmap() }
    var expired by remember(pair) { mutableStateOf(false) }
    var error by remember(pair) { mutableStateOf<String?>(null) }

    LaunchedEffect(pair) {
        while (System.currentTimeMillis() < pair.until) {
            try {
                if (controller.claim(pair)) {
                    controller.linkedFromPair()
                    controller.refresh()
                    return@LaunchedEffect
                }
                error = null
            } catch (failure: Exception) {
                error = failure.message ?: "No hi ha connexió amb Spotify."
            }
            delay(3_000)
        }
        expired = true
    }
    Text("Vincula Spotify Premium des del mòbil. El marc només controla els altaveus.",
        style = Type.Tiny.copy(color = Palette.InkSoft, fontSize = 18.sp),
        modifier = Modifier.padding(bottom = 8.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(image, contentDescription = "Codi QR per autoritzar Spotify al marc",
            modifier = Modifier.size(230.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .padding(10.dp))
    }
    Text(if (expired) "El QR ha caducat." else
            error ?: "Escaneja el QR i segueix els passos del mòbil…",
        style = Type.Tiny.copy(color = if (expired || error != null) Palette.Warm
            else Palette.InkSoft, fontSize = 17.sp),
        modifier = Modifier.padding(top = 10.dp))
    if (expired) {
        OutlinedButton(onClick = { pair = SpotifyPairCode() }) {
            Text("Generar un QR nou")
        }
    }
    Text("Necessitaràs un Client ID de Spotify for Developers; no cal contrasenya ni Client Secret.",
        style = Type.Tiny.copy(color = Palette.InkMuted, fontSize = 15.sp),
        modifier = Modifier.padding(top = 10.dp))
}

private fun spotifyQr(url: String): Bitmap {
    val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 300, 300)
    val pixels = IntArray(matrix.width * matrix.height) { n ->
        if (matrix[n % matrix.width, n / matrix.width]) AndroidColor.BLACK else AndroidColor.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}

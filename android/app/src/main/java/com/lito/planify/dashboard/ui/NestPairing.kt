package com.lito.planify.dashboard.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.lito.planify.dashboard.ui.theme.Type
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val PAIRING_MILLIS = 300_000L

private class NestPairingRequest {
    val proof = ByteArray(32).also { SecureRandom().nextBytes(it) }.hex()
    val until = System.currentTimeMillis() + PAIRING_MILLIS
    private val id = MessageDigest.getInstance("SHA-256").digest(proof.toByteArray(Charsets.UTF_8)).hex()
    val url = "$NEST_ORIGIN/nest-pair.html#$id.$until"
}

private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun qrBitmap(url: String): Bitmap {
    val matrix = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 320, 320)
    val pixels = IntArray(matrix.width * matrix.height) { position ->
        if (matrix[position % matrix.width, position / matrix.width]) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
}

private suspend fun claim(request: NestPairingRequest): String? = withContext(Dispatchers.IO) {
    val connection = URL("$NEST_ORIGIN/api/nest").openConnection() as HttpURLConnection
    try {
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 6000
        connection.readTimeout = 6000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use {
            it.write(JSONObject().put("action", "pair-claim")
                .put("proof", request.proof).put("until", request.until)
                .toString().toByteArray(Charsets.UTF_8))
        }
        if (connection.responseCode != 200) throw IOException("No s'ha pogut consultar la vinculació")
        val result = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        result.optString("key").takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }
    } finally {
        connection.disconnect()
    }
}

@Composable
internal fun NestPairingPanel(onLinked: (String) -> Unit, onCancel: () -> Unit) {
    var request by remember { mutableStateOf(NestPairingRequest()) }
    val qr = remember(request) { qrBitmap(request.url).asImageBitmap() }
    val finish by rememberUpdatedState(onLinked)
    var expired by remember(request) { mutableStateOf(false) }
    var connectionIssue by remember(request) { mutableStateOf(false) }

    LaunchedEffect(request) {
        while (System.currentTimeMillis() < request.until) {
            val key = try {
                claim(request).also { connectionIssue = false }
            } catch (_: IOException) {
                connectionIssue = true
                null
            } catch (_: org.json.JSONException) {
                connectionIssue = true
                null
            }
            if (key != null) {
                finish(key)
                return@LaunchedEffect
            }
            delay(3000)
        }
        expired = true
    }

    if (expired) {
        Text("El QR ha caducat.", style = Type.Small)
        PillButton("Generar un QR nou", Modifier.fillMaxWidth(), primary = true,
            onClick = { request = NestPairingRequest() })
    } else {
        Text("Escaneja el QR amb el mòbil i enganxa-hi la clau. Tens 5 minuts.", style = Type.Small)
        Image(qr, contentDescription = "QR de vinculació de càmeres Nest", modifier = Modifier.size(280.dp))
        Text(
            if (connectionIssue) "Comprova la connexió del marc. Es tornarà a intentar."
            else "Esperant la confirmació del mòbil…",
            style = Type.Small
        )
    }
    PillButton("Cancel·lar", Modifier.fillMaxWidth(), onClick = onCancel)
}

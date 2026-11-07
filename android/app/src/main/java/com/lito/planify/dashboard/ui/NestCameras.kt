package com.lito.planify.dashboard.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lito.planify.dashboard.ui.theme.Type

private const val NEST_ORIGIN = "https://marc-familiar-pwa.vercel.app"
private val nestKeyPattern = Regex("[a-fA-F0-9]{64}")

enum class NestDevice(val label: String, val apiValue: String) {
    Doorbell("Timbre", "timbre"),
    Camera("Càmera", "camera")
}

@Composable
fun NestCameraPanel(
    cameraKey: String,
    onSaveKey: (String) -> Unit,
    onOpen: (NestDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(cameraKey.isEmpty()) }
    var draft by remember { mutableStateOf("") }
    Panel(modifier, compact = true) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Càmeres Nest", style = Type.Large)
            if (editing) {
                Text("Enganxa la clau de càmeres per vincular aquest dispositiu.", style = Type.Small)
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.trim().take(65) },
                    label = { Text("Clau de càmeres") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                PillButton("Desar la clau", Modifier.fillMaxWidth(), primary = true,
                    enabled = nestKeyPattern.matches(draft), onClick = {
                        onSaveKey(draft)
                        draft = ""
                        editing = false
                    })
                if (cameraKey.isNotEmpty()) {
                    PillButton("Cancel·lar", Modifier.fillMaxWidth(), onClick = { draft = ""; editing = false })
                }
            } else {
                NestDevice.entries.forEach { device ->
                    PillButton(device.label, Modifier.fillMaxWidth(), primary = true, onClick = { onOpen(device) })
                }
                PillButton("Canviar la clau", Modifier.fillMaxWidth(), onClick = { editing = true })
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NestCameraViewer(device: NestDevice, cameraKey: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val html = remember(device, cameraKey, context) {
        context.assets.open("nest-viewer.html").bufferedReader().use { it.readText() }
            .replace("__NEST_KEY__", cameraKey.takeIf(nestKeyPattern::matches).orEmpty())
            .replace("__NEST_DEVICE__", device.apiValue)
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(device.label, style = Type.Large, modifier = Modifier.weight(1f))
            PillButton("Tancar", onClick = onClose)
        }
        key(device, cameraKey) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { activity ->
                    WebView(activity).apply {
                        setBackgroundColor(Color.BLACK)
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mediaPlaybackRequiresUserGesture = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                        }
                        loadDataWithBaseURL("$NEST_ORIGIN/", html, "text/html", "UTF-8", null)
                    }
                },
                onRelease = { view ->
                    view.evaluateJavascript("if (window.endNestStream) window.endNestStream()", null)
                    // Dona temps a WebView per enviar el tancament abans de destruir-la.
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ view.destroy() }, 1000L)
                }
            )
        }
    }
}

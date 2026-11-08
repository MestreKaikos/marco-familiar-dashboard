package com.lito.planify.dashboard.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.PersistableBundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lito.planify.dashboard.ui.theme.Type
import org.json.JSONObject

internal const val NEST_ORIGIN = "https://marc-familiar-pwa.vercel.app"
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
    onPairingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(cameraKey.isEmpty()) }
    var draft by remember { mutableStateOf("") }
    var pairing by remember { mutableStateOf(false) }
    DisposableEffect(pairing) {
        onPairingChanged(pairing)
        onDispose { onPairingChanged(false) }
    }
    Panel(modifier, compact = true) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Càmeres Nest", style = Type.Large)
            if (pairing) {
                NestPairingPanel(onLinked = { key ->
                    onSaveKey(key)
                    draft = ""
                    editing = false
                    pairing = false
                }, onCancel = { pairing = false })
            } else if (editing) {
                PillButton("Vincular amb el mòbil", Modifier.fillMaxWidth(), primary = true,
                    onClick = { draft = ""; pairing = true })
                Text("També pots enganxar la clau aquí.", style = Type.Small)
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
    val embeddedUrl = "$NEST_ORIGIN/nest.html?app=1"
    val browserUrl = "$NEST_ORIGIN/nest.html#${device.apiValue}"
    var problem by remember(device) { mutableStateOf<String?>(null) }
    var useNative by remember(device, cameraKey) { mutableStateOf(true) }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(device.label, style = Type.Large, modifier = Modifier.weight(1f))
            TextButton(onClick = { useNative = !useNative }) {
                Text(if (useNative) "Provar visor web" else "Provar visor natiu", style = Type.Tiny)
            }
            TextButton(onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(browserUrl))
                try {
                    context.startActivity(intent)
                    val clip = ClipData.newPlainText("Clau de càmeres Nest", cameraKey)
                    clip.description.extras = PersistableBundle().apply {
                        putBoolean("android.content.extra.IS_SENSITIVE", true)
                    }
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                    Toast.makeText(context, "Clau copiada. Enganxa-la al navegador.", Toast.LENGTH_LONG).show()
                } catch (_: ActivityNotFoundException) {
                    problem = "No hi ha cap navegador disponible."
                }
            }) { Text("Copiar clau i obrir al navegador", style = Type.Tiny) }
            PillButton("Tancar", onClick = onClose)
        }
        problem?.let { Text(it, style = Type.Small) }
        key(device, cameraKey, useNative) {
            if (useNative) {
                NestNativePlayer(device, cameraKey, Modifier.fillMaxWidth().weight(1f))
            } else AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { activity ->
                    WebView(activity).apply {
                        setBackgroundColor(Color.BLACK)
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mediaPlaybackRequiresUserGesture = false
                        webViewClient = object : WebViewClient() {
                            private var initialized = false

                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                                request.url.toString() != embeddedUrl

                            override fun onPageFinished(view: WebView, url: String) {
                                if (initialized || url != embeddedUrl || view.url != embeddedUrl) return
                                initialized = true
                                val credential = JSONObject.quote(cameraKey)
                                val selected = JSONObject.quote(device.apiValue)
                                view.evaluateJavascript(
                                    "if (location.href === '$embeddedUrl' && window.openNestInApp) " +
                                        "window.openNestInApp($credential, $selected); else false;"
                                ) { result ->
                                    if (result != "true") problem = "No s’ha pogut iniciar el visor. Prova el navegador."
                                }
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) problem = "No s’ha pogut carregar el visor de Nest."
                            }
                        }
                        loadUrl(embeddedUrl)
                    }
                },
                onRelease = { view ->
                    view.evaluateJavascript("if (window.endNestStream) window.endNestStream()", null)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ view.destroy() }, 1000L)
                }
            )
        }
    }
}

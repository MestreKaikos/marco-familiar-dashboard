package com.lito.planify.dashboard.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lito.planify.dashboard.ui.theme.Palette
import com.lito.planify.dashboard.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class LocalPhoto(val uri: Uri, val name: String, val remote: Boolean = false)
private const val SLIDESHOW_DELAY_MS = 20_000L
private const val PHOTO_RESCAN_DELAY_MS = 60_000L

/** Galeria local per al XORO (Android 10, 1 GB RAM). Només llegeix la carpeta escollida. */
@Composable
fun PhotosScreen() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("marc_fotos", Context.MODE_PRIVATE) }
    var folder by remember { mutableStateOf(prefs.getString("carpeta", null)) }
    var photos by remember { mutableStateOf<List<LocalPhoto>>(emptyList()) }
    var sharedPhotos by remember { mutableStateOf<List<LocalPhoto>>(emptyList()) }
    var sharedUrl by remember { mutableStateOf(prefs.getString("shared_url", "").orEmpty()) }
    var sharedKey by remember { mutableStateOf(prefs.getString("shared_key", "").orEmpty()) }
    var showSharedSetup by remember { mutableStateOf(false) }
    var sharedStatus by remember { mutableStateOf("") }
    var index by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var refreshRequest by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                .onSuccess {
                    prefs.edit().putString("carpeta", uri.toString()).apply()
                    folder = uri.toString()
                    index = 0
                    error = null
                }
                .onFailure { error = "No s'ha pogut conservar l'accés a la carpeta." }
        }
    }

    // La carpeta pot rebre imatges noves mentre la presentació és oberta.
    // Reescaneja periòdicament sense carregar totes les fotos en memòria.
    LaunchedEffect(folder, refreshRequest) {
        if (folder == null) {
            photos = emptyList()
            bitmap = null
            return@LaunchedEffect
        }
        var firstScan = true
        while (true) {
            if (firstScan && photos.isEmpty()) loading = true
            try {
                val updated = withContext(Dispatchers.IO) { listLocalPhotos(context, Uri.parse(folder)) }
                val currentUri = photos.getOrNull(index)?.uri
                if (updated != photos) {
                    photos = updated
                    index = currentUri?.let { uri -> updated.indexOfFirst { it.uri == uri } }
                        ?.takeIf { it >= 0 } ?: 0
                }
                error = null
            } catch (_: Exception) {
                // Un error de lectura temporal no ha d'esborrar les fotos ja disponibles.
                error = "No s'ha pogut actualitzar la carpeta de fotografies."
            } finally {
                loading = false
                firstScan = false
            }
            delay(PHOTO_RESCAN_DELAY_MS)
        }
    }

    LaunchedEffect(sharedUrl, sharedKey, refreshRequest) {
        if (sharedUrl.isBlank() || sharedKey.isBlank()) return@LaunchedEffect
        while (true) {
            try {
                val result = SharedPhotoSync.sync(context, sharedUrl, sharedKey)
                sharedPhotos = result.photos.map { LocalPhoto(Uri.fromFile(it.file), it.name, remote = true) }
                sharedStatus = if (result.pending > 0) "Queden fotos pendents de descarregar" else "Fotos compartides sincronitzades"
            } catch (_: Exception) {
                sharedStatus = "No s'han pogut actualitzar les fotos compartides"
            }
            delay(PHOTO_RESCAN_DELAY_MS)
        }
    }

    val allPhotos = photos + sharedPhotos
    val photo = allPhotos.getOrNull(index)
    LaunchedEffect(photo?.uri) {
        bitmap = null
        if (photo != null) {
            try {
                bitmap = withContext(Dispatchers.IO) {
                    if (photo.remote) {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(photo.uri.path, opts)
                        var sample = 1
                        while (opts.outWidth / sample > 1600 || opts.outHeight / sample > 1000) sample *= 2
                        val decoded = BitmapFactory.decodeFile(photo.uri.path,
                            BitmapFactory.Options().apply { inSampleSize = sample })
                            ?: error("Imatge compartida invàlida")
                        decoded.asImageBitmap()
                    } else {
                        context.contentResolver.loadThumbnail(photo.uri, Size(1600, 1000), null).asImageBitmap()
                    }
                }
                error = null
            } catch (_: Exception) { error = "No s'ha pogut obrir aquesta fotografia." }
        }
    }
    LaunchedEffect(allPhotos.size) {
        if (allPhotos.size > 1) {
            while (true) {
                delay(SLIDESHOW_DELAY_MS)
                index = (index + 1) % allPhotos.size
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Fotografies", style = Type.Title)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { refreshRequest++ }, enabled = folder != null) {
                    Text("Actualitzar fotos")
                }
                OutlinedButton(onClick = { showSharedSetup = !showSharedSetup }) { Text("Fotos del mòbil") }
                OutlinedButton(onClick = { chooser.launch(null) }) {
                    Text(if (folder == null) "Triar carpeta" else "Canviar carpeta")
                }
            }
        }
        if (showSharedSetup) {
            OutlinedTextField(value = sharedUrl, onValueChange = { sharedUrl = it },
                label = { Text("URL del servei (/exec)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = sharedKey, onValueChange = { sharedKey = it },
                label = { Text("Clau de lectura privada") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    prefs.edit().putString("shared_url", sharedUrl.trim())
                        .putString("shared_key", sharedKey.trim()).apply()
                    refreshRequest++
                    showSharedSetup = false
                }, enabled = sharedUrl.isNotBlank() && sharedKey.length >= 32) { Text("Desar i sincronitzar") }
                TextButton(onClick = {
                    prefs.edit().remove("shared_url").remove("shared_key").apply()
                    sharedUrl = ""; sharedKey = ""; sharedPhotos = emptyList()
                    showSharedSetup = false
                }) { Text("Desconnectar") }
            }
        }
        if (sharedStatus.isNotBlank()) Text(sharedStatus, style = Type.Tiny)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(Color.Black),
            contentAlignment = Alignment.Center) {
            when {
                loading -> CircularProgressIndicator()
                photo == null -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(24.dp)) {
                    Text(if (folder == null) "Encara no hi ha fotografies" else "La carpeta no conté fotografies",
                        style = Type.Large, color = Color.White)
                    Text("Tria una carpeta d'imatges. Les fotos es queden al marc.", style = Type.Small, color = Color.LightGray)
                }
                bitmap != null -> Image(bitmap = bitmap!!, contentDescription = photo.name,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                error != null -> Text("No es pot mostrar aquesta fotografia", color = Color.White)
                else -> CircularProgressIndicator()
            }
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error.orEmpty(), style = Type.Small, color = Palette.Accent)
        }
        if (allPhotos.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { index = (index - 1 + allPhotos.size) % allPhotos.size }, enabled = allPhotos.size > 1) {
                    Text("Anterior")
                }
                Column(Modifier.weight(1f).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text((index + 1).toString() + " / " + allPhotos.size, style = Type.Small)
                    Text(photo?.name.orEmpty(), style = Type.Tiny, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = { index = (index + 1) % allPhotos.size }, enabled = allPhotos.size > 1) {
                    Text("Següent")
                }
            }
        }
    }
}

private fun listLocalPhotos(context: Context, treeUri: Uri): List<LocalPhoto> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
    val columns = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
    val list = mutableListOf<LocalPhoto>()
    context.contentResolver.query(childrenUri, columns, null, null, null)?.use { c ->
        val id = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val name = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mime = c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
        while (c.moveToNext()) {
            if (c.getString(mime).orEmpty().startsWith("image/")) {
                list += LocalPhoto(DocumentsContract.buildDocumentUriUsingTree(treeUri, c.getString(id)), c.getString(name).orEmpty())
            }
        }
    }
    return list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

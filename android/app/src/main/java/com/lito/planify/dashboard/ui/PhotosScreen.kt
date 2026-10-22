package com.lito.planify.dashboard.ui

import android.content.Context
import android.content.Intent
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

private data class LocalPhoto(val uri: Uri, val name: String)
private const val SLIDESHOW_DELAY_MS = 20_000L

/** Galeria local per al XORO (Android 10, 1 GB RAM). Només llegeix la carpeta escollida. */
@Composable
fun PhotosScreen() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("marc_fotos", Context.MODE_PRIVATE) }
    var folder by remember { mutableStateOf(prefs.getString("carpeta", null)) }
    var photos by remember { mutableStateOf<List<LocalPhoto>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
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

    LaunchedEffect(folder) {
        photos = emptyList()
        bitmap = null
        if (folder == null) return@LaunchedEffect
        loading = true
        try {
            photos = withContext(Dispatchers.IO) { listLocalPhotos(context, Uri.parse(folder)) }
            index = 0
            error = null
        } catch (_: Exception) {
            error = "No es poden llegir les fotos. Torna a triar la carpeta."
        } finally { loading = false }
    }

    val photo = photos.getOrNull(index)
    LaunchedEffect(photo?.uri) {
        bitmap = null
        if (photo != null) {
            try {
                bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.loadThumbnail(photo.uri, Size(1600, 1000), null).asImageBitmap()
                }
                error = null
            } catch (_: Exception) { error = "No s'ha pogut obrir aquesta fotografia." }
        }
    }
    LaunchedEffect(photos) {
        if (photos.size > 1) {
            while (true) {
                delay(SLIDESHOW_DELAY_MS)
                index = (index + 1) % photos.size
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Fotografies", style = Type.Title)
            OutlinedButton(onClick = { chooser.launch(null) }) {
                Text(if (folder == null) "Triar carpeta" else "Canviar carpeta")
            }
        }
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
        if (photos.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { index = (index - 1 + photos.size) % photos.size }, enabled = photos.size > 1) {
                    Text("Anterior")
                }
                Column(Modifier.weight(1f).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text((index + 1).toString() + " / " + photos.size, style = Type.Small)
                    Text(photo?.name.orEmpty(), style = Type.Tiny, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = { index = (index + 1) % photos.size }, enabled = photos.size > 1) {
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

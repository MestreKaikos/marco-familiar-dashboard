package com.lito.planify.dashboard.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.io.File

private data class LocalPhoto(val uri: Uri, val name: String)
private data class GalleryPhoto(val name: String, val uri: Uri? = null, val file: File? = null) {
    val key: String get() = uri?.toString() ?: file?.absolutePath.orEmpty()
}

/**
 * Diapositives de fotos locals + fotografies compartides de Drive, desades en
 * memòria cau privada. L'APK només descodifica una foto alhora (XORO: 1 GB RAM).
 */
@Composable
fun PhotosScreen(
    sync: FamilySync,
    shared: FamilyPhotoCache = rememberFamilyPhotoCache(),
    presentationMode: Boolean = false,
    slideSeconds: Int = 20,
    onPhotosAvailable: (Boolean) -> Unit = {},
    onExitPresentation: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("marc_fotos", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var folder by remember { mutableStateOf(prefs.getString("carpeta", null)) }
    var localPhotos by remember { mutableStateOf<List<LocalPhoto>>(emptyList()) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var localChecked by remember(folder) { mutableStateOf(folder == null) }
    var cloudChecked by remember(sync.connected) { mutableStateOf(!sync.connected || shared.photos.isNotEmpty()) }
    val availabilityListener by rememberUpdatedState(onPhotosAvailable)
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var loadingLocal by remember { mutableStateOf(false) }
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
        localPhotos = emptyList()
        bitmap = null
        if (folder == null) return@LaunchedEffect
        loadingLocal = true
        localChecked = false
        try {
            localPhotos = withContext(Dispatchers.IO) { listLocalPhotos(context, Uri.parse(folder)) }
            error = null
        } catch (_: Exception) {
            error = "No es poden llegir les fotos locals. Torna a triar la carpeta."
        } finally { loadingLocal = false; localChecked = true }
    }

    // La connexió familiar es fa des d'Opcions; aquesta pantalla només consulta fotos.
    LaunchedEffect(sync.connected, sync.photoIntervalMinutes) {
        if (!sync.connected) return@LaunchedEffect
        shared.refresh(sync, minAgeMillis = 5 * 60_000L)
        cloudChecked = true
        while (true) {
            delay(60_000L)
            shared.refresh(sync, minAgeMillis = sync.photoIntervalMinutes * 60_000L)
        }
    }
    // En tornar a la galeria, comprova si hi ha fotos noves només si fa més de 5 minuts.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { shared.refresh(sync, minAgeMillis = 5 * 60_000L) }
    }

    val photos = localPhotos.map { GalleryPhoto(it.name, uri = it.uri) } +
        shared.photos.map { GalleryPhoto(it.name, file = it.file) }

    LaunchedEffect(photos.size, localChecked, cloudChecked, shared.refreshing) {
        if (localChecked && cloudChecked && !shared.refreshing) availabilityListener(photos.isNotEmpty())
    }

    LaunchedEffect(photos.size, localChecked) {
        if (!localChecked) return@LaunchedEffect
        if (photos.isEmpty()) index = 0
        else if (index >= photos.size) index = photos.lastIndex
    }

    val photo = photos.getOrNull(index)
    LaunchedEffect(photo?.key) {
        bitmap = null
        if (photo != null) {
            try {
                bitmap = withContext(Dispatchers.IO) {
                    when {
                        photo.uri != null -> context.contentResolver
                            .loadThumbnail(photo.uri, Size(1600, 1000), null).asImageBitmap()
                        photo.file != null -> decodeSizedPhoto(photo.file).asImageBitmap()
                        else -> throw IllegalStateException("No es pot llegir la foto")
                    }
                }
                error = null
            } catch (_: Exception) { error = "No s'ha pogut obrir aquesta fotografia." }
        }
    }

    LaunchedEffect(photos.size, slideSeconds) {
        if (photos.size > 1) {
            while (true) {
                delay(slideSeconds.coerceIn(10, 60) * 1_000L)
                index = (index + 1) % photos.size
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(if (presentationMode) 0.dp else 24.dp)) {
        if (!presentationMode) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Fotografies", style = Type.Title)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { scope.launch { shared.refresh(sync) } },
                    enabled = sync.connected && !shared.refreshing) {
                    Text(if (shared.refreshing) "Actualitzant..." else "Actualitzar fotos")
                }
                OutlinedButton(onClick = { chooser.launch(null) }) {
                    Text(if (folder == null) "Triar carpeta local" else "Canviar carpeta local")
                }
            }
        }
        Text(
            "${localPhotos.size} de carpeta local · ${shared.photos.size} de Drive (desades al marc)" +
                if (sync.connected) " · Les fotos noves s'afegeixen automàticament." else
                    " · Connecta la sincronització familiar a Opcions per rebre fotos.",
            style = Type.Tiny, color = Palette.InkMuted
        )
        Spacer(Modifier.height(14.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(if (presentationMode) 0.dp else 20.dp))
            .then(if (presentationMode) Modifier.clickable(onClick = onExitPresentation) else Modifier)
            .background(Color.Black), contentAlignment = Alignment.Center) {
            when {
                loadingLocal && photos.isEmpty() -> CircularProgressIndicator()
                photo == null -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(24.dp)) {
                    Text("Encara no hi ha fotografies", style = Type.Large, color = Color.White)
                    Text("Envia fotos des de la web del mòbil o tria una carpeta del marc.",
                        style = Type.Small, color = Color.LightGray)
                }
                bitmap != null -> Image(bitmap = bitmap!!, contentDescription = photo.name,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                error != null -> Text("No es pot mostrar aquesta fotografia", color = Color.White)
                else -> CircularProgressIndicator()
            }
            if (presentationMode) {
                Text("Toca per veure Avui", style = Type.Tiny,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp))
            }
        }
        if (!presentationMode && (error != null || shared.message != null)) {
            Spacer(Modifier.height(8.dp))
            Text(error ?: shared.message.orEmpty(), style = Type.Tiny, color = Palette.InkMuted)
        }
        if (!presentationMode && photos.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(onClick = { index = (index - 1 + photos.size) % photos.size },
                    enabled = photos.size > 1) { Text("Anterior") }
                Column(Modifier.weight(1f).padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text((index + 1).toString() + " / " + photos.size, style = Type.Small)
                    Text(photo?.name.orEmpty(), style = Type.Tiny, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = { index = (index + 1) % photos.size },
                    enabled = photos.size > 1) { Text("Següent") }
            }
        }
    }
}

/** Descodifica JPEG de Drive a mida de pantalla, evitant consumir massa memòria. */
private fun decodeSizedPhoto(file: File): android.graphics.Bitmap {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) throw IllegalStateException("JPEG no vàlid")
    var sample = 1
    while (options.outWidth / sample > 1600 || options.outHeight / sample > 1000) sample *= 2
    val sized = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.absolutePath, sized)
        ?: throw IllegalStateException("No es pot descodificar la fotografia")
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

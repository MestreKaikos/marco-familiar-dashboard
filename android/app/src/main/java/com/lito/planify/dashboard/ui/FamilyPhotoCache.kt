package com.lito.planify.dashboard.ui

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SharedCachedPhoto(val id: String, val name: String, val file: File)

/**
 * Les fotos del mòbil no es mostren des de Drive en obert: l'APK les consulta amb
 * la clau familiar i en desa JPG, PNG o WebP en filesDir, visibles sense Internet.
 *
 * L'índex guardat conté només IDs/noms, mai la clau ni els IDs dels fitxers de Drive.
 */
class FamilyPhotoCache(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("marc_cached_photos", Context.MODE_PRIVATE)
    private val folder = File(app.filesDir, "marc_fotos_compartides")
    private val mutex = Mutex()
    private val validId = Regex("^[a-zA-Z0-9_-]{1,80}$")
    private val photoMaxBytes = 5L * 1024L * 1024L // Fotos.gs permet fins a 5 MB.

    var photos by mutableStateOf(readSaved())
        private set
    var refreshing by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private data class RemotePhoto(val id: String, val name: String)

    private fun cachedFile(id: String) = File(folder, "$id.jpg")

    private fun readSaved(): List<SharedCachedPhoto> = runCatching {
        val raw = JSONArray(prefs.getString("index", "[]"))
        (0 until raw.length()).mapNotNull { i ->
            val item = raw.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id")
            if (!validId.matches(id)) return@mapNotNull null
            val file = cachedFile(id)
            if (file.isFile && file.length() in 6L..photoMaxBytes)
                SharedCachedPhoto(id, item.optString("name").take(75), file)
            else null
        }.take(120)
    }.getOrDefault(emptyList())

    private fun writeIndex(items: List<SharedCachedPhoto>) {
        val index = JSONArray()
        items.forEach { photo ->
            index.put(JSONObject().put("id", photo.id).put("name", photo.name))
        }
        prefs.edit().putString("index", index.toString()).apply()
        photos = items
    }

    suspend fun refresh(sync: FamilySync) = mutex.withLock {
        if (!sync.connected) {
            message = if (photos.isEmpty()) "Activa la sincronització familiar per rebre fotografies." else null
            return@withLock
        }
        refreshing = true
        try {
            val response = sync.photoAction("photo_list")
            val remoteArray = response.getJSONArray("photos")
            val remote = (0 until minOf(remoteArray.length(), 120)).mapNotNull { i ->
                val item = remoteArray.optJSONObject(i) ?: return@mapNotNull null
                val id = item.optString("id")
                if (!validId.matches(id)) return@mapNotNull null
                RemotePhoto(id, item.optString("name").take(75))
            }
            withContext(Dispatchers.IO) { if (!folder.exists()) folder.mkdirs() }
            // Retirar les fotos que la família ha eliminat des del mòbil.
            val keepIds = remote.map { it.id }.toSet()
            withContext(Dispatchers.IO) {
                folder.listFiles()?.forEach { file ->
                    if (file.extension == "jpg" && file.nameWithoutExtension !in keepIds) file.delete()
                }
            }

            var downloaded = 0
            var attempted = 0
            var firstError: String? = null
            // Màxim 12 fotografies noves a cada comprovació per no saturar un Android d'1 GB RAM.
            for (photo in remote) {
                val file = cachedFile(photo.id)
                if (file.isFile && file.length() in 6L..photoMaxBytes) continue
                if (attempted >= 12) break
                attempted++
                try {
                    val result = sync.photoAction("photo_get", JSONObject().put("id", photo.id))
                    val encoded = result.getJSONObject("photo").getString("base64")
                    if (encoded.length > 7_100_000) throw IllegalStateException("Fotografia massa gran")
                    val bytes = Base64.decode(encoded, Base64.DEFAULT)
                    val mime = result.getJSONObject("photo").optString("mimeType", "image/jpeg")
                    val jpeg = bytes.size >= 4 &&
                        (bytes[0].toInt() and 255) == 255 &&
                        (bytes[1].toInt() and 255) == 216 &&
                        (bytes[bytes.lastIndex - 1].toInt() and 255) == 255 &&
                        (bytes[bytes.lastIndex].toInt() and 255) == 217
                    val pngSignature = intArrayOf(137,80,78,71,13,10,26,10)
                    val png = bytes.size >= 8 &&
                        pngSignature.indices.all { i -> (bytes[i].toInt() and 255) == pngSignature[i] }
                    val webp = bytes.size >= 12 &&
                        String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                        String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
                    if (bytes.size.toLong() !in 6L..photoMaxBytes ||
                        !((mime == "image/jpeg" && jpeg) || (mime == "image/png" && png) ||
                            (mime == "image/webp" && webp))) {
                        throw IllegalStateException("Fotografia JPG, PNG o WebP incorrecta")
                    }
                    withContext(Dispatchers.IO) {
                        val temp = File(folder, photo.id + ".tmp")
                        try {
                            temp.writeBytes(bytes)
                            if (!temp.renameTo(file)) throw IllegalStateException("No es pot guardar la foto")
                        } finally {
                            temp.delete()
                        }
                    }
                    downloaded++
                } catch (error: Exception) {
                    if (firstError == null) firstError = error.message ?: "No s'ha pogut descarregar una foto"
                }
            }

            val valid = withContext(Dispatchers.IO) {
                remote.mapNotNull { item ->
                    val file = cachedFile(item.id)
                    if (file.isFile && file.length() in 6L..photoMaxBytes)
                        SharedCachedPhoto(item.id, item.name, file)
                    else null
                }
            }
            writeIndex(valid)
            message = when {
                firstError != null -> "Algunes fotos no s'han pogut baixar. Es tornarà a provar."
                valid.size < remote.size -> "Baixant fotos noves · ${valid.size} de ${remote.size}"
                else -> null
            }
        } catch (error: Exception) {
            // Sense Internet, conservar i mostrar la còpia de les fotos ja descarregades.
            message = "No es poden actualitzar les fotos. Mostrem les que ja tenim desades."
        } finally {
            refreshing = false
        }
    }
}

@Composable
fun rememberFamilyPhotoCache(): FamilyPhotoCache {
    val context = LocalContext.current
    return remember(context) { FamilyPhotoCache(context) }
}

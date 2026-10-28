package com.lito.planify.dashboard.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal data class SharedPhoto(val file: File, val name: String)
internal data class PhotoSyncResult(val photos: List<SharedPhoto>, val pending: Int)

/** Còpia privada al marc. La xarxa només s'utilitza per incorporar fotos noves. */
internal object SharedPhotoSync {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true).build()

    private fun post(url: String, data: JSONObject): JSONObject {
        val request = Request.Builder().url(url)
            .post(data.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            require(response.isSuccessful && response.request.url.isHttps) { "Resposta del servei invàlida" }
            val raw = response.body?.string().orEmpty()
            require(raw.length < 8_000_000) { "Resposta massa gran" }
            return JSONObject(raw).also { require(it.optBoolean("ok")) { "El servei ha rebutjat la consulta" } }
        }
    }

    private fun fileName(id: String): String =
        MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) } + ".jpg"

    suspend fun sync(context: Context, rawUrl: String, key: String): PhotoSyncResult =
        withContext(Dispatchers.IO) {
            require(key.length >= 32) { "La clau de lectura és massa curta" }
            val url = rawUrl.trim().toHttpUrlOrNull()
                ?: error("URL invàlida")
            require(url.isHttps && url.host == "script.google.com" && url.encodedPath.endsWith("/exec")) {
                "Cal una URL HTTPS de Google Apps Script acabada en /exec"
            }
            val dir = File(context.filesDir, "shared_photos").apply { mkdirs() }
            val items = post(url.toString(), JSONObject().put("action","photos_list").put("key",key))
                .getJSONArray("data")
            require(items.length() <= 300) { "Massa fotos al manifest" }
            val photos = mutableListOf<SharedPhoto>()
            var downloads = 0
            for (i in 0 until items.length()) {
                val row = items.getJSONObject(i)
                val id = row.optString("id")
                val size = row.optLong("size")
                if (!id.matches(Regex("[A-Za-z0-9_-]{10,100}")) || size < 1 || size > 5_242_880) continue
                val file = File(dir, fileName(id))
                if (!file.isFile && downloads < 4) {
                    val photo = post(url.toString(), JSONObject().put("action","photos_get")
                        .put("key",key).put("payload", JSONObject().put("id",id))).getJSONObject("data")
                    require(photo.optString("mime") in setOf("image/jpeg","image/png","image/webp")) {
                        "Format no admès"
                    }
                    val bytes = Base64.decode(photo.getString("base64"), Base64.DEFAULT)
                    require(bytes.isNotEmpty() && bytes.size <= 5_242_880) { "Foto massa gran" }
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
                    require(options.outWidth > 0 && options.outHeight > 0) { "Imatge invàlida" }
                    val tmp = File(dir, file.name + ".tmp")
                    try {
                        tmp.outputStream().use { it.write(bytes) }
                        require(tmp.renameTo(file)) { "No s'ha pogut guardar" }
                    } finally { tmp.delete() }
                    downloads++
                }
                if (file.isFile) photos.add(SharedPhoto(file,row.optString("name","Foto compartida")))
            }
            PhotoSyncResult(photos, items.length()-photos.size)
        }
}

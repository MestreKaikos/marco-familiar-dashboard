package com.lito.planify.dashboard.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val SPOTIFY_API = "https://api.spotify.com/v1"
private const val SPOTIFY_TOKEN = "https://accounts.spotify.com/api/token"
private const val PREFERRED_SPOTIFY_SPEAKER = "ABAIX"
private const val SPOTIFY_PAIR = "https://marc-familiar-pwa.vercel.app/api/spotify-pair"
private val SPOTIFY_CLIENT = Regex("[A-Za-z0-9]{32}")
private val SPOTIFY_ID = Regex("[A-Za-z0-9]+")
private val SPOTIFY_PLAYLIST = Regex("spotify:playlist:[A-Za-z0-9]+")

internal data class SpotifySpeaker(
    val id: String, val name: String, val isActive: Boolean, val type: String
)
internal data class SpotifyList(val id: String, val uri: String, val title: String)

/** Prova privada; el codi QR conté només el SHA-256 de la prova, mai cap token. */
internal class SpotifyPairCode {
    val proof = ByteArray(32).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    val until = System.currentTimeMillis() + 5 * 60_000L
    private val id = MessageDigest.getInstance("SHA-256")
        .digest(proof.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    val url = "https://marc-familiar-pwa.vercel.app/spotify.html#pair=" + id + "." + until
}

internal class SpotifyController(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("marc_spotify", Context.MODE_PRIVATE)

    var linked by mutableStateOf(!prefs.getString("refresh_token", "").isNullOrEmpty())
        private set
    var loading by mutableStateOf(false)
        private set
    var status by mutableStateOf("Spotify Premium · tria un altaveu Nest.")
        private set
    var speakers by mutableStateOf<List<SpotifySpeaker>>(emptyList())
        private set
    var lists by mutableStateOf<List<SpotifyList>>(emptyList())
        private set
    var favorites by mutableStateOf(prefs.getStringSet("favourites", emptySet()).orEmpty().toSet())
        private set
    var selectedDeviceId by mutableStateOf(prefs.getString("device_id", "").orEmpty())
        private set

    fun chooseDevice(id: String) {
        selectedDeviceId = id
        prefs.edit().putString("device_id", id).apply()
    }

    fun toggleFavorite(uri: String) {
        if (!SPOTIFY_PLAYLIST.matches(uri)) return
        favorites = if (uri in favorites) favorites - uri else favorites + uri
        prefs.edit().putStringSet("favourites", favorites).apply()
    }

    fun disconnect() {
        prefs.edit().clear().commit()
        linked = false
        loading = false
        speakers = emptyList()
        lists = emptyList()
        favorites = emptySet()
        selectedDeviceId = ""
        status = "Spotify desvinculat. Escaneja el QR per tornar-lo a connectar."
    }

    private fun form(fields: Map<String, String>) = fields.entries.joinToString("&") {
        URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
    }

    private fun request(
        url: String, method: String = "GET", authorization: String? = null,
        payload: String? = null, contentType: String = "application/json"
    ): Pair<Int, JSONObject> {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.requestMethod = method
            if (authorization != null)
                connection.setRequestProperty("Authorization", "Bearer " + authorization)
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (code == 204) return code to JSONObject()
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText().take(24_000) }.orEmpty()
            return code to runCatching { JSONObject(text) }.getOrDefault(JSONObject())
        } finally {
            connection.disconnect()
        }
    }

    private fun clearExpired() {
        prefs.edit().remove("access_token").remove("refresh_token")
            .remove("expires_at").commit()
    }

    /** PKCE al mateix dispositiu, sense Client Secret ni tokens en cap URL. */
    @Synchronized
    private fun accessToken(forceRefresh: Boolean = false): String {
        val now = System.currentTimeMillis()
        val cached = prefs.getString("access_token", "").orEmpty()
        if (!forceRefresh && cached.isNotEmpty() &&
            prefs.getLong("expires_at", 0L) > now + 60_000L) return cached
        val client = prefs.getString("client_id", "").orEmpty()
        val refresh = prefs.getString("refresh_token", "").orEmpty()
        if (!SPOTIFY_CLIENT.matches(client) || refresh.isEmpty())
            throw IOException("Spotify no està vinculat. Escaneja el QR.")
        val (code, body) = request(SPOTIFY_TOKEN, "POST", payload = form(mapOf(
            "grant_type" to "refresh_token",
            "refresh_token" to refresh,
            "client_id" to client
        )), contentType = "application/x-www-form-urlencoded")
        if (code !in 200..299 || !body.has("access_token")) {
            if (body.optString("error") == "invalid_grant") {
                clearExpired()
                throw IOException("La vinculació Spotify ha caducat. Torna a escanejar el QR.")
            }
            throw IOException("No s'ha pogut renovar Spotify (error " + code + ").")
        }
        val token = body.getString("access_token")
        val nextRefresh = body.optString("refresh_token")
            .takeIf { it.isNotEmpty() } ?: refresh
        val validUntil = now + body.optLong("expires_in", 3600L)
            .coerceIn(60L, 7200L) * 1000L
        prefs.edit().putString("access_token", token)
            .putString("refresh_token", nextRefresh)
            .putLong("expires_at", validUntil).commit()
        return token
    }

    private fun spotify(endpoint: String, method: String = "GET", data: JSONObject? = null): JSONObject {
        fun call(token: String) = request(
            SPOTIFY_API + endpoint, method, authorization = token, payload = data?.toString()
        )
        var (code, body) = call(accessToken())
        if (code == 401) {
            val retry = call(accessToken(forceRefresh = true))
            code = retry.first
            body = retry.second
        }
        if (code !in 200..299) {
            val explanation = when (code) {
                401 -> "Cal tornar a autoritzar Spotify."
                403 -> "Spotify Premium o permisos de reproducció insuficients."
                404 -> "El Nest no està disponible. Activa'l des del Spotify del mòbil."
                429 -> "Massa peticions a Spotify. Torna-ho a provar més tard."
                else -> "Spotify ha retornat l'error " + code + "."
            }
            throw IOException(explanation)
        }
        return body
    }

    private fun readDevices(): List<SpotifySpeaker> {
        val data = spotify("/me/player/devices").optJSONArray("devices") ?: JSONArray()
        return buildList {
            for (i in 0 until data.length()) {
                val d = data.optJSONObject(i) ?: continue
                val id = d.optString("id")
                if (id.isBlank() || d.optBoolean("is_restricted")) continue
                add(SpotifySpeaker(id, d.optString("name", "Altaveu"),
                    d.optBoolean("is_active"), d.optString("type", "")))
            }
        }
    }

    private fun readLists(): List<SpotifyList> {
        val found = ArrayList<SpotifyList>()
        for (offset in 0 until 200 step 50) {
            val data = spotify("/me/playlists?limit=50&offset=" + offset)
            val items = data.optJSONArray("items") ?: break
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (!SPOTIFY_ID.matches(id)) continue
                found.add(SpotifyList(id, "spotify:playlist:" + id,
                    item.optString("name", "Playlist sense nom").take(110)))
            }
            if (items.length() < 50 || found.size >= data.optInt("total", Int.MAX_VALUE))
                break
        }
        return found.distinctBy { it.id }
    }

    suspend fun refresh() {
        if (!linked) return
        loading = true
        status = "Consultant Spotify i els altaveus…"
        try {
            val result = withContext(Dispatchers.IO) { readDevices() to readLists() }
            speakers = result.first.filter { it.name.trim().equals(PREFERRED_SPOTIFY_SPEAKER, ignoreCase = true) }
            lists = result.second
            chooseDevice(speakers.firstOrNull()?.id ?: "")
            status = if (speakers.isEmpty())
                "No apareix ABAIX. Activa Spotify a aquest altaveu des del mòbil i prem Actualitzar."
            else lists.size.toString() + " playlists · " + speakers.size +
                " dispositius Spotify Connect."
        } catch (error: Exception) {
            if (prefs.getString("refresh_token", "").isNullOrEmpty()) linked = false
            status = error.message ?: "No s'ha pogut consultar Spotify."
        } finally { loading = false }
    }

    suspend fun claim(pair: SpotifyPairCode): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("action", "claim")
            .put("proof", pair.proof).put("until", pair.until)
        val (code, body) = request(SPOTIFY_PAIR, "POST", payload = payload.toString())
        if (code !in 200..299)
            throw IOException(body.optString("error", "Vinculació Spotify fallida."))
        if (!body.optBoolean("linked")) return@withContext false
        val client = body.optString("client_id")
        val access = body.optString("access_token")
        val refresh = body.optString("refresh_token")
        val expiry = body.optLong("expires_at")
        if (!SPOTIFY_CLIENT.matches(client) ||
            access.length < 12 || refresh.length < 12 || expiry <= System.currentTimeMillis())
            throw IOException("La resposta de Spotify no és vàlida.")
        val favs = body.optJSONArray("favourites") ?: JSONArray()
        val newFavorites = buildSet {
            for (i in 0 until favs.length()) {
                val uri = favs.optString(i)
                if (SPOTIFY_PLAYLIST.matches(uri)) add(uri)
            }
        }
        val saved = prefs.edit()
            .putString("client_id", client)
            .putString("access_token", access)
            .putString("refresh_token", refresh)
            .putLong("expires_at", expiry)
            .putStringSet("favourites", newFavorites)
            .commit()
        if (!saved) throw IOException("No s'ha pogut desar la vinculació Spotify.")
        true
    }

    fun linkedFromPair() {
        linked = true
        favorites = prefs.getStringSet("favourites", emptySet()).orEmpty().toSet()
        status = "Spotify vinculat correctament."
    }

    private fun destination(): String {
        val id = selectedDeviceId
        if (id.isBlank() || speakers.none { it.id == id })
            throw IOException("Selecciona primer el Nest o el grup d'altaveus.")
        return "?device_id=" + URLEncoder.encode(id, "UTF-8")
    }

    suspend fun play(list: SpotifyList) = action("Enviant la playlist als altaveus…") {
        spotify("/me/player/play" + destination(), "PUT",
            JSONObject().put("context_uri", list.uri))
        "Reproduint «" + list.title + "» al dispositiu seleccionat."
    }

    suspend fun control(command: String) = action("Enviant la comanda a Spotify…") {
        val endpoint = when (command) {
            "pause" -> "pause"
            "resume" -> "play"
            "next" -> "next"
            "previous" -> "previous"
            else -> throw IOException("Comanda no vàlida.")
        }
        val method = if (command == "next" || command == "previous") "POST" else "PUT"
        spotify("/me/player/" + endpoint + destination(), method,
            if (command == "resume") JSONObject() else null)
        "Comanda enviada als altaveus."
    }

    private suspend fun action(loadingText: String, work: () -> String) {
        loading = true
        status = loadingText
        try {
            status = withContext(Dispatchers.IO) { work() }
        } catch (error: Exception) {
            if (prefs.getString("refresh_token", "").isNullOrEmpty()) linked = false
            status = error.message ?: "No s'ha pogut controlar Spotify."
        } finally { loading = false }
    }
}

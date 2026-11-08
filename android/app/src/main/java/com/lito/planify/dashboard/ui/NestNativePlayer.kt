package com.lito.planify.dashboard.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lito.planify.dashboard.ui.theme.Type
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * El WebView 74 del marc no anuncia H.264 al seu SDP. Aquest reproductor
 * utilitza una biblioteca WebRTC actual integrada a l'APK i només consulta
 * la nostra API Vercel (mai no exposa el token OAuth de Google).
 */
@Composable
internal fun NestNativePlayer(device: NestDevice, cameraKey: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var status by remember(device, cameraKey) { mutableStateOf("Iniciant WebRTC natiu…") }
    val session = remember(device, cameraKey) {
        NestNativeSession(context.applicationContext, device, cameraKey) { status = it }
    }
    DisposableEffect(session) {
        session.start()
        onDispose { session.stop() }
    }
    Column(modifier.background(Color.Black), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(status, color = Color.White, style = Type.Tiny,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        AndroidView(factory = { session.renderer }, modifier = Modifier.fillMaxSize())
    }
}

/** Validació que també cobreixen els tests, sense modificar un SDP no compatible. */
internal fun nestNativeOfferProblem(sdp: String): String? {
    val order = Regex("(?m)^m=(audio|video|application) ")
        .findAll(sdp).map { it.groupValues[1] }.toList()
    if (order != listOf("audio", "video", "application"))
        return "El SDP no té els canals d'àudio, vídeo i dades en ordre."
    if (!sdp.contains(Regex("(?mi)^a=rtpmap:\\d+ opus/")))
        return "Falta el còdec d'àudio Opus."
    if (!sdp.contains(Regex("(?mi)^a=rtpmap:\\d+ H264/")))
        return "Aquest dispositiu no ofereix descodificació WebRTC H.264."
    val audio = sdp.substringAfter("m=audio ", "").substringBefore("\nm=video ")
    if (!audio.contains("a=recvonly"))
        return "El canal d'àudio no és només de recepció."
    return null
}

private class NestNativeSession(
    private val context: Context,
    private val device: NestDevice,
    private val cameraKey: String,
    private val update: (String) -> Unit
) {
    val renderer = SurfaceViewRenderer(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gathered = CompletableDeferred<Unit>()
    private var egl: EglBase? = null
    private var factory: PeerConnectionFactory? = null
    private var pc: PeerConnection? = null
    private var videoTrack: VideoTrack? = null
    private var channel: DataChannel? = null
    private var remoteSession: String? = null
    private var closed = false

    private fun say(message: String) {
        scope.launch {
            if (!closed) update(message.take(240))
        }
    }

    fun start() {
        scope.launch {
            try {
                initializeWebRtc(context)
                val surface = EglBase.create()
                egl = surface
                renderer.init(surface.eglBaseContext, object : RendererCommon.RendererEvents {
                    override fun onFirstFrameRendered() = say("Vídeo Nest en directe (WebRTC natiu)")
                    override fun onFrameResolutionChanged(width: Int, height: Int, rotation: Int) {
                        say("Vídeo en directe · " + width + "×" + height)
                    }
                })
                renderer.setMirror(false)
                renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)

                val decoder = DefaultVideoDecoderFactory(surface.eglBaseContext)
                val codecs = decoder.supportedCodecs.map { it.name }
                say("Còdecs natius: " + codecs.distinct().joinToString(", "))
                if (codecs.none { it.equals("H264", ignoreCase = true) }) {
                    throw IOException("El dispositiu no ofereix descodificació H.264 nativa.")
                }

                val encoder = DefaultVideoEncoderFactory(surface.eglBaseContext, true, true)
                val peerFactory = PeerConnectionFactory.builder()
                    .setVideoEncoderFactory(encoder)
                    .setVideoDecoderFactory(decoder)
                    .createPeerConnectionFactory()
                factory = peerFactory
                val config = PeerConnection.RTCConfiguration(
                    listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                        .createIceServer())
                )
                config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                val peer = peerFactory.createPeerConnection(config, object : PeerConnection.Observer {
                    override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                        say("Connexió Nest · ICE " + state.name)
                    }
                    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                        if (state == PeerConnection.IceGatheringState.COMPLETE) gathered.complete(Unit)
                    }
                    override fun onIceCandidate(candidate: IceCandidate) = Unit
                    override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit
                    override fun onAddStream(stream: MediaStream) = Unit
                    override fun onRemoveStream(stream: MediaStream) = Unit
                    override fun onDataChannel(dataChannel: DataChannel) = Unit
                    override fun onRenegotiationNeeded() = Unit
                    override fun onTrack(transceiver: RtpTransceiver) {
                        val incoming = transceiver.receiver.track()
                        if (incoming is VideoTrack && !closed) {
                            videoTrack = incoming
                            incoming.addSink(renderer)
                            say("Rebent la pista de vídeo Nest…")
                        }
                    }
                }) ?: throw IOException("No es pot crear la connexió WebRTC nativa.")
                pc = peer

                peer.addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
                )
                peer.addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
                )
                channel = peer.createDataChannel("data", DataChannel.Init())
                val offer = createOffer(peer)
                setDescription(peer, offer, local = true)
                withTimeoutOrNull(8_000L) { gathered.await() }
                val sdp = (peer.localDescription?.description ?: offer.description)
                    .trimEnd('\r', '\n') + "\r\n"
                val error = nestNativeOfferProblem(sdp)
                if (error != null) throw IOException(error)
                say("Oferta nativa H.264 preparada; sol·licitant el directe a Google…")
                val response = withContext(Dispatchers.IO) {
                    api("start", JSONObject().put("device", device.apiValue).put("sdp", sdp))
                }
                remoteSession = response.getString("session")
                setDescription(peer, SessionDescription(SessionDescription.Type.ANSWER,
                    response.getString("answerSdp")), local = false)
                say("Google ha acceptat la sessió. Esperant fotogrames…")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say("Nest natiu: " + (e.message ?: "No s'ha pogut obrir el directe."))
            }
        }
    }

    private suspend fun createOffer(peer: PeerConnection): SessionDescription =
        suspendCancellableCoroutine { continuation ->
            peer.createOffer(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) {
                    if (continuation.isActive) continuation.resume(description)
                }
                override fun onCreateFailure(message: String) {
                    if (continuation.isActive) continuation.resumeWithException(IOException(message))
                }
                override fun onSetSuccess() = Unit
                override fun onSetFailure(message: String) = Unit
            }, MediaConstraints())
        }

    private suspend fun setDescription(
        peer: PeerConnection, description: SessionDescription, local: Boolean
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        val observer = object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) = Unit
            override fun onCreateFailure(message: String) = Unit
            override fun onSetSuccess() {
                if (continuation.isActive) continuation.resume(Unit)
            }
            override fun onSetFailure(message: String) {
                if (continuation.isActive) continuation.resumeWithException(IOException(message))
            }
        }
        if (local) peer.setLocalDescription(observer, description)
        else peer.setRemoteDescription(observer, description)
    }

    private fun api(action: String, payload: JSONObject): JSONObject {
        val connection = URL(NEST_ORIGIN + "/api/nest").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer " + cameraKey)
            val request = payload.put("action", action).toString()
            connection.outputStream.use { it.write(request.toByteArray(Charsets.UTF_8)) }
            val successful = connection.responseCode == 200
            val result = (if (successful) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { JSONObject(it.readText()) } ?: JSONObject()
            if (!successful) throw IOException(
                result.optString("error", "Google Nest ha retornat un error " + connection.responseCode)
                    .take(180)
            )
            return result
        } finally {
            connection.disconnect()
        }
    }

    fun stop() {
        if (closed) return
        closed = true
        val token = remoteSession
        remoteSession = null
        // La sessió es tanca al backend encara que Compose destrueixi el visor.
        if (token != null) CoroutineScope(Dispatchers.IO).launch {
            runCatching { api("stop", JSONObject().put("session", token)) }
        }
        scope.cancel()
        runCatching { videoTrack?.removeSink(renderer) }
        runCatching { channel?.dispose() }
        runCatching { pc?.close() }
        runCatching { pc?.dispose() }
        runCatching { factory?.dispose() }
        runCatching { renderer.release() }
        runCatching { egl?.release() }
    }

    private companion object {
        @Volatile private var initialized = false

        private fun initializeWebRtc(context: Context) {
            synchronized(NestNativeSession::class.java) {
                if (!initialized) {
                    PeerConnectionFactory.initialize(
                        PeerConnectionFactory.InitializationOptions.builder(context)
                            .createInitializationOptions()
                    )
                    initialized = true
                }
            }
        }
    }
}

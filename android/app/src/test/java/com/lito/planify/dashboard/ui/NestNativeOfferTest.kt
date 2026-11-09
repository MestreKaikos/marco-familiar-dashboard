package com.lito.planify.dashboard.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NestNativeOfferTest {
    private val validOffer = listOf(
        "v=0", "m=audio 9 UDP/TLS/RTP/SAVPF 111", "a=recvonly",
        "a=rtpmap:111 opus/48000/2",
        "m=video 9 UDP/TLS/RTP/SAVPF 102", "a=recvonly",
        "a=rtpmap:102 H264/90000",
        "m=application 9 UDP/DTLS/SCTP webrtc-datachannel", ""
    ).joinToString("\r\n")

    @Test fun acceptsH264WithAllRequiredNestMediaLines() {
        assertNull(nestNativeOfferProblem(validOffer))
    }

    @Test fun rejectsMissingH264DecoderOffer() {
        val offer = validOffer.replace("a=rtpmap:102 H264/90000", "a=rtpmap:102 VP8/90000")
        assertEquals("Aquest dispositiu no ofereix descodificació WebRTC H.264.",
            nestNativeOfferProblem(offer))
    }

    @Test fun rejectsWrongMediaOrder() {
        val offer = validOffer.replace("m=video 9 UDP/TLS/RTP/SAVPF 102",
            "m=application 9 UDP/DTLS/SCTP webrtc-datachannel")
            .replace("m=application 9 UDP/DTLS/SCTP webrtc-datachannel\r\n", "")
        assertEquals("El SDP no té els canals d'àudio, vídeo i dades en ordre.",
            nestNativeOfferProblem(offer))
    }

    @Test fun rejectsAudioSending() {
        val offer = validOffer.replaceFirst("a=recvonly", "a=sendrecv")
        assertEquals("El canal d'àudio no és només de recepció.",
            nestNativeOfferProblem(offer))
    }

    @Test fun rejectsMissingOpus() {
        val offer = validOffer.replace("a=rtpmap:111 opus/48000/2", "a=rtpmap:111 ISAC/16000")
        assertEquals("Falta el còdec d'àudio Opus.", nestNativeOfferProblem(offer))
    }
}

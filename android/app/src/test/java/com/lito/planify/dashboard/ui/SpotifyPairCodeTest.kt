package com.lito.planify.dashboard.ui

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyPairCodeTest {
    @Test fun qrContainsOnlyPublicHashAndExpiry() {
        val code = SpotifyPairCode()
        val id = MessageDigest.getInstance("SHA-256")
            .digest(code.proof.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertTrue(code.proof.matches(Regex("[0-9a-f]{64}")))
        assertTrue(code.url.endsWith("#pair=" + id + "." + code.until))
        assertFalse(code.url.contains(code.proof))
        assertTrue(code.until > System.currentTimeMillis())
        assertTrue(code.until <= System.currentTimeMillis() + 300_000)
    }
    @Test fun eachQrUsesIndependentCryptographicProof() {
        val first = SpotifyPairCode()
        val second = SpotifyPairCode()
        assertNotEquals(first.proof, second.proof)
        assertNotEquals(first.url, second.url)
    }
}

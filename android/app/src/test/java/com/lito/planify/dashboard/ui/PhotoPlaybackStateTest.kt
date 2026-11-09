package com.lito.planify.dashboard.ui

import org.junit.Assert.*
import org.junit.Test

class PhotoPlaybackStateTest {
    @Test fun orderedModeUsesAlphabeticalSequence() {
        val player = PhotoPlaybackState()
        player.configure(listOf("a", "b", "c"), false)
        assertEquals("a", player.currentKey)
        player.next()
        assertEquals("b", player.currentKey)
        player.next()
        assertEquals("c", player.currentKey)
        player.next()
        assertEquals("a", player.currentKey)
    }

    @Test fun shuffleShowsAllPhotosBeforeRepeating() {
        val keys = (1..12).map { "photo-$it" }
        val player = PhotoPlaybackState()
        player.configure(keys, true)
        val seen = mutableListOf<String>()
        repeat(keys.size) {
            seen += player.currentKey!!
            player.next()
        }
        assertEquals(keys.toSet(), seen.toSet())
        assertEquals(keys.size, seen.size)
        assertNotEquals(seen.last(), player.currentKey)
    }

    @Test fun updatingDriveAndReturningToAgendaKeepsCurrentPhoto() {
        val player = PhotoPlaybackState()
        val keys = listOf("a", "b", "c", "d", "e")
        player.configure(keys, true)
        player.next()
        val current = player.currentKey
        player.configure(keys, true)
        assertEquals(current, player.currentKey)
        player.configure(keys + "new", true)
        assertEquals(current, player.currentKey)
    }
}

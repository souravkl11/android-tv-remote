package com.ryzen.tvremote

import com.ryzen.tvremote.apps.AppShortcut
import com.ryzen.tvremote.apps.AppShortcutCodec
import com.ryzen.tvremote.apps.moveItem
import com.ryzen.tvremote.protocol.RemoteKey
import com.ryzen.tvremote.ui.components.SwipeMath
import com.ryzen.tvremote.ui.components.appInitials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteLogicTest {

    private fun app(id: String) = AppShortcut(id, "App $id", "pkg.$id", 0xFF123456, null)

    // ---- SwipeMath ---------------------------------------------------------------------------

    @Test
    fun swipeDirectionUsesDominantAxis() {
        assertEquals(RemoteKey.DPAD_RIGHT, SwipeMath.direction(30f, 10f))
        assertEquals(RemoteKey.DPAD_LEFT, SwipeMath.direction(-30f, 10f))
        assertEquals(RemoteKey.DPAD_DOWN, SwipeMath.direction(10f, 30f))
        assertEquals(RemoteKey.DPAD_UP, SwipeMath.direction(10f, -30f))
    }

    @Test
    fun swipeThreshold() {
        assertFalse(SwipeMath.exceeds(20f, 20f, 40f)) // ~28
        assertTrue(SwipeMath.exceeds(30f, 30f, 40f))  // ~42
        assertTrue(SwipeMath.exceeds(0f, -40f, 40f))
    }

    @Test
    fun dpadZones() {
        assertEquals(RemoteKey.DPAD_CENTER, SwipeMath.dpadZone(5f, -5f, 46f))
        assertEquals(RemoteKey.DPAD_UP, SwipeMath.dpadZone(5f, -100f, 46f))
        assertEquals(RemoteKey.DPAD_LEFT, SwipeMath.dpadZone(-100f, 20f, 46f))
    }

    // ---- App shortcuts -----------------------------------------------------------------------

    @Test
    fun shortcutCodecRoundTrip() {
        val list = listOf(
            AppShortcut("1", "YouTube", "https://www.youtube.com", 0xFFFF0033, "youtube"),
            AppShortcut("2", "Kodi", "org.xbmc.kodi", 0xFF17B2E7, null)
        )
        assertEquals(list, AppShortcutCodec.decode(AppShortcutCodec.encode(list)))
    }

    @Test
    fun shortcutCodecSkipsInvalidEntries() {
        val json = """[{"id":"1","name":"","target":"x"},{"id":"2","name":"Ok","target":"pkg","color":1}]"""
        val decoded = AppShortcutCodec.decode(json)
        assertEquals(1, decoded.size)
        assertEquals("Ok", decoded[0].name)
    }

    @Test
    fun moveItemShiftsAndClamps() {
        val list = listOf(app("a"), app("b"), app("c"))
        assertEquals(listOf("b", "a", "c"), moveItem(list, "a", 1).map { it.id })
        assertEquals(listOf("a", "c", "b"), moveItem(list, "c", -1).map { it.id })
        assertEquals(list, moveItem(list, "a", -1))   // already first
        assertEquals(list, moveItem(list, "c", 1))    // already last
        assertEquals(list, moveItem(list, "zzz", 1))  // unknown id
    }

    @Test
    fun initialsForTiles() {
        assertEquals("Y", appInitials("YouTube"))
        assertEquals("PV", appInitials("Prime Video"))
        assertEquals("?", appInitials("   "))
    }
}

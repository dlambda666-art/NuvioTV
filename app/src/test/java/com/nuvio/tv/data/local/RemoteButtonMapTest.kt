package com.nuvio.tv.data.local

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteButtonMapTest {
    @Test
    fun `assigned keys survive a save and reload`() {
        val map = RemoteButtonMap.EMPTY
            .with(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, RemoteButtonBinding(RemoteButtonJob.SKIP_FORWARD, 300_000L))
            .with(KeyEvent.KEYCODE_PROG_RED, RemoteButtonBinding(RemoteButtonJob.SUBTITLES_TOGGLE))
        val reloaded = RemoteButtonMap.decode(map.encode())
        assertEquals(map, reloaded)
        assertEquals(300_000L, reloaded.overrideFor(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)?.skipMs)
    }

    @Test
    fun `navigation keys are never taken over`() {
        val map = RemoteButtonMap.decode("${KeyEvent.KEYCODE_DPAD_CENTER}=play_pause,${KeyEvent.KEYCODE_BACK}=restart")
        assertNull(map.overrideFor(KeyEvent.KEYCODE_DPAD_CENTER))
        assertNull(map.overrideFor(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun `normal behaviour leaves the key alone`() {
        val map = RemoteButtonMap.EMPTY.with(KeyEvent.KEYCODE_MENU, RemoteButtonBinding.NOTHING)
        assertNull(map.overrideFor(KeyEvent.KEYCODE_MENU))
        assertEquals("", map.encode())
    }
}

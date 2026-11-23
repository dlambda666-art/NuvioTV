package com.nuvio.tv.core.wake

import com.nuvio.tv.core.tools.Rapport666
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cas pièges (23/11) : le Rapport dit où Frank était bloqué, sans jamais laisser passer un secret. */
class ExitTrace666Test {
    private val dump = """
----- pid 1234 at 2026-11-23 13:17:02 -----
"main" prio=5 tid=1 Native
  | group="main" sCount=1
  at java.io.UnixFileSystem.getBooleanAttributes0(Native method)
  at java.io.File.exists(File.java:815)
  at com.nuvio.tv.ui.navigation.NuvioNavHostKt.record666For(NuvioNavHost.kt:1573)
  - locked <0x0a1b2c3> (a java.lang.Object)
  at com.nuvio.tv.ui.screens.player.PlayerRuntimeControllerPlaybackEvents.onReady(PlayerRuntimeControllerPlaybackEvents.kt:88)

"Signal Catcher" daemon prio=10 tid=2 Runnable
  at dalvik.system.Other.run(Other.java:1)
""".trimIndent()

    @Test fun `on garde le fil principal seulement`() {
        val f = ExitReason666.mainThreadFrames(dump)!!
        assertTrue(f.contains("File.exists"))
        assertTrue(f.contains("record666For"))
        assertFalse(f.contains("Signal Catcher") || f.contains("Other.run"))
    }

    @Test fun `les noms de classe restent lisibles, les secrets non`() {
        val frame = "at com.nuvio.tv.ui.screens.player.PlayerRuntimeControllerPlaybackEvents.onReady(PlayerRuntimeControllerPlaybackEvents.kt:88)"
        assertEquals(frame, Rapport666.cleanTrace(frame))
        val msg = "java.io.IOException: 403 https://x.test/eyJrZXkiOiJzZWNyZXQifQ/manifest.json token=abcdef"
        val c = Rapport666.cleanTrace(msg)
        assertFalse(c.contains("http")); assertFalse(c.contains("abcdef")); assertFalse(c.contains("eyJ"))
    }

    @Test fun `un plantage est resume`() {
        val s = ExitReason666.crashFrames(IllegalStateException("boum"))
        assertTrue(s.startsWith("java.lang.IllegalStateException: boum"))
    }
}

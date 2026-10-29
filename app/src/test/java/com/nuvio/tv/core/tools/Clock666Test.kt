package com.nuvio.tv.core.tools

import org.junit.Assert.assertEquals
import org.junit.Test

/** Cas piège 28/10 : un soleil affiché en pleine nuit. */
class Clock666Test {
    @Test
    fun `clear night shows the moon, not the sun`() {
        assertEquals("☀", Clock666.iconFor(0, day = true))
        assertEquals("🌙", Clock666.iconFor(0, day = false))
        assertEquals("🌙", Clock666.iconFor(1, day = false))
        assertEquals("🌧", Clock666.iconFor(61, day = false))
    }
}

/** Voix des spectres : jamais la nuit, et espacée. */
class DreamVoiceRuleTest {
    @Test
    fun `ghost voice is silent at night and spaced out`() {
        val d = com.nuvio.tv.core.dream.Dream666Data
        org.junit.Assert.assertTrue(d.voiceAllowed(14, 1_000_000L, 0L))
        org.junit.Assert.assertTrue(d.voiceAllowed(23, 1_000_000L, 0L))
        org.junit.Assert.assertTrue(d.voiceAllowed(5, 1_000_000L, 0L))
        org.junit.Assert.assertFalse(d.voiceAllowed(0, 1_000_000L, 0L))
        org.junit.Assert.assertFalse(d.voiceAllowed(3, 1_000_000L, 0L))
        org.junit.Assert.assertFalse(d.voiceAllowed(14, 1_000_000L, 1_000_000L - 30_000L))
        org.junit.Assert.assertTrue(d.voiceAllowed(14, 1_000_000L, 1_000_000L - d.VOICE_GAP_MS))
    }
}

/** Plaque « PROGRAMME » : libellés de date lisibles. */
class ProgrammeLabelTest {
    @Test
    fun `programme labels`() {
        val z = java.time.ZoneId.of("Europe/Brussels")
        val now = java.time.ZonedDateTime.of(2026, 10, 28, 21, 0, 0, 0, z).toInstant().toEpochMilli()
        fun at(d: Int, h: Int, m: Int = 0) = java.time.ZonedDateTime.of(2026, 10, d, h, m, 0, 0, z).toInstant().toEpochMilli()
        val f = com.nuvio.tv.core.dream.Dream666Data
        assertEquals("Ce soir 22:00", f.whenLabel(at(28, 22), now, z))
        assertEquals("Demain 02:00", f.whenLabel(at(29, 2), now, z))
        assertEquals("Sam. 01:00", f.whenLabel(at(31, 1), now, z))
    }
}

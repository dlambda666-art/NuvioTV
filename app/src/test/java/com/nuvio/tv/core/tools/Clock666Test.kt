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

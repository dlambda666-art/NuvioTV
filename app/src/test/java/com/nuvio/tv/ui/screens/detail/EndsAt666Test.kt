package com.nuvio.tv.ui.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class EndsAt666Test {
    private val utc = TimeZone.getTimeZone("UTC")
    private val at1730 = 17L * 3600_000L + 30L * 60_000L   // 17:30 UTC le 1er janvier 1970

    @Test fun `heure de fin sur 24 h a partir de la duree`() {
        assertEquals("Se termine à 19:17", endsAt666("1h 47m", null, at1730, utc))
        assertEquals("Se termine à 19:17", endsAt666("107", null, at1730, utc))
        assertEquals("Se termine à 19:17", endsAt666("1:47", null, at1730, utc))
    }

    @Test fun `film commence - temps restant`() {
        assertEquals("Se termine à 18:10", endsAt666("1h 47m", 40L * 60_000L, at1730, utc))
    }

    @Test fun `duree inconnue - rien`() {
        assertNull(endsAt666(null, null, at1730, utc))
        assertNull(endsAt666("0", null, at1730, utc))
    }
}

package com.nuvio.tv.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

/** 16/11 : la Shield sans internet n'accuse plus French-Stream ; journal des coupures des deux Shield. */
class Net666Test {
    private val min = 60_000L

    @Test fun `piege 23h52 - Shield sans internet, French-Stream n'est pas accuse`() {
        // le 15/11 : alerte « aucune adresse French-Stream » alors que c'est la Shield du haut qui n'avait plus de câble
        assertFalse(Net666.shouldBlameService(serviceAnswered = false, shieldOnline = false))
        assertTrue(Net666.shouldBlameService(serviceAnswered = false, shieldOnline = true))
        assertFalse(Net666.shouldBlameService(serviceAnswered = true, shieldOnline = true))
    }

    @Test fun `une coupure s'ouvre puis se ferme avec sa duree`() {
        var l = Net666.opened(emptyList(), Net666.Kind.LINK, 1_000 * min)
        l = Net666.opened(l, Net666.Kind.LINK, 1_001 * min)        // déjà ouverte : pas de doublon
        assertEquals(1, l.size)
        l = Net666.closed(l, Net666.Kind.LINK, 1_006 * min)
        assertEquals(6 * min, l.single().end - l.single().start)
        assertEquals(6 * min, Net666.lastDropAgo(l, 1_012 * min))
    }

    @Test fun `micro-coupure de moins de 3 s - oubliee`() {
        val l = Net666.closed(Net666.opened(emptyList(), Net666.Kind.LINK, 10_000), Net666.Kind.LINK, 11_500)
        assertTrue(l.isEmpty())
    }

    @Test fun `coupure en cours - lastDropAgo vaut 0, jamais coupe - moins 1`() {
        assertEquals(-1L, Net666.lastDropAgo(emptyList(), 5 * min))
        assertEquals(0L, Net666.lastDropAgo(Net666.opened(emptyList(), Net666.Kind.INTERNET, min), 5 * min))
    }

    @Test fun `piege coupure de courant - redemarrage sans extinction propre`() {
        val open = Net666.opened(emptyList(), Net666.Kind.INTERNET, 10 * min)
        val power = Net666.booted(open, 20 * min, cleanShutdown = false)
        assertEquals(Net666.Kind.POWER, power.last().kind)
        assertEquals(20 * min, power.first().end)            // la coupure restée ouverte se ferme au démarrage
        assertEquals(Net666.Kind.BOOT, Net666.booted(emptyList(), 20 * min, cleanShutdown = true).last().kind)
    }

    @Test fun `le journal passe d'une Shield a l'autre sans rien perdre`() {
        val l = listOf(Net666.Entry(Net666.Kind.LINK, 1L, 2_000L), Net666.Entry(Net666.Kind.POWER, 5L, 5L), Net666.Entry(Net666.Kind.INTERNET, 9L))
        assertEquals(l, Net666.fromJson(Net666.toJson(l).toString()))
        assertTrue(Net666.fromJson("pas du json").isEmpty())
        assertTrue(Net666.fromJson(null).isEmpty())
    }

    @Test fun `vieilles lignes effacees apres 30 jours, coupure en cours gardee`() {
        val day = 24 * 60 * min
        val l = listOf(Net666.Entry(Net666.Kind.LINK, 0L, 1L), Net666.Entry(Net666.Kind.LINK, 2L, 0L), Net666.Entry(Net666.Kind.LINK, 31 * day, 31 * day + min))
        val kept = Net666.prune(l, 32 * day)
        assertEquals(2, kept.size)
    }

    @Test fun `ligne lisible, heure sur 24 h`() {
        val tz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Brussels"))
        try {
            // 16/11/2026 23:52 → 23:58 (Bruxelles)
            val start = java.time.ZonedDateTime.of(2026, 11, 16, 23, 52, 0, 0, java.time.ZoneId.of("Europe/Brussels")).toInstant().toEpochMilli()
            val text = Net666.line(Net666.Entry(Net666.Kind.LINK, start, start + 6 * min), start + 60 * min, Locale.FRANCE)
            assertTrue(text, text.contains("23:52 → 23:58 (6 min)"))
            assertTrue(text, text.contains("câble"))
        } finally { TimeZone.setDefault(tz) }
    }

    @Test fun `haut ou bas`() {
        assertEquals("Haut", Net666.whereAmI("sif"))
        assertEquals("Bas", Net666.whereAmI("mdarcy"))
    }
}

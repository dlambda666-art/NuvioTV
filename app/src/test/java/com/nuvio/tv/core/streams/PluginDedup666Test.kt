package com.nuvio.tv.core.streams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 16/11 (idée prise dans Cauldron) : un seul exemplaire de chaque fichier entre nos plugins. */
class PluginDedup666Test {
    private val hash = "h:" + "a".repeat(40)

    @Test fun `piege meme torrent chez Comet666 et Lumio - une seule copie`() {
        val d = PluginDedup666()
        // Lumio le présente avec le nom du fichier, Comet avec le nom du torrent : textes différents, même hash
        assertTrue(d.admit(listOf("film 2024 multi vff 1080p|1080p", hash), PluginDedup666.score(true, false, 2), false).keep)
        assertFalse(d.admit(listOf("film.2024.multi.pack|1080p", hash), PluginDedup666.score(true, false, 4), false).keep)
    }

    @Test fun `la copie en cache TorBox remplace une copie qui ne l'est pas`() {
        val d = PluginDedup666()
        d.admit(listOf("rel|1080p", hash), PluginDedup666.score(cachedTorBox = false, allDebridReady = true, pluginRank = 0), false)
        val v = d.admit(listOf("autre nom|1080p", hash), PluginDedup666.score(cachedTorBox = true, allDebridReady = false, pluginRank = 5), false)
        assertTrue(v.keep)
        assertEquals(setOf(hash), v.displaced)
    }

    @Test fun `AllDebrid point d'interrogation - jamais devant une copie sure, garde s'il est seul`() {
        val d = PluginDedup666()
        assertTrue(d.admit(listOf("seul|1080p"), 300, guess = true).keep)
        d.admit(listOf("rel|1080p", hash), 0, false)
        assertFalse(d.admit(listOf("x|1080p", hash), 0, guess = true).keep)
    }

    @Test fun `empreinte trop courte ou absente - pas de cle`() {
        assertEquals(null, PluginDedup666.hashKey(null))
        assertEquals(null, PluginDedup666.hashKey("abc"))
        assertEquals("h:" + "b".repeat(40), PluginDedup666.hashKey("B".repeat(40)))
    }
}

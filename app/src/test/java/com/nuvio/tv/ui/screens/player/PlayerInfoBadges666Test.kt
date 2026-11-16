package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** 16/11 (pris dans Nuvio 1.1.0) : badges de ce qui est en lecture, sous le titre. */
class PlayerInfoBadges666Test {
    private fun texts(info: MediaInfo666?, release: String?, lang: String?) =
        PlayerInfoBadges666.badges(info, release, lang).map { it.text }

    @Test fun `exemple Reddit - 1080p Dolby Digital+ 5_1 anglais WEB-DL`() {
        val info = MediaInfo666(1920, 1080, emptyList(), "audio/eac3", null, null, 6)
        assertEquals(listOf("1080p", "Dolby Digital+", "5.1", "Anglais", "WEB-DL"),
            texts(info, "High.Potential.S01E05.1080p.WEB-DL.DDP5.1.H.264", "en"))
    }

    @Test fun `piste francaise - badge VF en vert`() {
        val b = PlayerInfoBadges666.badges(MediaInfo666(3840, 2160, listOf("DV", "HDR10"), "audio/eac3-joc", null, null, 8), "Film.2026.MULTi.VFF.2160p.WEB-DL.DV", "fre")
        assertEquals(listOf("4K", "Dolby Vision", "Dolby Digital+", "Atmos", "7.1", "VF", "WEB-DL"), b.map { it.text })
        assertEquals(true, b.first { it.text == "VF" }.vf)
    }

    @Test fun `moteur MPV sans infos - on lit le nom du fichier`() {
        assertEquals(listOf("4K", "HDR10+", "TrueHD", "Atmos", "7.1", "Remux"),
            texts(null, "Film.2024.2160p.UHD.BluRay.REMUX.HDR10Plus.TrueHD.7.1.Atmos", null))
    }

    @Test fun `rien de connu - aucun badge`() {
        assertEquals(emptyList<String>(), texts(null, null, null))
        assertEquals(emptyList<String>(), texts(null, "fichier", "und"))
    }
}

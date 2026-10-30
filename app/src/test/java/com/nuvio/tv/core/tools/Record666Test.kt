package com.nuvio.tv.core.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 666TVF : cas pièges des enregistrements (Shield du bas). */
class Record666Test {
    private val GB = 1024L * 1024 * 1024

    @Test fun `la tube n'enregistre pas, la Shield du bas oui`() {
        assertFalse(Record666.isEnabledDevice("sif"))
        assertFalse(Record666.isEnabledDevice("SIF "))
        assertTrue(Record666.isEnabledDevice("mdarcy"))
        assertTrue(Record666.isEnabledDevice("darcy"))
        assertTrue(Record666.isEnabledDevice(null))
    }

    @Test fun `cle FAT32 vue en fuse sur Android 11 - 4 Go max`() {
        val mounts = """
            /dev/block/vold/public:8,1 /mnt/media_rw/1A2B-3C4D vfat rw,dirsync,nosuid 0 0
            /dev/fuse /storage/1A2B-3C4D fuse rw,lazytime,nosuid 0 0
            /dev/fuse /storage/emulated fuse rw 0 0
        """.trimIndent()
        assertEquals(Record666.FAT_MAX_BYTES, Record666.maxFileFor(mounts, "/storage/1A2B-3C4D/Android/data/com.nuvio.tv/files/666TVF"))
    }

    @Test fun `cle exFAT ou NTFS - pas de limite`() {
        val mounts = """
            /dev/block/vold/public:8,1 /mnt/media_rw/ABCD-1234 exfat rw 0 0
            /dev/fuse /storage/ABCD-1234 fuse rw 0 0
        """.trimIndent()
        assertEquals(Long.MAX_VALUE, Record666.maxFileFor(mounts, "/storage/ABCD-1234/Android/data/x/files/666TVF"))
        assertEquals(Long.MAX_VALUE, Record666.maxFileFor("", "/storage/ABCD-1234/x"))
    }

    @Test fun `ancien Android sans fuse - le point de montage le plus long gagne`() {
        val mounts = """
            /dev/block/sda1 /storage vfat rw 0 0
            /dev/block/sdb1 /storage/9999-0000 sdfat rw 0 0
        """.trimIndent()
        assertEquals(Long.MAX_VALUE, Record666.maxFileFor(mounts, "/storage/9999-0000/x"))
    }

    @Test fun `place - 4K refusee sur cle FAT32, 1080p leger accepte, taille inconnue tentee`() {
        val usable = 24 * GB
        assertEquals(Record666.Fit.TooBigForKey, Record666.fit(62 * GB, usable, Record666.FAT_MAX_BYTES))
        assertEquals(Record666.Fit.Ok, Record666.fit(3 * GB + GB / 2, usable, Record666.FAT_MAX_BYTES))
        assertEquals(Record666.Fit.Unknown, Record666.fit(null, usable, Record666.FAT_MAX_BYTES))
        assertEquals(Record666.Fit.Unknown, Record666.fit(0, usable, Record666.FAT_MAX_BYTES))
    }

    @Test fun `place - ce qui est deja en file compte`() {
        assertEquals(Record666.Fit.Ok, Record666.fit(3 * GB, 5 * GB, Long.MAX_VALUE, 0))
        assertEquals(Record666.Fit.NoRoom, Record666.fit(3 * GB, 5 * GB, Long.MAX_VALUE, 3 * GB))
    }

    @Test fun `reponses HTTP - reprise, redemarrage, lien expire`() {
        assertEquals(Record666.HttpVerdict.RESUME, Record666.verdict(206, 1000))
        assertEquals(Record666.HttpVerdict.RESUME, Record666.verdict(200, 0))
        // Le serveur ignore la reprise : on recommence au début plutôt que coller deux morceaux.
        assertEquals(Record666.HttpVerdict.RESTART, Record666.verdict(200, 1000))
        assertEquals(Record666.HttpVerdict.COMPLETE, Record666.verdict(416, 1000))
        assertEquals(Record666.HttpVerdict.EXPIRED, Record666.verdict(403, 0))
        assertEquals(Record666.HttpVerdict.EXPIRED, Record666.verdict(410, 0))
        assertEquals(Record666.HttpVerdict.RETRY, Record666.verdict(503, 0))
    }

    @Test fun `noms de fichiers propres, meme avec des pourcent`() {
        assertEquals("Monstre S04E05", Record666.safeName("Monstre", 4, 5, null))
        assertEquals("Dune  Deuxième partie (2024)".replace("  ", " "), Record666.safeName("Dune : Deuxième partie", null, null, "2024"))
        assertEquals("100% Loup S01E02", Record666.safeName("100% Loup", 1, 2, null))
        assertEquals("video", Record666.safeName("///", null, null, null))
    }

    @Test fun `extension - du nom de fichier, sinon du lien, sinon mkv`() {
        assertEquals("mp4", Record666.extensionFor("Film.2024.1080p.mp4", "https://x/y"))
        assertEquals("mkv", Record666.extensionFor(null, "https://cdn/x/Film.MKV?token=abc"))
        assertEquals("mkv", Record666.extensionFor(null, "https://cdn/dl/12345"))
    }

    @Test fun `identifiant - un par film, un par episode`() {
        assertEquals("tt1234", Record666.idFor("tt1234", null, null, null))
        assertEquals("tt1234_s4e5", Record666.idFor("tt1234", "tt1234:4:5", 4, 5))
    }

    @Test fun `qualite lue dans le nom du lien`() {
        assertEquals("1080p · VFF", Record666.qualityOf("Comet666 1080p 🗣️ 🇫🇷 VFF Dune.Part.Two.MULTi.1080p"))
        assertEquals("4K · VF", Record666.qualityOf("2160p MULTi"))
        assertEquals("1080p · VFQ", Record666.qualityOf("1080p VFQ"))
    }
}

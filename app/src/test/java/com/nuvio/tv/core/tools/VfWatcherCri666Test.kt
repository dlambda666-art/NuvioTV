package com.nuvio.tv.core.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 666TVF (03/11) : cas pièges du cri du guetteur — VF sûre, 1080p ou 4K seulement. */
class VfWatcherCri666Test {

    @Test fun `vraie VF en 1080p, HDLight ou 4K - ca crie`() {
        listOf(
            "Resident.Evil.2026.TRUEFRENCH.1080p.WEB.H264",
            "Beware Boiuna 2026 FRENCH VFF 1080p HDLight x264",
            "Apocalypse.Z.2026.MULTi.VFF.2160p.WEB.DV.HDR",
            "Ghost in the Cell (2026) VFQ 1080p BluRay",
            "Forbidden Fruits 2026 MULTi VF2 4K"
        ).forEach { assertTrue(it, VfWatcher666.isCryRelease(it)) }
    }

    @Test fun `pas de cri - VOSTFR, MULTi incertain, VF douteuse, 720p, CAM`() {
        listOf(
            "Resident.Evil.2026.VOSTFR.1080p.WEB",
            "Resident.Evil.2026.MULTi.1080p.WEB",
            "Resident Evil 2026 VF ? 1080p",
            "Resident.Evil.2026.TRUEFRENCH.720p.WEB",
            "Resident.Evil.2026.TRUEFRENCH.HDCAM",
            "Resident.Evil.2026.TRUEFRENCH.TS.1080p",
            "Resident.Evil.2026.TRUEFRENCH.WEB",
            ""
        ).forEach { assertFalse(it, VfWatcher666.isCryRelease(it)) }
        assertFalse(VfWatcher666.isCryRelease(null))
    }
}

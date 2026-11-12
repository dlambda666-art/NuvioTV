package com.nuvio.tv.core.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 12/11 : la Shield à jour installe la nouvelle version sur l'autre, jamais pendant un film. */
class Maj666Test {
    private val h = 3600_000L

    @Test fun `plus recente ici, rien en cours la-haut - on envoie`() {
        assertTrue(Maj666.shouldPush(140, 139, false, 0, 0, 10 * h))
    }

    @Test fun `film en cours la-haut, meme version ou plus recente - on n'envoie pas`() {
        assertFalse(Maj666.shouldPush(140, 139, true, 0, 0, 10 * h))
        assertFalse(Maj666.shouldPush(140, 140, false, 0, 0, 10 * h))
        assertFalse(Maj666.shouldPush(139, 140, false, 0, 0, 10 * h))
    }

    @Test fun `deja essaye il y a moins de 3 h - on attend`() {
        assertFalse(Maj666.shouldPush(140, 139, false, 140, 9 * h, 10 * h))
        assertTrue(Maj666.shouldPush(140, 139, false, 140, 6 * h, 10 * h))
    }
}

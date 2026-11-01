package com.nuvio.tv.ui.screens.stream

import com.nuvio.tv.data.local.StreamAutoPlayMode
import org.junit.Assert.assertEquals
import org.junit.Test

/** 666TVF : cas piège du 01/11 — un film jamais vu ne se lançait pas, seulement les reprises. */
class AutoPlay666Test {
    @Test fun `le bouton lecture lance toujours un lien, meme en reglage manuel`() {
        assertEquals(StreamAutoPlayMode.FIRST_STREAM, effectiveAutoPlayMode666(StreamAutoPlayMode.MANUAL))
        assertEquals(StreamAutoPlayMode.FIRST_STREAM, effectiveAutoPlayMode666(StreamAutoPlayMode.FIRST_STREAM))
        assertEquals(StreamAutoPlayMode.REGEX_MATCH, effectiveAutoPlayMode666(StreamAutoPlayMode.REGEX_MATCH))
    }
}

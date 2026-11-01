package com.nuvio.tv.ui.screens.calendar

import com.nuvio.tv.core.calendar.VfState
import org.junit.Assert.assertEquals
import org.junit.Test

/** 666TVF : cas piège du 01/11 — la cloche annonce la VF de WWE, le calendrier restait sur « pas encore ». */
class CalendarVf666Test {
    @Test fun `VF trouvee par la cloche - le calendrier passe en dispo`() {
        val vf = mapOf("tt1:28:44" to VfState.NOT_YET, "tt2:1:5" to VfState.CHECKING, "tt3:2:1" to VfState.NOT_YET)
        val out = applyKnownVf666(vf, setOf("tt1:28:44", "ttX:9:9"))
        assertEquals(VfState.AVAILABLE, out["tt1:28:44"])
        assertEquals(VfState.CHECKING, out["tt2:1:5"])
        assertEquals(VfState.NOT_YET, out["tt3:2:1"])
        assertEquals(3, out.size) // un épisode non affiché n'est pas ajouté
    }
}

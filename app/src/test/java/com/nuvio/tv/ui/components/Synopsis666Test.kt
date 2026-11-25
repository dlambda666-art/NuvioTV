package com.nuvio.tv.ui.components

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 25/11 (David) : résumé de l'affiche ciblée sans ouvrir la fiche. */
class Synopsis666Test {
    private fun meta(name: String, desc: String?) = MetaPreview(
        id = "tt1", type = ContentType.MOVIE, name = name, poster = null, posterShape = PosterShape.POSTER,
        background = null, logo = null, description = desc, releaseInfo = "1979", imdbRating = 8.5f,
        genres = listOf("Science-fiction", "Horreur", "Thriller", "Action"), runtime = "1 h 57"
    )

    @Test fun `ligne film annee duree note`() {
        val d = Synopsis666Data.of(meta("Alien", "Un cargo spatial…"))!!
        assertEquals("Film  ·  1979  ·  1 h 57  ·  ★ 8,5", d.meta)
        assertEquals("Un cargo spatial…", d.description)
        assertEquals("Science-fiction  ·  Horreur  ·  Thriller", d.genres)
    }

    @Test fun `affiche sans titre ou en chargement - rien`() {
        assertNull(Synopsis666Data.of(meta(" ", null)))
        assertNull(Synopsis666Data.of(null as MetaPreview?))
    }

    @Test fun `resume vide - pas de ligne vide`() {
        assertNull(Synopsis666Data.of(meta("Alien", "  "))!!.description)
    }
}

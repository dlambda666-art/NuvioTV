package com.nuvio.tv.core.tools

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** 11/11 piège : « L'Odyssée » introuvable par Cinemeta (titres anglais seulement). */
class SearchFr666Test {
    @Test fun `titres francais, fiches tmdb, sans affiche ecartees`() {
        val json = JSONObject("""{"results":[
            {"id":1104000,"title":"L'Odyssée","original_title":"The Odyssey","release_date":"2026-07-15","poster_path":"/a.jpg"},
            {"id":5,"title":"Sans affiche","release_date":"2001-01-01","poster_path":null}]}""")
        val out = SearchFr666.parse(json, series = false)
        assertEquals(listOf("tmdb:1104000"), out.map { it.id })
        assertEquals("L'Odyssée", out[0].name)
        assertEquals("2026", out[0].releaseInfo)
    }

    @Test fun `series - nom francais, type serie`() {
        val json = JSONObject("""{"results":[{"id":2,"name":"La Quatrième Dimension","original_name":"The Twilight Zone","first_air_date":"1959-10-02","poster_path":"/b.jpg"}]}""")
        val out = SearchFr666.parse(json, series = true)
        assertEquals("series", out.single().rawType)
        assertEquals("La Quatrième Dimension", out.single().name)
    }
}

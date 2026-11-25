
    @Test fun `AHS - Frank recoit le vrai nom de release, pas la ligne drapeau`() {
        val rel = "American.Horror.Story.S13E01-06..MULTi.1080p.WEB-DL.H264-Mixio"
        assertEquals(rel, releaseNameForVerdict666(rel, "🗣️ 🇫🇷\n🐎 Comet666", "Comet666 1080p"))
        // pas de fichier connu : on cherche une ligne qui ressemble à une release
        assertEquals(rel, releaseNameForVerdict666(null, "🗣️ 🇫🇷\n$rel", "Comet666"))
        assertEquals("", releaseNameForVerdict666(null, "🗣️ 🇫🇷\n🐎 Comet666", "Comet666 1080p"))
    }

    @Test fun `fichier menteur garde 180 jours puis oublie`() {
        val today = 1000L
        val kept = NoFrench666.pruned(listOf("$mixio|1000", "$good|${today - 200}", "$mixio|990"), today)
        assertEquals(listOf("$mixio|1000"), kept)
    }
}

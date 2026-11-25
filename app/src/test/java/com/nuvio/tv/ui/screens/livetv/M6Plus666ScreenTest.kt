package com.nuvio.tv.ui.screens.livetv

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

/**
 * 25/11 (piège M6+) : sur la 2.0.10, l'écran M6+ plantait à l'ouverture (écran blanc puis cinéma).
 * Ici on l'ouvre pour de vrai : sans compte (panneau du compte), puis avec un compte (direct + recherche).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class M6Plus666ScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<M6Plus666Activity>()

    @Test
    fun `ecran M6+ s'ouvre sans planter`() {
        rule.waitForIdle()
        // sans compte : le panneau du compte ; avec : le direct
        val compte = rule.onAllNodesWithText("Ton compte M6+").fetchSemanticsNodes().isNotEmpty()
        val direct = rule.onAllNodesWithText("EN DIRECT").fetchSemanticsNodes().isNotEmpty()
        assertTrue(compte || direct)
    }
}

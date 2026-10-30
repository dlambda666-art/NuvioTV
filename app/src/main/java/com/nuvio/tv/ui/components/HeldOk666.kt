package com.nuvio.tv.ui.components

import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent

/**
 * 666TVF : une fenêtre ouverte par un appui long sur OK ne doit pas réagir au même appui
 * (OK encore enfoncé, puis relâché) — sinon elle se ferme toute seule ou valide trop vite (30/10).
 * Elle n'écoute OK qu'après le relâchement, ou après un nouvel appui.
 */
object HeldOk666 {
    const val FRESH_PRESS_MS = 350L

    private fun isOk(code: Int) = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
        code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A

    /** Règle pure (testée) : faut-il avaler cet événement ? Met à jour [armed]. */
    fun swallow(state: BooleanArray, keyCode: Int, isDown: Boolean, repeatCount: Int, sinceOpenMs: Long): Boolean {
        if (!isOk(keyCode) || state[0]) return false
        if (!isDown) { state[0] = true; return true }           // relâchement de l'appui long
        if (repeatCount == 0 && sinceOpenMs > FRESH_PRESS_MS) { state[0] = true; return false } // nouvel appui
        return true                                              // OK toujours enfoncé
    }
}

@Composable
fun Modifier.ignoreHeldOk666(): Modifier {
    val state = remember { BooleanArray(1) }
    val openedAt = remember { SystemClock.uptimeMillis() }
    return this.onPreviewKeyEvent { e ->
        val k = e.nativeKeyEvent
        HeldOk666.swallow(state, k.keyCode, k.action == KeyEvent.ACTION_DOWN, k.repeatCount, SystemClock.uptimeMillis() - openedAt)
    }
}

package com.nuvio.tv.ui.screens.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import com.nuvio.tv.R
import kotlinx.coroutines.delay

/**
 * 666TVF : les portes de l'horreur s'ouvrent toutes seules (29/10). Quand tu restes sur une
 * porte jusqu'à ce que la dalle soit levée, tu entres dans le cimetière sans appuyer sur OK.
 * Seulement les portes ; on passe d'une porte à l'autre sans rien déclencher.
 */
object Door666 {
    private const val PREFS = "666tv_portes"
    /** Fin de l'ouverture de la dalle dans le GIF (tremblement + lever). */
    const val OPEN_MS = 4_600L

    @Volatile var autoEnter: Boolean = true
        private set

    fun init(context: Context) {
        autoEnter = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("auto", true)
    }

    fun setAutoEnter(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("auto", value).apply()
        autoEnter = value
    }

    /** Vrai si la tuile est une porte de l'horreur (son animation est celle de la dalle). */
    fun isDoor(focusGifUrl: String?): Boolean =
        focusGifUrl?.endsWith("/" + R.raw.anim_porte_666) == true
}

/** À placer dans une tuile : entre tout seul quand la porte est ouverte. */
@Composable
fun DoorAutoEnter666(isFocused: Boolean, focusGifUrl: String?, onEnter: () -> Unit) {
    val enter = rememberUpdatedState(onEnter)
    LaunchedEffect(isFocused, focusGifUrl) {
        if (!isFocused || !Door666.autoEnter || !Door666.isDoor(focusGifUrl)) return@LaunchedEffect
        delay(Door666.OPEN_MS)
        enter.value()
    }
}

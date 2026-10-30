package com.nuvio.tv.ui.screens.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

    /** Début du lever de la dalle dans le GIF : le fond commence alors à passer au cimetière. */
    const val LIFT_MS = 600L  // + fondu de 4 s = entrée à OPEN_MS

    /**
     * 666TVF : vrai pendant qu'une porte s'ouvre. Le fond de l'accueil glisse alors vers le
     * cimetière (fondu enchaîné) : en entrant, le cimetière est déjà là, plus d'attente (29/10).
     */
    var opening by androidx.compose.runtime.mutableStateOf(false)

    /** Vrai si la tuile est une porte de l'horreur (son animation est celle de la dalle). */
    fun isDoor(focusGifUrl: String?): Boolean =
        focusGifUrl?.endsWith("/" + R.raw.anim_porte_666) == true
}

/** À placer dans une tuile : entre tout seul quand la porte est ouverte. */
@Composable
fun DoorAutoEnter666(isFocused: Boolean, focusGifUrl: String?, onEnter: () -> Unit) {
    val enter = rememberUpdatedState(onEnter)
    LaunchedEffect(isFocused, focusGifUrl) {
        if (!isFocused || !Door666.isDoor(focusGifUrl)) return@LaunchedEffect
        try {
            delay(Door666.LIFT_MS)
            Door666.opening = true                    // le fond passe au cimetière pendant que la dalle monte
            if (!Door666.autoEnter) return@LaunchedEffect
            delay(Door666.OPEN_MS - Door666.LIFT_MS)
            enter.value()
        } finally {
            if (!isFocused) Door666.opening = false
        }
    }
    // la porte n'est plus sélectionnée : retour au fond normal
    LaunchedEffect(isFocused) { if (!isFocused && Door666.isDoor(focusGifUrl)) Door666.opening = false }
}

/** Durée du fondu vers le cimetière : de la levée de la dalle jusqu'à l'entrée (4 s, linéaire). */
const val DOOR_FADE_MS_666 = 4_000

/**
 * 666TVF : le cimetière apparaît doucement derrière les tuiles pendant qu'une porte s'ouvre
 * (dispositions Classique et Grille ; la Moderne le fait dans son bandeau). 30/10.
 */
@Composable
fun DoorFade666(modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (Door666.opening) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = if (Door666.opening) DOOR_FADE_MS_666 else 300,
            easing = androidx.compose.animation.core.LinearEasing
        ),
        label = "doorFade666"
    )
    if (alpha <= 0f) return
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(R.drawable.bg_cimetiere_666),
        contentDescription = null,
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        alpha = alpha,
        modifier = modifier
    )
}

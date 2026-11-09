package com.nuvio.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.nuvio.tv.R

/**
 * 666TVF (09/11) : le badge vert « VF » que Frank collait sur les affiches, maintenant posé par
 * l'appli sur tout titre confirmé en VF. Même place que chez Frank : à gauche, sous le bandeau du
 * haut des affiches BetterPosters (10,5 % de la hauteur), 6,5 % de haut.
 */
@Composable
fun BoxScope.VfBadge666(id: String, imdbId: String?) {
    val vf = remember(id, imdbId) { com.nuvio.tv.core.tools.VfCheck666.isKnownVf(id, imdbId) }
    if (!vf) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.badge_vf_666),
            contentDescription = "VF",
            contentScale = ContentScale.FillHeight,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = maxWidth * 0.035f, top = maxHeight * 0.105f)
                .height(maxHeight * 0.065f)
        )
    }
}

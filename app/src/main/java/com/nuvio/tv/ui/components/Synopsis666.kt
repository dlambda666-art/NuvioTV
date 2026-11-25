package com.nuvio.tv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview

/**
 * 666TV (David, 25/11) : le résumé de l'affiche ciblée, sans entrer dans la fiche.
 * Cimetière, pages de catégories, recherche. Tout en vert (titre Creepster), genres en mauve.
 * L'Abattoir reste dégagé (22/11) : on ne l'y affiche pas.
 */
@Immutable
data class Synopsis666Data(
    val title: String,
    val meta: String,
    val description: String?,
    val genres: String
) {
    companion object {
        fun of(
            title: String?,
            isSeries: Boolean,
            year: String?,
            runtime: String?,
            rating: String?,
            description: String?,
            genres: List<String>
        ): Synopsis666Data? {
            val t = title?.trim().orEmpty()
            if (t.isEmpty()) return null
            val meta = listOfNotNull(
                if (isSeries) "Série" else "Film",
                year?.takeIf { it.isNotBlank() },
                runtime?.takeIf { it.isNotBlank() },
                rating?.takeIf { it.isNotBlank() }?.let { "★ " + it.replace('.', ',') }
            ).joinToString("  ·  ")
            return Synopsis666Data(t, meta, description?.trim()?.takeIf { it.isNotEmpty() }, genres.take(3).joinToString("  ·  "))
        }

        fun of(item: MetaPreview?): Synopsis666Data? = item?.let {
            of(
                title = it.name,
                isSeries = it.type == ContentType.SERIES,
                year = (it.releaseInfo ?: it.released)?.let { r -> Regex("\\d{4}").find(r)?.value },
                runtime = it.runtime,
                rating = it.imdbRating?.let { r -> String.format(java.util.Locale.US, "%.1f", r) },
                description = it.description,
                genres = it.genres
            )
        }
    }
}

private val shadow666 = Shadow(Color(0xE6000000), Offset(0f, 2f), 8f)
private val textGreen666 = Color(0xFF9BE8A3)
private val mauve666 = Color(0xFFB98BE0)

@Composable
fun Synopsis666(data: Synopsis666Data?, modifier: Modifier = Modifier, lines: Int = 3) {
    AnimatedContent(
        targetState = data,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
        label = "synopsis666",
        modifier = modifier
    ) { d ->
        if (d == null) return@AnimatedContent
        Column {
            Text(
                d.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = Font666.Creepster, fontSize = 40.sp, color = Font666.Green, shadow = Shadow(Color(0xCC1FAF3A), Offset.Zero, 10f))
            )
            if (d.meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(d.meta, maxLines = 1, style = TextStyle(fontSize = 16.sp, color = textGreen666, shadow = shadow666))
            }
            d.description?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    maxLines = lines,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 16.sp, lineHeight = 23.sp, color = textGreen666, shadow = shadow666)
                )
            }
            if (d.genres.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(d.genres, maxLines = 1, style = TextStyle(fontSize = 13.sp, color = mauve666, shadow = shadow666))
            }
        }
    }
}

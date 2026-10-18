package com.nuvio.tv.ui.screens.livetv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * 666TV : « TV en direct ». Raccourcis vers les applis officielles des chaînes belges
 * (leurs directs sont protégés et ne se lisent que dans leurs propres applis).
 * L'appli est retrouvée par son nom parmi les applis installées sur la Shield.
 */
private data class LiveTvApp(
    val title: String,
    val channels: String,
    val labelMatches: List<String>,
    val storeQuery: String,
    /** 666TV : image de tuile intégrée (sinon la bannière de l'appli). */
    val tileRes: Int? = null
)

// 666TV : un seul raccourci, Orange TV (toutes les chaînes de l'abonnement, RTLplay et Auvio inclus).
private val LIVE_TV_APPS = listOf(
    LiveTvApp("Orange TV", "Toutes tes chaînes · RTLplay et Auvio inclus", listOf("orange tv", "orange"), "Orange TV Belgium", tileRes = com.nuvio.tv.R.drawable.tile_live_tv_666),
    LiveTvApp("TizenTube", "YouTube sans pub", listOf("tizentube", "tizen tube"), "TizenTube", tileRes = com.nuvio.tv.R.drawable.tile_tizentube_666)
)

private data class InstalledApp(val packageName: String, val banner: Drawable?)

private fun findInstalledApp(context: Context, app: LiveTvApp): InstalledApp? {
    val pm = context.packageManager
    val intents = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).flatMap { category ->
        runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
        }.getOrDefault(emptyList())
    }
    val match = intents.firstOrNull { info ->
        val label = info.loadLabel(pm).toString().lowercase().replace(" ", " ")
        app.labelMatches.any { label.contains(it) }
    } ?: return null
    val pkg = match.activityInfo.packageName
    val banner = runCatching { pm.getApplicationBanner(pkg) }.getOrNull()
        ?: runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
    return InstalledApp(pkg, banner)
}

private fun launch(context: Context, app: LiveTvApp, installed: InstalledApp?) {
    val pm = context.packageManager
    val intent = installed?.let {
        pm.getLeanbackLaunchIntentForPackage(it.packageName) ?: pm.getLaunchIntentForPackage(it.packageName)
    } ?: Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(app.storeQuery)))
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun LiveTvScreen() {
    val context = LocalContext.current
    val installed = remember { LIVE_TV_APPS.map { findInstalledApp(context, it) } }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)
    ) {
        Text(
            text = "TV en direct",
            style = MaterialTheme.typography.headlineMedium,
            color = NuvioTheme.colors.TextPrimary
        )
        Text(
            text = "Tes chaînes avec Orange TV, et YouTube sans pub avec TizenTube.",
            style = MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.TextSecondary
        )
        Spacer(Modifier.height(NuvioTheme.spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            LIVE_TV_APPS.forEachIndexed { index, app ->
                LiveTvCard(
                    app = app,
                    installed = installed[index],
                    onClick = { launch(context, app, installed[index]) },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                )
            }
        }
    }
}

@Composable
private fun LiveTvCard(
    app: LiveTvApp,
    installed: InstalledApp?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.width(360.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundCard,
            focusedContainerColor = NuvioTheme.colors.BackgroundElevated
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border), shape = shape),
            focusedBorder = Border(border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs), shape = shape)
        ),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = remember(installed) {
                    installed?.banner?.let { runCatching { it.toBitmap(640, 360) }.getOrNull() }
                }
                if (app.tileRes != null) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(app.tileRes),
                        contentDescription = app.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = app.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "📺",
                        style = MaterialTheme.typography.displayMedium
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = app.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = NuvioTheme.colors.TextPrimary
            )
            Text(
                text = app.channels,
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextSecondary
            )
            if (installed == null) {
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "Appli pas trouvée : appuie pour l'installer",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextSecondary
                )
            }
        }
    }
}

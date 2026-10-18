package com.nuvio.tv.ui.screens.livetv

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 666TV : « Mes applis » — toutes les applis de la Shield, pour utiliser 666TV comme launcher.
 * Les raccourcis de « TV en direct » (Orange TV, TizenTube, Cache Cleaner) n'y figurent pas.
 */
private val Green666 = Color(0xFF49DD89)
private val Blood666 = Color(0xFFC3121F)
private val BloodHi666 = Color(0xFFFF3A45)

private data class ShieldApp(val label: String, val launch: Intent, val banner: Drawable?, val isBanner: Boolean)

private val HIDDEN_LABELS = listOf("orange", "tizentube", "tizen tube", "tduk", "cache cleaner", "accueil d'android tv")

private fun loadApps(context: Context): List<ShieldApp> {
    val pm = context.packageManager
    val found = linkedMapOf<String, ShieldApp>()
    listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER).forEach { category ->
        val infos = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)
        }.getOrDefault(emptyList())
        infos.forEach { info ->
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName || found.containsKey(pkg)) return@forEach
            val label = info.loadLabel(pm).toString()
            if (HIDDEN_LABELS.any { label.lowercase().contains(it) }) return@forEach
            val launch = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return@forEach
            val banner = runCatching { info.activityInfo.loadBanner(pm) }.getOrNull()
                ?: runCatching { pm.getApplicationBanner(pkg) }.getOrNull()
            val icon = if (banner == null) runCatching { info.loadIcon(pm) }.getOrNull() else null
            found[pkg] = ShieldApp(label, launch, banner ?: icon, banner != null)
        }
    }
    val settings = Intent(android.provider.Settings.ACTION_SETTINGS)
    val list = found.values.sortedBy { it.label.lowercase() }.toMutableList()
    if (list.none { it.label.contains("Paramètres", true) || it.label.contains("Settings", true) }) {
        list.add(0, ShieldApp("Paramètres Shield", settings, null, false))
    }
    return list
}

@Composable
fun MyAppsScreen() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<ShieldApp>?>(null) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(context) } }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(apps) { if (!apps.isNullOrEmpty()) runCatching { firstFocus.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "MES APPLIS",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Black,
            color = Blood666
        )
        Text(
            text = "Toutes les applis de ta Shield, hors raccourcis TV en direct",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFC8C3BE)
        )
        val list = apps ?: return@Column
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(list, key = { _, app -> app.label + app.launch.`package` }) { index, app ->
                AppTile(
                    app = app,
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = {
                        runCatching { context.startActivity(Intent(app.launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                )
            }
        }
    }
}

@Composable
private fun AppTile(app: ShieldApp, modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onFocusChanged { focused = it.hasFocus },
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF1A1F1D),
                focusedContainerColor = Color(0xFF1A1F1D)
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color(0xFF2C3330)), shape = shape),
                focusedBorder = Border(BorderStroke(3.dp, BloodHi666), shape = shape)
            ),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = Blood666, elevation = 14.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val bitmap = remember(app) {
                    app.banner?.let { d ->
                        runCatching { if (app.isBanner) d.toBitmap(320, 180) else d.toBitmap(128, 128) }.getOrNull()
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = app.label,
                        contentScale = if (app.isBanner) ContentScale.Crop else ContentScale.Fit,
                        modifier = if (app.isBanner) Modifier.fillMaxSize() else Modifier.fillMaxSize().padding(18.dp)
                    )
                } else {
                    Text(
                        text = app.label.split(" ").take(2).joinToString("") { it.take(1) }.uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (focused) Color.White else Green666,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

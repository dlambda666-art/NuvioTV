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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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

private data class ShieldApp(val label: String, val launch: Intent, val icon: Drawable?, val banner: Drawable?) {
    val key: String get() = launch.`package` ?: launch.component?.packageName ?: label
}

private const val ORDER_PREFS = "666tv_mes_applis"
private fun loadOrder(context: Context): List<String> =
    context.getSharedPreferences(ORDER_PREFS, Context.MODE_PRIVATE).getString("order", null)
        ?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
private fun saveOrder(context: Context, keys: List<String>) {
    context.getSharedPreferences(ORDER_PREFS, Context.MODE_PRIVATE).edit().putString("order", keys.joinToString("|")).apply()
}
/** Ordre choisi par l'utilisateur d'abord, puis les nouvelles applis par ordre alphabétique. */
private fun applyOrder(apps: List<ShieldApp>, order: List<String>): List<ShieldApp> {
    val byKey = apps.associateBy { it.key }
    val ordered = order.mapNotNull { byKey[it] }
    return ordered + apps.filter { it.key !in order }
}

/** Plaques d'horreur (pierre, bois, rouille, écailles) posées sous chaque appli. */
private val PLATES = listOf(
    listOf(com.nuvio.tv.R.drawable.plate_666_pierre_1, com.nuvio.tv.R.drawable.plate_666_pierre_2, com.nuvio.tv.R.drawable.plate_666_pierre_3),
    listOf(com.nuvio.tv.R.drawable.plate_666_bois_1, com.nuvio.tv.R.drawable.plate_666_bois_2, com.nuvio.tv.R.drawable.plate_666_bois_3),
    listOf(com.nuvio.tv.R.drawable.plate_666_rouille_1, com.nuvio.tv.R.drawable.plate_666_rouille_2, com.nuvio.tv.R.drawable.plate_666_rouille_3),
    listOf(com.nuvio.tv.R.drawable.plate_666_ecailles_1, com.nuvio.tv.R.drawable.plate_666_ecailles_2, com.nuvio.tv.R.drawable.plate_666_ecailles_3)
)

/** Deux voisines (à côté ou au-dessus) n'ont jamais la même matière. */
private fun plateFor(index: Int): Int {
    val row = index / 4
    val texture = (index + row) % 4
    return PLATES[texture][row % 3]
}

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
            val icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
            found[pkg] = ShieldApp(label, launch, icon, banner)
        }
    }
    val settings = Intent(android.provider.Settings.ACTION_SETTINGS)
    val list = found.values.sortedBy { it.label.lowercase() }.toMutableList()
    if (list.none { it.label.contains("Paramètres", true) || it.label.contains("Settings", true) }) {
        list.add(0, ShieldApp("Paramètres Shield", settings, null, null))
    }
    // 666TV : outils maison (état, sauvegarde, mode léger…), toujours en premier.
    list.add(0, ShieldApp(TOOLS_LABEL, Intent(TOOLS_ACTION).setPackage(context.packageName), null, null))
    return list
}

private const val TOOLS_LABEL = "Outils 666"
private const val TOOLS_ACTION = "com.nuvio.tv.OUTILS_666"

@Composable
fun MyAppsScreen(onOpenTools: () -> Unit = {}) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<ShieldApp>?>(null) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { applyOrder(loadApps(context), loadOrder(context)) } }
    // Appui long sur OK : l'appli « se décolle » et se déplace avec les flèches ; OK ou Retour pour la poser.
    var movingKey by remember { mutableStateOf<String?>(null) }
    val tileFocus = remember { mutableMapOf<String, FocusRequester>() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(apps) { if (!apps.isNullOrEmpty()) runCatching { firstFocus.requestFocus() } }

    Box(modifier = Modifier.fillMaxSize()) {
    // Ton fond horreur, assombri, derrière les plaques.
    Image(
        painter = androidx.compose.ui.res.painterResource(com.nuvio.tv.R.drawable.fond_collections_666),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = 0.22f,
        modifier = Modifier.fillMaxSize()
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "MES APPLIS",
            style = com.nuvio.tv.ui.components.Font666.horror(MaterialTheme.typography.headlineLarge, 1.15f),
            color = com.nuvio.tv.ui.components.Font666.Green
        )
        Text(
            text = if (movingKey != null) "Déplace l'appli avec les flèches, puis OK pour la poser"
            else "Toutes les applis de ta Shield · appui long sur OK pour changer l'ordre",
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
            itemsIndexed(list, key = { _, app -> app.key }) { index, app ->
                val fr = tileFocus.getOrPut(app.key) { FocusRequester() }
                val moving = movingKey == app.key
                LaunchedEffect(index, moving) { if (moving) runCatching { fr.requestFocus() } }
                AppTile(
                    app = app,
                    plateRes = plateFor(index),
                    moving = moving,
                    modifier = Modifier
                        .focusRequester(fr)
                        .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .onPreviewKeyEvent { event ->
                            if (!moving) return@onPreviewKeyEvent false
                            if (event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent true
                            // Répétitions du OK maintenu (l'appui long qui vient de lancer le déplacement) : ignorées.
                            if (event.nativeKeyEvent.repeatCount > 0 &&
                                (event.key == androidx.compose.ui.input.key.Key.DirectionCenter || event.key == androidx.compose.ui.input.key.Key.Enter)
                            ) return@onPreviewKeyEvent true
                            val delta = when (event.key) {
                                androidx.compose.ui.input.key.Key.DirectionLeft -> -1
                                androidx.compose.ui.input.key.Key.DirectionRight -> 1
                                androidx.compose.ui.input.key.Key.DirectionUp -> -4
                                androidx.compose.ui.input.key.Key.DirectionDown -> 4
                                else -> 0
                            }
                            if (delta != 0) {
                                val target = (index + delta).coerceIn(0, list.lastIndex)
                                if (target != index) {
                                    val newList = list.toMutableList().apply { add(target, removeAt(index)) }
                                    apps = newList
                                    saveOrder(context, newList.map { it.key })
                                }
                                return@onPreviewKeyEvent true
                            }
                            // OK, Entrée ou Retour : on pose l'appli.
                            movingKey = null
                            true
                        },
                    onLongClick = { movingKey = app.key },
                    onClick = {
                        if (app.launch.action == TOOLS_ACTION) onOpenTools()
                        else runCatching { context.startActivity(Intent(app.launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                )
            }
        }
    }
    }
}

@Composable
private fun AppTile(app: ShieldApp, plateRes: Int, moving: Boolean, modifier: Modifier, onLongClick: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onFocusChanged { focused = it.hasFocus },
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0xFF0C0F0E),
                focusedContainerColor = Color(0xFF0C0F0E)
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border.None,
                focusedBorder = Border(BorderStroke(if (moving) 5.dp else 3.dp, if (moving) Green666 else BloodHi666), shape = shape)
            ),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = Blood666, elevation = 14.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = if (moving) 1.12f else 1.06f)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(plateRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Le logo de l'appli au centre de la plaque (sinon sa bannière en petit, sinon ses initiales).
                val bitmap = remember(app) {
                    (app.icon ?: app.banner)?.let { d ->
                        runCatching { if (app.icon != null) d.toBitmap(160, 160) else d.toBitmap(320, 180) }.getOrNull()
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = app.label,
                        contentScale = ContentScale.Fit,
                        modifier = if (app.icon != null) Modifier.fillMaxSize().padding(vertical = 22.dp)
                        else Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 30.dp)
                    )
                } else {
                    Text(
                        text = app.label.split(" ").take(2).joinToString("") { it.take(1) }.uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = com.nuvio.tv.ui.components.Font666.Green
                    )
                }
            }
        }
        Text(
            text = app.label,
            style = if (focused) MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            else com.nuvio.tv.ui.components.Font666.horror(MaterialTheme.typography.bodyMedium, 1.15f),
            color = if (focused) Color.White else com.nuvio.tv.ui.components.Font666.Green,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

package com.nuvio.tv.ui.screens.livetv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.core.tools.Record666
import kotlinx.coroutines.delay

private val RGreen = Color(0xFF4CC36B)
private val RMauve = Color(0xFF9B30D9)
private val RMauveLight = Color(0xFFB57BE0)
private val RGrey = Color(0xFFC8C3BE)
private val RDim = Color(0xFF8F8A85)

/** 666TVF : les films et épisodes enregistrés (Shield du bas), lisibles même sans internet. */
@Composable
fun Records666Screen(onPlay: (Record666.Rec, Boolean) -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Record666.init(context) }
    val items by Record666.items.collectAsState()
    val live by Record666.live.collectAsState()
    var place by remember { mutableStateOf(Record666.place(context)) }
    var menuFor by remember { mutableStateOf<Record666.Rec?>(null) }
    LaunchedEffect(Unit) {
        while (true) { delay(5_000); place = Record666.place(context) }
    }
    val ongoing = items.filter { it.status == Record666.Status.QUEUED || it.status == Record666.Status.RUNNING || it.status == Record666.Status.FAILED }
        .sortedBy { it.addedAt }
    val ready = items.filter { it.status == Record666.Status.DONE }.sortedByDescending { it.addedAt }
    val first = remember { FocusRequester() }
    LaunchedEffect(ongoing.isEmpty() && ready.isEmpty()) { runCatching { first.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = androidx.compose.ui.res.painterResource(com.nuvio.tv.R.drawable.fond_collections_666),
            contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.14f, modifier = Modifier.fillMaxSize()
        )
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 56.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 30.dp)) {
            item {
                Text("ENREGISTREMENTS", fontSize = 40.sp, fontWeight = FontWeight.Black, color = RGreen)
                val p = place
                Text(
                    if (p == null) "Pas de stockage disponible"
                    else "${p.label} · ${Record666.gb(p.free)} libres sur ${Record666.gb(p.totalSpace)}" +
                        if (p.maxFile < Long.MAX_VALUE) " · fichiers de 4 Go maximum" else "",
                    color = RGrey, fontSize = 16.sp
                )
                if (p != null && p.totalSpace > 0) {
                    Bar(1f - p.free.toFloat() / p.totalSpace, Modifier.width(520.dp).padding(top = 10.dp))
                }
                if (!Record666.enabledHere()) {
                    Text("Les enregistrements se font sur la Shield du bas.", color = RDim, fontSize = 16.sp, modifier = Modifier.padding(top = 30.dp))
                } else if (ongoing.isEmpty() && ready.isEmpty()) {
                    Text(
                        "Rien d'enregistré pour l'instant.\nDans la liste des liens d'un film ou d'un épisode : appui long sur OK → « Enregistrer ».",
                        color = RGrey, fontSize = 18.sp, modifier = Modifier.padding(top = 30.dp)
                    )
                }
            }
            if (ongoing.isNotEmpty()) {
                item { Section("En cours") }
                items(ongoing, key = { "o_" + it.id }) { r ->
                    OngoingCard(
                        r, live[r.id],
                        modifier = if (r == ongoing.first()) Modifier.focusRequester(first) else Modifier,
                        onClick = { if (r.status == Record666.Status.FAILED) Record666.retry(context, r.id) else menuFor = r },
                        onLongClick = { menuFor = r }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (ready.isNotEmpty()) {
                item { Section("Prêts à regarder, même sans internet") }
                val rows = ready.chunked(6)
                items(rows.size, key = { "r_$it" }) { i ->
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                        rows[i].forEach { r ->
                            ReadyCard(
                                r,
                                modifier = if (ongoing.isEmpty() && i == 0 && r == rows[0].first()) Modifier.focusRequester(first) else Modifier,
                                onClick = { if (r.file.exists()) onPlay(r, false) else menuFor = r },
                                onLongClick = { menuFor = r }
                            )
                        }
                    }
                }
                item {
                    Text("OK : regarder · appui long sur OK : reprendre du début / supprimer", color = RDim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        menuFor?.let { r ->
            RecordMenu(
                r,
                onPlayFromStart = { menuFor = null; onPlay(r, true) },
                onRetry = { menuFor = null; Record666.retry(context, r.id) },
                onDelete = { menuFor = null; Record666.delete(context, r.id) },
                onDismiss = { menuFor = null }
            )
        }
    }
}

@Composable
private fun Section(t: String) {
    Text(t, color = RMauve, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 22.dp, bottom = 10.dp))
}

@Composable
private fun Bar(frac: Float, modifier: Modifier) {
    Box(modifier.height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF2A2626))) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f))
                .background(Brush.horizontalGradient(listOf(RGreen, RMauve)))
        )
    }
}

private fun posterModel(r: Record666.Rec): Any? = if (r.posterFile.exists()) android.net.Uri.fromFile(r.posterFile).toString() else r.posterUrl

@Composable
private fun OngoingCard(r: Record666.Rec, l: Record666.Live?, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.width(820.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xE61C1A1A), focusedContainerColor = Color(0xFF2A2230)),
        border = ClickableSurfaceDefaults.border(focusedBorder = androidx.tv.material3.Border(BorderStroke(2.dp, RMauve), shape = shape)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = posterModel(r), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(70.dp, 104.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2626))
            )
            Spacer(Modifier.width(18.dp))
            Column {
                Text(r.label, color = Color(0xFFF2EFE9), fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val total = l?.total?.takeIf { it > 0 } ?: r.total
                val bytes = l?.bytes ?: r.partFile.length()
                val parts = buildList {
                    if (r.quality.isNotBlank()) add(r.quality)
                    if (total > 0) add("${Record666.gb(bytes)} / ${Record666.gb(total)}") else if (bytes > 0) add(Record666.gb(bytes))
                    if (r.status == Record666.Status.RUNNING && l != null && l.note == null && l.bytesPerSec > 0) {
                        add(String.format(java.util.Locale.FRANCE, "%.1f Mo/s", l.bytesPerSec / 1048576.0))
                        if (total > 0) add("encore ~${((total - bytes) / l.bytesPerSec / 60).coerceAtLeast(1)} min")
                    }
                    if (r.status == Record666.Status.QUEUED) add("en file d'attente")
                }
                Text(parts.joinToString(" · "), color = RGrey, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                if (total > 0) Bar(bytes.toFloat() / total, Modifier.width(560.dp).padding(top = 10.dp))
                when {
                    r.status == Record666.Status.FAILED ->
                        Text("${r.error ?: "Échec"} · OK pour réessayer", color = Color(0xFFE05A5A), fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                    l?.note != null ->
                        Text(l.note, color = RMauveLight, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                    else ->
                        Text("⏸ Se met en pause tout seul quand tu regardes un film", color = RMauveLight, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun ReadyCard(r: Record666.Rec, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val missing = !r.file.exists()
    Column(Modifier.width(150.dp)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier.size(150.dp, 222.dp),
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF2A2626), focusedContainerColor = Color(0xFF2A2626)),
            border = ClickableSurfaceDefaults.border(focusedBorder = androidx.tv.material3.Border(BorderStroke(3.dp, RMauve), shape = shape)),
            glow = androidx.tv.material3.ClickableSurfaceDefaults.glow(focusedGlow = androidx.tv.material3.Glow(RMauve.copy(alpha = 0.6f), 14.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(model = posterModel(r), contentDescription = r.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (missing) {
                    Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
                        Text("Clé débranchée", color = RGrey, fontSize = 14.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Text(r.label, color = Color(0xFFF2EFE9), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(
            listOfNotNull(r.total.takeIf { it > 0 }?.let { Record666.gb(it) }, r.quality.ifBlank { null }).joinToString(" · "),
            color = RDim, fontSize = 13.sp
        )
    }
}

@Composable
private fun RecordMenu(r: Record666.Rec, onPlayFromStart: () -> Unit, onRetry: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(480.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF1C1A1A))
                .border(2.dp, RMauve, RoundedCornerShape(16.dp)).padding(24.dp)
        ) {
            Text(r.label, color = Color(0xFFF2EFE9), fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Spacer(Modifier.height(16.dp))
            var firstUsed = false
            fun Modifier.firstOnce(): Modifier = if (!firstUsed) { firstUsed = true; this.focusRequester(first) } else this
            val colors = ButtonDefaults.colors(
                containerColor = Color(0xFF2A2626), contentColor = Color(0xFFF2EFE9),
                focusedContainerColor = RMauve, focusedContentColor = Color.White
            )
            if (r.status == Record666.Status.DONE && r.file.exists()) {
                Button(onClick = onPlayFromStart, colors = colors, modifier = Modifier.fillMaxWidth().firstOnce()) {
                    Text("▶  Reprendre du début", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(10.dp))
            }
            if (r.status == Record666.Status.FAILED) {
                Button(onClick = onRetry, colors = colors, modifier = Modifier.fillMaxWidth().firstOnce()) {
                    Text("↻  Réessayer", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(10.dp))
            }
            Button(onClick = onDelete, colors = colors, modifier = Modifier.fillMaxWidth().firstOnce()) {
                Text(if (r.status == Record666.Status.DONE) "🗑  Supprimer" else "✕  Arrêter et supprimer", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onDismiss, colors = colors, modifier = Modifier.fillMaxWidth()) {
                Text("Annuler", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}

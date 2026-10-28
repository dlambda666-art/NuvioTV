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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.domain.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

private val RGreen = Color(0xFF4CC36B)
private val RMauve = Color(0xFF9B30D9)

/** Un film de la roulette (toujours en VF : il vient des catalogues VF de Frank). */
data class RouletteFilm(val id: String, val name: String, val poster: String?, val year: String?, val info: String?)

@HiltViewModel
class Roulette666ViewModel @Inject constructor(
    private val addonRepository: AddonRepository
) : ViewModel() {
    private val _films = MutableStateFlow<List<RouletteFilm>?>(null)
    val films: StateFlow<List<RouletteFilm>?> = _films
    var frankBase: String? = null
        private set

    init {
        viewModelScope.launch {
            val addons = withTimeoutOrNull(5_000) { addonRepository.getInstalledAddons().first() }.orEmpty()
            val frank = addons.firstOrNull { it.baseUrl.contains("frankenstream", ignoreCase = true) }
            frankBase = frank?.baseUrl?.trimEnd('/')
            _films.value = withContext(Dispatchers.IO) {
                val base = frankBase ?: return@withContext emptyList()
                listOf("fs-horreur-classiques", "fs-pour-toi").flatMap { load(base, it) }.distinctBy { it.id }.shuffled()
            }
        }
    }

    private fun load(base: String, catalog: String): List<RouletteFilm> = runCatching {
        val raw = com.nuvio.tv.core.tools.Http666.get("$base/catalog/movie/$catalog.json", 15_000) ?: return emptyList()
        val arr = JSONObject(raw).optJSONArray("metas") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val m = arr.getJSONObject(i)
            val id = m.optString("id").takeIf { it.startsWith("tt") } ?: return@mapNotNull null
            RouletteFilm(
                id = id,
                name = cleanName(m.optString("name")),
                poster = m.optString("poster").takeIf { it.startsWith("http") },
                year = m.optString("releaseInfo").takeIf { it.isNotBlank() }?.take(4),
                info = m.optString("description").takeIf { it.isNotBlank() }
            )
        }
    }.getOrDefault(emptyList())
}

/** « [VF] The Thing » → « The Thing ». */
internal fun cleanName(raw: String): String = raw.replace(Regex("^\\s*\\[[^\\]]*\\]\\s*"), "").trim()

/** Délais de la roue : rapide au départ, puis de plus en plus lent jusqu'à l'arrêt. */
internal fun rouletteDelays(steps: Int = 28): List<Long> = (0 until steps).map { i ->
    val x = i.toDouble() / (steps - 1)
    (45 + 380 * x * x * x).toLong()
}

/** 666TVF : la roulette de l'horreur — un film au hasard, toujours en VF. */
@Composable
fun Roulette666Screen(
    onOpenDetail: (itemId: String, itemType: String, addonBaseUrl: String?) -> Unit,
    viewModel: Roulette666ViewModel = hiltViewModel()
) {
    val filmsState by viewModel.films.let { flow -> androidx.compose.runtime.produceState<List<RouletteFilm>?>(null, flow) { flow.collect { value = it } } }
    val films = filmsState
    var index by remember { mutableIntStateOf(0) }
    var spinning by remember { mutableStateOf(false) }
    var spins by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val open = remember { FocusRequester() }

    fun spin() {
        val list = films ?: return
        if (list.isEmpty() || spinning) return
        spinning = true
        scope.launch {
            val target = (0 until list.size).random()
            val delays = rouletteDelays()
            val start = index
            val total = delays.size
            for ((n, d) in delays.withIndex()) {
                // on finit pile sur le film tiré
                index = ((start + n + 1) + (target - (start + total)).mod(list.size)).mod(list.size)
                delay(d)
            }
            index = target
            spinning = false
            spins++
            runCatching { open.requestFocus() }
        }
    }
    LaunchedEffect(films) { if (!films.isNullOrEmpty() && spins == 0) spin() }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = androidx.compose.ui.res.painterResource(com.nuvio.tv.R.drawable.fond_collections_666),
            contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.16f, modifier = Modifier.fillMaxSize()
        )
        Column(Modifier.fillMaxSize().padding(top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "ROULETTE DE L'HORREUR", fontSize = 46.sp, color = RMauve, fontWeight = FontWeight.Black,
                fontFamily = com.nuvio.tv.ui.components.Font666.Creepster
            )
            Text("Un film au hasard, toujours en VF · tes classiques de l'horreur et ton « Pour toi »", color = Color(0xFFC8C3BE), fontSize = 15.sp)
            Spacer(Modifier.height(18.dp))
            when {
                films == null -> Text("Chargement…", color = Color(0xFFC8C3BE), fontSize = 20.sp)
                films.isEmpty() -> Text("Frank ne répond pas pour l'instant", color = Color(0xFFC8C3BE), fontSize = 20.sp)
                else -> {
                    Text("▼", color = RGreen, fontSize = 26.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        for (offset in -2..2) {
                            val f = films[(index + offset).mod(films.size)]
                            val center = offset == 0
                            val w = if (center) 200.dp else 140.dp
                            val shape = RoundedCornerShape(10.dp)
                            Box(
                                Modifier.size(w, w * 1.5f).alpha(if (center) 1f else if (kotlin.math.abs(offset) == 1) 0.65f else 0.35f)
                                    .clip(shape).background(Color(0xFF1A1220))
                                    .let { if (center && !spinning) it.border(BorderStroke(3.dp, RGreen), shape) else it }
                            ) {
                                if (f.poster != null) AsyncImage(model = f.poster, contentDescription = f.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                else Text(f.name, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(8.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    val f = films[index.mod(films.size)]
                    Text(
                        if (spinning) "…" else f.name + (f.year?.let { " · $it" } ?: ""),
                        color = RGreen, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (!spinning && f.info != null) {
                        Text(
                            f.info, color = Color(0xFFC8C3BE), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(0.6f).padding(top = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        RouletteButton("▶ Voir la fiche", primary = true, modifier = Modifier.focusRequester(open)) {
                            if (!spinning) onOpenDetail(f.id, "movie", viewModel.frankBase)
                        }
                        RouletteButton("🎲 Relancer", primary = false) { spin() }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouletteButton(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) RMauve.copy(alpha = 0.55f) else Color(0x22FFFFFF),
            focusedContainerColor = RMauve,
            contentColor = Color.White,
            focusedContentColor = Color.White
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f)
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp))
    }
}

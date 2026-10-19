package com.nuvio.tv.ui.screens.livetv

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.core.tools.Backup666
import com.nuvio.tv.core.tools.Clock666
import com.nuvio.tv.core.tools.Http666
import com.nuvio.tv.core.tools.Light666
import com.nuvio.tv.data.local.PluginDataStore
import com.nuvio.tv.domain.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Blood = Color(0xFFC3121F)
private val Green666 = Color(0xFF4CC36B)
private val Orange666 = Color(0xFFE59A2B)

/** Une ligne de l'écran « État 666 ». Les adresses ne sont jamais affichées (elles contiennent tes clés). */
data class Health666(val name: String, val detail: String, val ok: Boolean?)

@HiltViewModel
class Tools666ViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val pluginDataStore: PluginDataStore
) : ViewModel() {
    private val _checks = MutableStateFlow<List<Health666>>(emptyList())
    val checks: StateFlow<List<Health666>> = _checks
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    fun runChecks() {
        if (_running.value) return
        _running.value = true
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) {
                val list = mutableListOf<suspend () -> Health666>()
                list += { webRow("Internet", "https://clients3.google.com/generate_204") }
                list += { webRow("TorBox", "https://api.torbox.app/") }
                list += { webRow("Passer le générique (IntroDB)", "https://api.introdb.app/") }
                val addons = withTimeoutOrNull(5_000) { addonRepository.getInstalledAddons().first() }.orEmpty()
                addons.forEach { a ->
                    list += {
                        val code = Http666.status(a.baseUrl.trimEnd('/') + "/manifest.json")
                        Health666("Addon · ${a.displayName}", statusText(code), code in 200..299)
                    }
                }
                val repos = withTimeoutOrNull(5_000) { pluginDataStore.repositories.first() }.orEmpty()
                val scrapers = withTimeoutOrNull(5_000) { pluginDataStore.scrapers.first() }.orEmpty()
                repos.forEach { r ->
                    list += {
                        val active = scrapers.count { it.repositoryId == r.id && it.enabled }
                        val code = Http666.status(r.url)
                        val ok = code in 200..299
                        Health666(
                            "Plugins · ${r.name}",
                            (if (ok) "Dépôt joignable" else statusText(code)) + " · $active actif(s)",
                            if (!ok) false else if (active == 0) null else true
                        )
                    }
                }
                coroutineScopeAll(list)
            }
            _checks.value = rows
            _running.value = false
        }
    }

    private suspend fun coroutineScopeAll(list: List<suspend () -> Health666>): List<Health666> =
        kotlinx.coroutines.coroutineScope { list.map { async { it() } }.awaitAll() }

    private fun webRow(name: String, url: String): Health666 {
        val code = Http666.status(url)
        // Un serveur qui répond, même par une erreur de page, est en ligne.
        val up = code in 100..499
        return Health666(name, if (up) "En ligne" else statusText(code), up)
    }

    private fun statusText(code: Int): String = when {
        code in 200..299 -> "OK"
        code == -1 -> "Injoignable"
        code in 500..599 -> "Serveur en panne ($code)"
        else -> "Erreur $code"
    }
}

@Composable
fun Tools666Screen(viewModel: Tools666ViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checks by viewModel.checks.collectAsState()
    val running by viewModel.running.collectAsState()
    var lastBackup by remember { mutableStateOf(Backup666.lastBackupAt(context)) }
    var busy by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        viewModel.runChecks()
        runCatching { first.requestFocus() }
    }

    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { Backup666.restore(context, it) } }
                    .getOrNull() == true
            }
            if (!ok) toast(context, "Ce fichier n'est pas une sauvegarde 666TV")
        }
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = androidx.compose.ui.res.painterResource(com.nuvio.tv.R.drawable.fond_collections_666),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.18f,
            modifier = Modifier.fillMaxSize()
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("OUTILS 666", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = Blood)
            }

            // ---------- État 666 ----------
            item { Section("État 666", "Tout ce dont 666TV a besoin pour trouver tes flux") }
            item {
                ActionRow(
                    title = if (running) "Vérification en cours…" else "Revérifier",
                    subtitle = "Internet, TorBox, ton addon aiometadata, tes dépôts de plugins",
                    modifier = Modifier.focusRequester(first),
                    onClick = { viewModel.runChecks() }
                )
            }
            checks.forEach { row ->
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            when (row.ok) { true -> "●"; false -> "●"; null -> "●" },
                            color = when (row.ok) { true -> Green666; false -> Blood; null -> Orange666 },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(row.name, modifier = Modifier.padding(start = 12.dp).width(380.dp), color = Color.White)
                        Text(row.detail, color = Color(0xFFC8C3BE))
                    }
                }
            }
            item {
                Text(
                    "Les instances de tes addons privés (Lumio, Frenchio…) ne sont pas testées ici : leurs adresses contiennent tes clés.",
                    modifier = Modifier.padding(horizontal = 18.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8F8A85)
                )
            }

            // ---------- Sauvegarde ----------
            item {
                val date = if (lastBackup == 0L) "jamais" else
                    SimpleDateFormat("d MMM yyyy 'à' HH:mm", Locale.FRANCE).format(Date(lastBackup))
                Section("Sauvegarde", "Réglages, plugins et leurs configs, Mes applis… · dernière : $date · automatique chaque semaine")
            }
            item {
                ActionRow("Sauvegarder maintenant", "Dans Téléchargements/${Backup666.FILE_NAME}", onClick = {
                    if (busy) return@ActionRow
                    busy = true
                    scope.launch {
                        val n = withContext(Dispatchers.IO) { runCatching { Backup666.backup(context) }.getOrDefault(-1) }
                        busy = false
                        lastBackup = Backup666.lastBackupAt(context)
                        toast(context, if (n >= 0) "Sauvegarde faite ($n fichiers)" else "La sauvegarde a échoué")
                    }
                })
            }
            item {
                ActionRow("Restaurer la dernière sauvegarde", "666TV redémarre tout seul ensuite", onClick = {
                    if (busy) return@ActionRow
                    busy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching { Backup666.openLatest(context)?.use { Backup666.restore(context, it) } }.getOrNull()
                        }
                        busy = false
                        if (ok != true) toast(context, "Aucune sauvegarde trouvée : utilise « Choisir un fichier »")
                    }
                })
            }
            item {
                ActionRow("Choisir un fichier de sauvegarde…", "Après une réinstallation, ou une sauvegarde venue de l'autre Shield", onClick = {
                    runCatching { pickBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                        .onFailure { toast(context, "Pas de sélecteur de fichiers sur cette Shield") }
                })
            }

            // ---------- Affichage ----------
            item { Section("Affichage", null) }
            item {
                val auto = if (Light666.lowEndDevice) "Auto (activé : petite Shield)" else "Auto (désactivé : Shield puissante)"
                val label = when (Light666.mode) {
                    Light666.Mode.AUTO -> auto
                    Light666.Mode.ON -> "Activé"
                    Light666.Mode.OFF -> "Désactivé"
                }
                ActionRow("Mode léger : $label", "Coupe les GIF des tuiles et l'effet poussière pour rester fluide", onClick = {
                    val next = when (Light666.mode) {
                        Light666.Mode.AUTO -> Light666.Mode.ON
                        Light666.Mode.ON -> Light666.Mode.OFF
                        Light666.Mode.OFF -> Light666.Mode.AUTO
                    }
                    Light666.setMode(context, next)
                })
            }
            item {
                ActionRow(
                    "Heure et météo sur l'accueil : " + if (Clock666.enabled) "affichées" else "masquées",
                    "En haut à droite de l'accueil",
                    onClick = { Clock666.setEnabled(context, !Clock666.enabled) }
                )
            }
            item {
                ActionRow(
                    "Pierres tombales : " + if (com.nuvio.tv.ui.components.Tomb666.enabled) "activées" else "désactivées",
                    "Affiches dans les tombes, fond cimetière",
                    onClick = {
                        com.nuvio.tv.ui.components.Tomb666.setEnabled(context, !com.nuvio.tv.ui.components.Tomb666.enabled)
                    }
                )
            }
        }
    }
}

private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

@Composable
private fun Section(title: String, subtitle: String?) {
    Column(Modifier.padding(top = 14.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Green666)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFFC8C3BE))
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x33000000),
            focusedContainerColor = Blood,
            contentColor = Color.White,
            focusedContentColor = Color.White
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE6E0DA))
        }
    }
}

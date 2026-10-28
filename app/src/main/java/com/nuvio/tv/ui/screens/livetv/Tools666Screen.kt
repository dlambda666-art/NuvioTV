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
import androidx.compose.ui.input.key.onPreviewKeyEvent
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

private val Blood = Color(0xFF9B30D9)
/** 666TVF : rouge gardé pour les pannes seulement ; le reste est mauve (28/10). */
private val Red666 = Color(0xFFC3121F)
private val Green666 = Color(0xFF4CC36B)
private val Orange666 = Color(0xFFE59A2B)

/** Une ligne de l'écran « État 666 ». Les adresses ne sont jamais affichées (elles contiennent tes clés). */
data class Health666(val name: String, val detail: String, val ok: Boolean?)

@HiltViewModel
class Tools666ViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val pluginDataStore: PluginDataStore,
    private val pluginHealth: com.nuvio.tv.core.tools.PluginHealth666,
    private val startupSync666: com.nuvio.tv.core.sync.StartupSyncService
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
                // 666TVF : commencé en bas, repris en haut — passe par le compte Nuvio des deux Shield.
                list += {
                    if (startupSync666.hasAccount666) Health666("Reprise entre les Shield", "Compte Nuvio connecté", true)
                    else Health666("Reprise entre les Shield", "Pas de compte Nuvio : connecte le même sur les deux Shield", false)
                }
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
            // 666TVF : chaque plugin passe le test Matrix sur la Shield (liens de config compris).
            _checks.value = rows + Health666("Plugins · test Matrix", "en cours (environ 1 min)…", null)
            val plugins = withContext(Dispatchers.IO) { runCatching { pluginHealth.checkAll() }.getOrDefault(emptyList()) }
            _checks.value = rows + plugins.map { Health666("Plugin · ${it.name}", it.detail, it.ok) }
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
    var menuOrder by remember { mutableStateOf(com.nuvio.tv.core.tools.MenuOrder666.fullOrder()) }
    var movingRoute by remember { mutableStateOf<String?>(null) }
    var pluginOrder by remember { mutableStateOf(com.nuvio.tv.core.tools.PluginOrder666.order) }
    var movingPlugin by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    var annonces by remember { mutableStateOf(com.nuvio.tv.core.tools.Announce666.entries(context)) }
    var overlayOk by remember { mutableStateOf(com.nuvio.tv.core.tools.Announce666.canOverlay(context)) }
    LaunchedEffect(Unit) {
        // l'autorisation se donne dans les réglages Android, puis on revient ici
        while (true) {
            kotlinx.coroutines.delay(2_000L)
            overlayOk = com.nuvio.tv.core.tools.Announce666.canOverlay(context)
            annonces = com.nuvio.tv.core.tools.Announce666.entries(context)
        }
    }
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
            if (!ok) toast(context, "Ce fichier n'est pas une sauvegarde 666TVF")
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
            item { Section("État 666", "Tout ce dont 666TVF a besoin pour trouver tes flux") }
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
                            color = when (row.ok) { true -> Green666; false -> Red666; null -> Orange666 },
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(row.name, modifier = Modifier.padding(start = 12.dp).width(380.dp), color = Color.White)
                        Text(row.detail, color = Color(0xFFC8C3BE))
                    }
                }
            }
            item {
                Text(
                    "Chaque plugin est testé ici même, avec tes configs (elles ne quittent pas la Shield). Vérification automatique chaque jour : la cloche sonne si l'un d'eux ne renvoie plus rien.",
                    modifier = Modifier.padding(horizontal = 18.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8F8A85)
                )
            }

            // ---------- Dernières annonces ----------
            item { Section("Dernières annonces", "Tout ce qui a fait sonner la cloche, le plus récent en haut (30 dernières)") }
            if (annonces.isEmpty()) {
                item {
                    Text("Aucune annonce pour l'instant", modifier = Modifier.padding(horizontal = 18.dp), color = Color(0xFFC8C3BE))
                }
            }
            annonces.forEach { a ->
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            SimpleDateFormat("EEE d · HH:mm", Locale.FRANCE).format(Date(a.at)),
                            modifier = Modifier.width(150.dp),
                            color = Color(0xFFC8C3BE)
                        )
                        Text(a.text, modifier = Modifier.width(470.dp), color = Color.White, maxLines = 1)
                        Text(a.where, color = Color(0xFFC8C3BE), maxLines = 1)
                    }
                }
            }

            // ---------- Annonces partout ----------
            item { Section("Annonces partout", "Pour que la bannière apparaisse aussi dans Orange, YouTube…") }
            item {
                ActionRow(
                    "Autoriser l'affichage par-dessus les autres applis",
                    "À faire une seule fois sur chaque Shield · état : " + if (overlayOk) "autorisé ✓" else "pas encore autorisé",
                    onClick = {
                        val pkg = android.net.Uri.parse("package:" + context.packageName)
                        val opened = runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.isSuccess || runCatching {
                            context.startActivity(
                                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }.isSuccess
                        if (!opened) toast(context, "Réglages Android → Applis → Accès spécial → Afficher par-dessus d'autres applis → 666TVF")
                    }
                )
            }
            item {
                ActionRow(
                    "Tester la bannière",
                    "La cloche sonne dans 20 s avec une annonce d'essai : tu as le temps d'aller sur Orange",
                    onClick = {
                        com.nuvio.tv.core.tools.Announce666.requestTest()
                        toast(context, "Annonce d'essai dans 20 s")
                    }
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
                ActionRow("Restaurer la dernière sauvegarde", "666TVF redémarre tout seul ensuite", onClick = {
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

            // ---------- Ordre du menu ----------
            item { Section("Ordre du menu", "Appui long sur OK sur un onglet, flèches haut/bas pour le déplacer, OK pour le poser") }
            menuOrder.forEachIndexed { index, route ->
                item(key = "menu_$route") {
                    val moving = movingRoute == route
                    Surface(
                        onClick = { },
                        onLongClick = { movingRoute = route },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { ev ->
                                if (!moving) return@onPreviewKeyEvent false
                                if (ev.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
                                // L'appui long envoie des répétitions de OK : on les ignore,
                                // sinon l'onglet est « posé » aussitôt pris.
                                if (ev.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent true
                                val delta = when (ev.nativeKeyEvent.keyCode) {
                                    android.view.KeyEvent.KEYCODE_DPAD_UP -> -1
                                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> 1
                                    else -> 0
                                }
                                if (delta != 0) {
                                    val target = (index + delta).coerceIn(0, menuOrder.lastIndex)
                                    if (target != index) {
                                        val list = menuOrder.toMutableList().apply { add(target, removeAt(index)) }
                                        menuOrder = list
                                        com.nuvio.tv.core.tools.MenuOrder666.save(context, list)
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                when (ev.nativeKeyEvent.keyCode) {
                                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER,
                                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER, android.view.KeyEvent.KEYCODE_BACK -> movingRoute = null
                                }
                                true
                            },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0x33000000),
                            focusedContainerColor = if (moving) Color(0xFF1F6F2E) else Blood,
                            contentColor = com.nuvio.tv.ui.components.Font666.Green,
                            focusedContentColor = Color.White
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = if (moving) 1.04f else 1.01f)
                    ) {
                        Text(
                            (if (moving) "⇅  " else "${index + 1}.  ") + com.nuvio.tv.core.tools.MenuOrder666.labelOf(route),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ---------- Ordre des cavaliers ----------
            item { Section("Ordre des cavaliers", "Ordre de la liste des flux, de « Lecture VF » et du lien suivant. Appui long sur OK, flèches haut/bas, OK pour poser") }
            pluginOrder.forEachIndexed { index, name ->
                item(key = "plugin_$name") {
                    val moving = movingPlugin == name
                    Surface(
                        onClick = { },
                        onLongClick = { movingPlugin = name },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { ev ->
                                if (!moving) return@onPreviewKeyEvent false
                                if (ev.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent true
                                if (ev.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent true
                                val delta = when (ev.nativeKeyEvent.keyCode) {
                                    android.view.KeyEvent.KEYCODE_DPAD_UP -> -1
                                    android.view.KeyEvent.KEYCODE_DPAD_DOWN -> 1
                                    else -> 0
                                }
                                if (delta != 0) {
                                    val target = (index + delta).coerceIn(0, pluginOrder.lastIndex)
                                    if (target != index) {
                                        val list = pluginOrder.toMutableList().apply { add(target, removeAt(index)) }
                                        pluginOrder = list
                                        com.nuvio.tv.core.tools.PluginOrder666.save(context, list)
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                when (ev.nativeKeyEvent.keyCode) {
                                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER,
                                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER, android.view.KeyEvent.KEYCODE_BACK -> movingPlugin = null
                                }
                                true
                            },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0x33000000),
                            focusedContainerColor = if (moving) Color(0xFF1F6F2E) else Blood,
                            contentColor = com.nuvio.tv.ui.components.Font666.Green,
                            focusedContentColor = Color.White
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = if (moving) 1.04f else 1.01f)
                    ) {
                        Text(
                            (if (moving) "⇅  " else "${index + 1}.  ") + "🐎 " + com.nuvio.tv.core.tools.PluginOrder666.labelOf(name),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
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
                val addr = remember { com.nuvio.tv.core.tools.PhoneLink666.address(context) }
                ActionRow(
                    "Envoyer depuis le téléphone : " + if (com.nuvio.tv.core.tools.PhoneLink666.enabled) "activé" else "désactivé",
                    if (com.nuvio.tv.core.tools.PhoneLink666.enabled && addr != null)
                        "Sur ton téléphone (même Wi-Fi), ouvre $addr et tape un titre"
                    else "Tape un titre sur ton téléphone, la recherche s'ouvre sur la TV",
                    onClick = {
                        com.nuvio.tv.core.tools.PhoneLink666.setEnabled(context, !com.nuvio.tv.core.tools.PhoneLink666.enabled)
                    }
                )
            }
            item { CityPicker666() }
            item {
                var voice by remember { mutableStateOf(com.nuvio.tv.core.dream.Dream666Data.voiceEnabled(context)) }
                ActionRow(
                    "Voix des spectres (écran de veille) : " + if (voice) "activée" else "coupée",
                    "Une voix d'outre-tombe quand le grand spectre passe · volume bas, jamais entre 23 h et 8 h",
                    onClick = {
                        voice = !voice
                        com.nuvio.tv.core.dream.Dream666Data.setVoiceEnabled(context, voice)
                    }
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
            contentColor = com.nuvio.tv.ui.components.Font666.Green,
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

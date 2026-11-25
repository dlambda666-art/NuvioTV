package com.nuvio.tv.ui.screens.livetv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.core.m6.M6Plus666
import com.nuvio.tv.ui.components.Font666
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 24/11 (David) : l'écran M6+ de Frank : direct (M6, W9, 6ter, Gulli), recherche dans le replay M6+,
 * saisons → épisodes. Son compte M6+ est tapé ici et reste sur la Shield.
 */
class M6Plus666Activity : ComponentActivity() {

    companion object {
        /** Frank ouvre lui-même ses écrans M6+ : au retour, pas de cinéma (ce n'est pas « une autre appli »). */
        @Volatile var ownScreenOpened = false
        fun markOwnScreen() { ownScreenOpened = true }

        fun open(context: Context) {
            markOwnScreen()
            context.startActivity(Intent(context, M6Plus666Activity::class.java))
        }
    }

    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // 25/11 (piège M6+) : si cet écran plante, la cause est gardée et affichée à la prochaine ouverture
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        val app = applicationContext
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            M6Plus666.saveCrash(app, e)
            previousHandler?.uncaughtException(t, e)
        }
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
        setContent { NuvioTheme { M6PlusScreen(onExit = { finish() }) } }
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) previousHandler?.let { Thread.setDefaultUncaughtExceptionHandler(it) }
        super.onDestroy()
    }
}

private val Mauve = Color(0xFF9B30D9)
private val Bg = Color(0xFF0B0A0D)
private val Card = Color(0xFF17141B)
private val Muted = Color(0xFFB9B2C2)

private sealed interface Page {
    data object Home : Page
    data class Program(val program: M6Plus666.Program) : Page
    data class Folder(val program: M6Plus666.Program, val folder: M6Plus666.Folder) : Page
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun M6PlusScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf<Page>(Page.Home) }
    var showAccount by remember { mutableStateOf(!M6Plus666.hasAccount(context)) }

    BackHandler {
        when {
            showAccount && M6Plus666.hasAccount(context) -> showAccount = false
            page is Page.Folder -> page = Page.Program((page as Page.Folder).program)
            page is Page.Program -> page = Page.Home
            else -> onExit()
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF1A0F22), Bg, Bg))
        )
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("M6+", style = Font666.horror(TextStyle(fontSize = 40.sp), 1.15f), color = Font666.Green)
                Spacer(Modifier.width(16.dp))
                Text(
                    when (val p = page) {
                        Page.Home -> "Le direct et le replay, avec ton compte M6+"
                        is Page.Program -> p.program.title
                        is Page.Folder -> p.program.title + " · " + p.folder.title
                    },
                    color = Muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Pill(if (M6Plus666.hasAccount(context)) "Mon compte M6+" else "Entrer mon compte M6+") { showAccount = true }
            }
            Spacer(Modifier.height(12.dp))
            var crash by remember { mutableStateOf(M6Plus666.lastCrash(context)) }
            crash?.let { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A0A0A))
                        .border(1.dp, Color(0xFFB3261E), RoundedCornerShape(10.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "M6+ a planté la dernière fois. Prends cette ligne en photo pour Claude :\n$c",
                        color = Color(0xFFFFB4AB), fontSize = 13.sp, modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Pill("Effacer") { M6Plus666.clearCrash(context); crash = null }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (showAccount) {
                AccountPanel(onDone = { showAccount = false })
            } else when (val p = page) {
                Page.Home -> HomePage(onProgram = { page = Page.Program(it) })
                is Page.Program -> ProgramPage(p.program, onFolder = { page = Page.Folder(p.program, it) })
                is Page.Folder -> FolderPage(p.program, p.folder)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Pill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Card, focusedContainerColor = Mauve),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Mauve.copy(alpha = 0.6f)), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Font666.Green), shape = shape)
        )
    ) {
        Text(label, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Tile(
    title: String,
    subtitle: String?,
    image: String?,
    width: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    bigTitle: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.width(width),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Card, focusedContainerColor = Color(0xFF241A2C)),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color(0xFF2C2433)), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Font666.Green), shape = shape)
        )
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF2A1636), Color(0xFF0F1A12)))),
                contentAlignment = Alignment.Center
            ) {
                if (image != null) {
                    AsyncImage(model = image, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else if (bigTitle) {
                    Text(title, style = Font666.horror(TextStyle(fontSize = 44.sp), 1.1f), color = Font666.Green)
                }
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {}
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Email,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
        cursorBrush = SolidColor(Font666.Green),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card)
                    .border(1.dp, Mauve.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (value.isEmpty()) Text(placeholder, color = Muted, fontSize = 18.sp)
                inner()
            }
        }
    )
}

@Composable
private fun AccountPanel(onDone: () -> Unit) {
    val context = LocalContext.current
    var login by remember { mutableStateOf(M6Plus666.accountLabel(context).orEmpty()) }
    var password by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Ton compte M6+", style = Font666.horror(TextStyle(fontSize = 26.sp)), color = Font666.Green)
        Text(
            "Le même que dans Kodi. Il reste sur cette Shield : il n'est envoyé qu'à M6, jamais dans le Rapport.",
            color = Muted, fontSize = 15.sp
        )
        Field(login, { login = it }, "Adresse e-mail M6+", modifier = Modifier.fillMaxWidth().focusRequester(first))
        Field(password, { password = it }, if (M6Plus666.hasAccount(context)) "Mot de passe (déjà enregistré)" else "Mot de passe", password = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pill("Enregistrer") {
                if (login.isNotBlank() && password.isNotBlank()) {
                    M6Plus666.saveAccount(context, login, password); password = ""; onDone()
                } else if (M6Plus666.hasAccount(context)) onDone()
            }
            if (M6Plus666.hasAccount(context)) Pill("Oublier ce compte") { M6Plus666.forgetAccount(context); login = ""; password = "" }
        }
    }
}

@Composable
private fun HomePage(onProgram: (M6Plus666.Program) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<M6Plus666.Program>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    fun runSearch() {
        val q = query.trim(); if (q.isEmpty()) return
        message = "Recherche…"
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { M6Plus666.search(q) } }
            r.onSuccess { results = it; message = if (it.isEmpty()) "Rien trouvé sur M6+ pour « $q »" else null }
                .onFailure { message = it.message ?: "M6+ ne répond pas" }
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
        item { Text("EN DIRECT", style = Font666.horror(TextStyle(fontSize = 22.sp)), color = Font666.Green) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(M6Plus666.CHANNELS) { ch ->
                    Tile(
                        ch.label, ch.subtitle, null, 250.dp, bigTitle = true,
                        modifier = if (ch == M6Plus666.CHANNELS.first()) Modifier.focusRequester(first) else Modifier
                    ) { M6Plus666PlayerActivity.live(context, ch) }
                }
            }
        }
        item { Text("REPLAY M6+", style = Font666.horror(TextStyle(fontSize = 22.sp)), color = Font666.Green) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field(query, { query = it }, "Cherche une série, une émission… (ex. Pretty Little Liars)", modifier = Modifier.width(640.dp), onDone = { runSearch() })
                Pill("Rechercher") { runSearch() }
            }
        }
        message?.let { m -> item { Text(m, color = Muted, fontSize = 15.sp) } }
        if (results.isNotEmpty()) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(results) { pr -> Tile(pr.title, null, pr.image, 280.dp) { onProgram(pr) } }
            }
        }
    }
}

@Composable
private fun ProgramPage(program: M6Plus666.Program, onFolder: (M6Plus666.Folder) -> Unit) {
    var data by remember(program.id) { mutableStateOf<Pair<M6Plus666.Program, List<M6Plus666.Folder>>?>(null) }
    var error by remember(program.id) { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(program.id) {
        val r = withContext(Dispatchers.IO) { runCatching { M6Plus666.folders(program.id) } }
        r.onSuccess { data = it }.onFailure { error = it.message ?: "M6+ ne répond pas" }
    }
    LaunchedEffect(data) { if (data != null) runCatching { first.requestFocus() } }
    val d = data
    if (d == null) { Text(error ?: "Chargement…", color = Muted, fontSize = 16.sp); return }
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            (d.first.image ?: program.image)?.let {
                AsyncImage(it, d.first.title, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)))
            }
            Text(d.first.title.ifBlank { program.title }, style = Font666.horror(TextStyle(fontSize = 26.sp)), color = Font666.Green)
            if (d.first.description.isNotBlank()) Text(d.first.description, color = Muted, fontSize = 14.sp, maxLines = 8, overflow = TextOverflow.Ellipsis)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
            items(d.second) { f ->
                Pill(f.title, modifier = if (f == d.second.first()) Modifier.focusRequester(first) else Modifier) { onFolder(f) }
            }
        }
    }
}

@Composable
private fun FolderPage(program: M6Plus666.Program, folder: M6Plus666.Folder) {
    val context = LocalContext.current
    var videos by remember(program.id, folder.id) { mutableStateOf<List<M6Plus666.Video>?>(null) }
    var error by remember(program.id, folder.id) { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(program.id, folder.id) {
        val r = withContext(Dispatchers.IO) { runCatching { M6Plus666.videos(program.id, folder.id) } }
        r.onSuccess { videos = it; if (it.isEmpty()) error = "Aucune vidéo ici pour le moment" }
            .onFailure { error = it.message ?: "M6+ ne répond pas" }
    }
    LaunchedEffect(videos) { if (!videos.isNullOrEmpty()) runCatching { first.requestFocus() } }
    val v = videos
    if (v.isNullOrEmpty()) { Text(error ?: "Chargement…", color = Muted, fontSize = 16.sp); return }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
        items(v.chunked(4)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { video ->
                    val mins = if (video.durationS > 0) "${video.durationS / 60} min" else null
                    Tile(
                        video.title, listOfNotNull(mins, video.description.takeIf { it.isNotBlank() }).joinToString(" · "),
                        video.image ?: program.image, 270.dp,
                        modifier = if (video == v.first()) Modifier.focusRequester(first) else Modifier
                    ) { M6Plus666PlayerActivity.replay(context, video.id, video.title) }
                }
            }
        }
    }
}

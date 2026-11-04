package com.nuvio.tv.ui.screens.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.ui.components.Font666

private val Blood666K = Color(0xFF7E22B8)
private val KeyBg = Color(0xEE121416)

/**
 * 666TV : clavier à nous pour la recherche, en Creepster vert, AZERTY,
 * avec une citrouille à la place du « O ». Le texte se tape en direct
 * (les résultats suivent), OK lance la recherche, Retour ferme.
 */
@Composable
fun Keyboard666(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit
) {
    var digits by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    // 666TVF (04/11) : curseur dans le texte (◀ ▶), on tape / efface à l'endroit du curseur
    // texte tenu ici aussi : les répétitions rapides (appui long) ne lisent jamais un texte en retard
    var typed by remember { mutableStateOf(query) }
    var cursor by remember { mutableStateOf(query.length) }
    // ce que le clavier a lui-même envoyé : seul un changement venu d'ailleurs remet le curseur au bout
    val sent = remember { ArrayDeque<String>() }
    LaunchedEffect(query) { if (query != typed && query !in sent) { typed = query; cursor = query.length } }
    fun edit(r: Pair<String, Int>) {
        typed = r.first; cursor = r.second
        sent.addLast(r.first); while (sent.size > 64) sent.removeFirst()
        onQueryChanged(r.first)
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // 666TVF (04/11) : dernières recherches (raccourcis quand le champ est vide)
    val recent = remember { Recent666.list(ctx) }
    fun submitWith(q: String) {
        if (q.isNotBlank()) Recent666.add(ctx, q)
        if (q != typed) { typed = q; cursor = q.length; onQueryChanged(q) }
        onSubmit(); onDismiss()
    }
    // 666TVF (04/11) : recherche à la voix (reconnaissance Google de la Shield, en français)
    val voice = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { res ->
        val said = res.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (!said.isNullOrBlank()) edit(said to said.length)
    }
    fun startVoice() {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Dis le titre…")
            putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        val ok = runCatching { voice.launch(intent) }.isSuccess
        if (!ok) android.widget.Toast.makeText(ctx, "Pas de reconnaissance vocale sur cette Shield", android.widget.Toast.LENGTH_SHORT).show()
    }
    // 666TVF (31/10) : suggestions pendant qu'on tape — OK sur une suggestion lance la recherche
    var suggestions by remember { mutableStateOf<List<com.nuvio.tv.core.tmdb.Suggestion666>>(emptyList()) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { suggestions = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(350)
        suggestions = com.nuvio.tv.core.tmdb.tmdbSuggest666(query)
    }

    val rows = if (digits) listOf("1234567890", "-'&:!?.,", "") else listOf("AZERTYUIOP", "QSDFGHJKLM", "WXCVBN")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xF20A0B0C), RoundedCornerShape(18.dp))
                .border(2.dp, Font666.Green.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .padding(horizontal = 36.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Ce qui est tapé
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(Color(0xFF0F1112), RoundedCornerShape(14.dp))
                    .border(3.dp, Font666.Green, RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (typed.isEmpty()) "TAPE UN FILM OU UNE SÉRIE…"
                        else typed.substring(0, cursor.coerceIn(0, typed.length)).uppercase() + "▏" + typed.substring(cursor.coerceIn(0, typed.length)).uppercase(),
                    style = Font666.horror(MaterialTheme.typography.headlineMedium, 1.3f),
                    color = if (typed.isEmpty()) Font666.Green.copy(alpha = 0.45f) else Font666.Green,
                    maxLines = 1
                )
            }
            if (typed.isEmpty() && recent.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recent.size) { i -> Recent666Chip(recent[i]) { submitWith(recent[i]) } }
                }
            }
            if (suggestions.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth().height(84.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(suggestions.size) { i ->
                        val sg = suggestions[i]
                        Suggestion666Chip(sg) { submitWith(sg.title) }
                    }
                }
            }
            rows.forEachIndexed { r, row ->
                if (row.isEmpty()) return@forEachIndexed
                // 666TVF (04/11) : au bout d'une rangée, on passe de l'autre côté (Q ◀ → M, M ▶ → Q)
                val req = remember(row) { List(row.length) { FocusRequester() } }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { i, ch ->
                        Key666(
                            width = 76.dp,
                            modifier = Modifier.focusRequester(req[i])
                                .then(if (r == 0 && i == 0) Modifier.focusRequester(first) else Modifier)
                                .wrap666(req, i),
                            onClick = { edit(KeyText666.insert(typed, cursor, ch.lowercaseChar().toString())) }
                        ) { focused ->
                            if (ch == 'O') Pumpkin666(focused) else KeyLabel(ch.toString(), focused, 1.5f)
                        }
                    }
                }
            }
            val bottom = remember { List(7) { FocusRequester() } }
            val holdL = remember { Hold666() }; val holdR = remember { Hold666() }; val holdDel = remember { Hold666() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // ◀ ▶ : déplacer le curseur (appui long = ça défile vite)
                Key666(width = 84.dp, modifier = Modifier.focusRequester(bottom[0]).wrap666(bottom, 0)
                    .repeatOk666(holdL) { cursor = KeyText666.left(cursor) },
                    onClick = { if (!holdL.endOfHold()) cursor = KeyText666.left(cursor) }) { KeyLabel("◀", it, 1.4f) }
                Key666(width = 84.dp, modifier = Modifier.focusRequester(bottom[1]).wrap666(bottom, 1)
                    .repeatOk666(holdR) { cursor = KeyText666.right(cursor, typed) },
                    onClick = { if (!holdR.endOfHold()) cursor = KeyText666.right(cursor, typed) }) { KeyLabel("▶", it, 1.4f) }
                Key666(width = 200.dp, modifier = Modifier.focusRequester(bottom[2]).wrap666(bottom, 2),
                    onClick = { edit(KeyText666.insert(typed, cursor, " ")) }) { KeyLabel("ESPACE", it) }
                // EFFACER : la lettre avant le curseur ; appui long = tout s'efface, lettre par lettre, vite
                Key666(width = 180.dp, modifier = Modifier.focusRequester(bottom[3]).wrap666(bottom, 3)
                    .repeatOk666(holdDel) { edit(KeyText666.backspace(typed, cursor)) },
                    onClick = { if (!holdDel.endOfHold()) edit(KeyText666.backspace(typed, cursor)) }) { KeyLabel("EFFACER", it) }
                Key666(width = 110.dp, modifier = Modifier.focusRequester(bottom[4]).wrap666(bottom, 4),
                    onClick = { digits = !digits }) { KeyLabel(if (digits) "ABC" else "123", it) }
                Key666(width = 90.dp, modifier = Modifier.focusRequester(bottom[5]).wrap666(bottom, 5),
                    onClick = { startVoice() }) { Text("🎤", fontSize = 30.sp) }
                Key666(width = 140.dp, modifier = Modifier.focusRequester(bottom[6]).wrap666(bottom, 6),
                    onClick = { submitWith(typed) }) { KeyLabel("OK", it) }
            }
        }
    }
}

/** 666TVF (04/11) : les 8 dernières recherches (par Shield). */
object Recent666 {
    private const val PREFS = "666tv_recherches"
    fun list(context: android.content.Context): List<String> =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString("q", "").orEmpty()
            .split("\n").filter { it.isNotBlank() }
    fun add(context: android.content.Context, q: String) {
        val l = merge(list(context), q)
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString("q", l.joinToString("\n")).apply()
    }
    /** Règle pure (testée) : la plus récente en premier, sans doublon (casse ignorée), 8 max. */
    fun merge(old: List<String>, q: String): List<String> {
        val t = q.trim().replace("\n", " ")
        if (t.isEmpty()) return old
        return (listOf(t) + old.filterNot { it.equals(t, ignoreCase = true) }).take(8)
    }
}

@Composable
private fun Recent666Chip(q: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF16181A), focusedContainerColor = Blood666K),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = Modifier.height(54.dp)
    ) {
        Box(Modifier.height(54.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text("🕘 " + q.uppercase(), color = Font666.Green, fontSize = 18.sp, maxLines = 1)
        }
    }
}

/** 666TVF (04/11) : texte du clavier avec curseur — règles pures (testées). */
object KeyText666 {
    fun insert(q: String, c: Int, t: String): Pair<String, Int> {
        val i = c.coerceIn(0, q.length); return (q.substring(0, i) + t + q.substring(i)) to (i + t.length)
    }
    fun backspace(q: String, c: Int): Pair<String, Int> {
        val i = c.coerceIn(0, q.length); if (i == 0) return q to 0
        return (q.substring(0, i - 1) + q.substring(i)) to (i - 1)
    }
    fun left(c: Int): Int = (c - 1).coerceAtLeast(0)
    fun right(c: Int, q: String): Int = (c + 1).coerceAtMost(q.length)
    /** Rangée en boucle : la touche voisine à gauche / à droite, en repassant par l'autre bout. */
    fun wrapLeft(i: Int, n: Int): Int = if (i == 0) n - 1 else i - 1
    fun wrapRight(i: Int, n: Int): Int = if (i == n - 1) 0 else i + 1
}

/** Gauche sur la première touche → dernière ; droite sur la dernière → première. */
private fun Modifier.wrap666(req: List<FocusRequester>, i: Int): Modifier =
    this.focusProperties {
        if (i == 0) left = req[KeyText666.wrapLeft(i, req.size)]
        if (i == req.size - 1) right = req[KeyText666.wrapRight(i, req.size)]
    }

/** Appui long en cours : le relâchement qui suit ne doit pas compter comme un clic en plus. */
class Hold666 {
    var held = false
    /** Vrai (et remis à zéro) si ce clic est la fin d'un appui long. */
    fun endOfHold(): Boolean { val h = held; held = false; return h }
}

/** Appui long sur OK : [action] se répète tant qu'on tient (la télécommande envoie des répétitions). */
@Composable
private fun Modifier.repeatOk666(hold: Hold666, action: () -> Unit): Modifier {
    val latest = androidx.compose.runtime.rememberUpdatedState(action)
    return this.onPreviewKeyEvent { e ->
        val k = e.nativeKeyEvent
        val ok = k.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER || k.keyCode == android.view.KeyEvent.KEYCODE_ENTER ||
            k.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER
        if (ok && k.action == android.view.KeyEvent.ACTION_DOWN && k.repeatCount == 0) hold.held = false
        if (ok && k.action == android.view.KeyEvent.ACTION_DOWN && k.repeatCount > 0) { hold.held = true; latest.value(); true } else false
    }
}

@Composable
private fun Key666(
    width: Dp,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable (Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .height(70.dp)
            .onFocusChanged { focused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = KeyBg,
            focusedContainerColor = Blood666K
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f)
    ) {
        Box(Modifier.fillMaxWidth().height(70.dp), contentAlignment = Alignment.Center) { content(focused) }
    }
}

@Composable
private fun KeyLabel(text: String, focused: Boolean, boost: Float = 1.15f) {
    Text(
        text = text,
        style = Font666.horror(MaterialTheme.typography.titleLarge, boost),
        color = if (focused) Color.White else Font666.Green
    )
}

/** La touche « O » : une petite citrouille d'Halloween, dans le vert 666. */
@Composable
private fun Pumpkin666(focused: Boolean) {
    val body = if (focused) Color.White else Font666.Green
    val carve = if (focused) Blood666K else Color(0xFF0A0B0C)
    Canvas(Modifier.size(40.dp)) {
        val w = size.width
        val h = size.height
        // tige
        drawRect(if (focused) Color.White else Color(0xFF3B7D2A), Offset(w * 0.45f, h * 0.02f), Size(w * 0.1f, h * 0.18f))
        // trois lobes
        drawOval(body, Offset(w * 0.02f, h * 0.18f), Size(w * 0.5f, h * 0.78f))
        drawOval(body, Offset(w * 0.48f, h * 0.18f), Size(w * 0.5f, h * 0.78f))
        drawOval(body, Offset(w * 0.22f, h * 0.15f), Size(w * 0.56f, h * 0.82f))
        // yeux triangulaires
        fun tri(cx: Float) = Path().apply {
            moveTo(cx - w * 0.09f, h * 0.48f); lineTo(cx + w * 0.09f, h * 0.48f); lineTo(cx, h * 0.33f); close()
        }
        drawPath(tri(w * 0.34f), carve)
        drawPath(tri(w * 0.66f), carve)
        // bouche en dents de scie
        val mouth = Path().apply {
            moveTo(w * 0.22f, h * 0.62f)
            lineTo(w * 0.34f, h * 0.70f); lineTo(w * 0.42f, h * 0.62f); lineTo(w * 0.50f, h * 0.72f)
            lineTo(w * 0.58f, h * 0.62f); lineTo(w * 0.66f, h * 0.70f); lineTo(w * 0.78f, h * 0.62f)
            lineTo(w * 0.70f, h * 0.80f); lineTo(w * 0.30f, h * 0.80f); close()
        }
        drawPath(mouth, carve)
    }
}


/** 666TVF : une suggestion — petite affiche, titre, année · film/série. */
@Composable
private fun Suggestion666Chip(s: com.nuvio.tv.core.tmdb.Suggestion666, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    androidx.tv.material3.Surface(
        onClick = onClick,
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF16181A),
            focusedContainerColor = Color(0xFF2A1838)
        ),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(1.dp, Font666.Green.copy(alpha = 0.35f)), shape = shape),
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(3.dp, Blood666K), shape = shape)
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = Modifier.width(260.dp).height(80.dp)
    ) {
        Row(Modifier.fillMaxSize().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(46.dp).height(68.dp).background(Color(0xFF2A2626), RoundedCornerShape(6.dp))) {
                if (s.poster != null) {
                    coil3.compose.AsyncImage(
                        model = s.poster, contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                    )
                }
            }
            Column(Modifier.padding(start = 10.dp)) {
                Text(s.title, color = Color.White, fontSize = 16.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(s.year, if (s.isSeries) "Série" else "Film").joinToString(" · "),
                    color = Font666.Green, fontSize = 13.sp
                )
            }
        }
    }
}

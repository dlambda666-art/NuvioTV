package com.nuvio.tv.core.tools

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 666TVF : les annonces de la cloche.
 *  - jamais pendant un film : le lecteur lui-même dit quand il est ouvert (la route
 *    seule ne suffit pas : elle « clignote » quand 666TVF change de lien, 27/10) ;
 *  - un journal « Dernières annonces » (Outils 666) ;
 *  - la bannière par-dessus les autres applis (Orange, YouTube…), si autorisé.
 */
object Announce666 {
    private const val PREFS = "666tv_annonces"
    private const val MAX = 30
    /** Après la fermeture du lecteur, on attend un peu : changement de lien, épisode suivant… */
    const val PLAYER_GRACE_MS = 8_000L

    data class Entry(val at: Long, val text: String, val where: String)

    // ---------- Le lecteur est-il ouvert ? ----------
    private val _playerOpen = MutableStateFlow(0)
    val playerOpen: StateFlow<Int> = _playerOpen
    @Volatile private var playerClosedAt = 0L
    /** Lecteur externe lancé par 666TVF (le film tourne ailleurs). */
    @Volatile var externalPlayback: () -> Boolean = { false }

    /** 666TVF : une fiche ou une liste de flux est ouverte (le guetteur se met en pause). */
    @Volatile var browsing666: Boolean = false

    /** Le guetteur des sorties ne travaille que si on ne lit rien et qu'on ne cherche rien. */
    fun watcherBusy(): Boolean = inPlayer() || browsing666

    fun playerOpened() { _playerOpen.value = _playerOpen.value + 1 }
    fun playerClosed(now: Long = System.currentTimeMillis()) {
        _playerOpen.value = (_playerOpen.value - 1).coerceAtLeast(0)
        playerClosedAt = now
    }

    fun inPlayer(now: Long = System.currentTimeMillis()): Boolean =
        isBusy(_playerOpen.value, playerClosedAt, now) || runCatching { externalPlayback() }.getOrDefault(false)

    /** Règle pure (testée) : lecteur ouvert, ou fermé il y a moins de [PLAYER_GRACE_MS]. */
    fun isBusy(openCount: Int, closedAt: Long, now: Long): Boolean =
        openCount > 0 || (closedAt > 0 && now - closedAt < PLAYER_GRACE_MS)

    // ---------- Annonce d'essai (Outils 666) ----------
    private val _testRequest = MutableStateFlow(0L)
    val testRequest: StateFlow<Long> = _testRequest
    fun requestTest() { _testRequest.value = System.currentTimeMillis() }

    // ---------- 18/11 : minutage des cris (diffusion → VF trouvée → cri) ----------
    private val foundAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    fun markFound(key: String, now: Long = System.currentTimeMillis()) { foundAt.putIfAbsent(key, now) }
    fun foundAt(key: String): Long? = foundAt[key]

    /**
     * Règle pure (testée) : la ligne de minutage ajoutée au journal d'un cri d'épisode.
     * « diffusé il y a 27 h · VF trouvée à 05:12 · cri à 05:30 (écran éteint entre-temps) »
     */
    fun timing(airMs: Long, foundMs: Long?, shownMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        fun hm(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }
        val hours = ((shownMs - airMs) / 3_600_000L).coerceAtLeast(0)
        val parts = arrayListOf("diffusé il y a $hours h")
        if (foundMs != null) parts += "VF trouvée à ${hm(foundMs)}"
        parts += "cri à ${hm(shownMs)}"
        val waited = if (foundMs != null) (shownMs - foundMs) / 60_000L else 0
        return parts.joinToString(" · ") + if (waited >= 20) " (attendu ${waited} min : film en cours ou écran éteint)" else ""
    }

    // ---------- Journal ----------
    fun log(context: Context, text: String, where: String, now: Long = System.currentTimeMillis()) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val old = JSONArray(prefs.getString("log", "[]") ?: "[]")
            val out = JSONArray().put(JSONObject().put("at", now).put("text", text).put("where", where))
            for (i in 0 until minOf(old.length(), MAX - 1)) {
                val o = old.optJSONObject(i) ?: continue
                if (now - o.optLong("at") < KEEP_DAYS * 86_400_000L) out.put(o)
            }
            prefs.edit().putString("log", out.toString()).apply()
        }
    }

    fun entries(context: Context, now: Long = System.currentTimeMillis()): List<Entry> = runCatching {
        val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("log", "[]") ?: "[]")
        keepRecent((0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(o.optLong("at"), o.optString("text"), o.optString("where"))
        }, now)
    }.getOrDefault(emptyList())

    /** 666TVF (07/11) : les annonces s'effacent toutes seules après [KEEP_DAYS] jours. */
    const val KEEP_DAYS = 7
    fun keepRecent(entries: List<Entry>, now: Long): List<Entry> =
        entries.filter { now - it.at < KEEP_DAYS * 86_400_000L }

    /** Bouton « Effacer les annonces » d'Outils 666. */
    fun clear(context: Context) {
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("log", "[]").apply() }
    }

    // ---------- Par-dessus les autres applis ----------
    fun canOverlay(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    @Volatile private var overlayView: View? = null

    /**
     * Affiche la bannière par-dessus l'appli en cours. Elle reste jusqu'à OK / Retour
     * (ce premier appui la ferme, il n'arrive pas à l'autre appli). À appeler sur le fil principal.
     */
    /** Affiche téléchargée hors du fil principal (petite taille), puis posée dans la bannière. */
    private fun loadPoster(url: String, onLoaded: (android.graphics.Bitmap) -> Unit) {
        Thread {
            runCatching {
                if (url.startsWith("file:")) android.graphics.BitmapFactory.decodeFile(android.net.Uri.parse(url).path)
                else (java.net.URL(url).openConnection() as java.net.HttpURLConnection).run {
                    connectTimeout = 8000; readTimeout = 8000
                    try { inputStream.use { android.graphics.BitmapFactory.decodeStream(it) } } finally { disconnect() }
                }
            }.getOrNull()?.let(onLoaded)
        }.start()
    }

    const val OVERLAY_MS = 20_000L

    /**
     * Règle pure (testée) : Frank joue-t-il le rire lui-même ? Seulement s'il est devant quand l'annonce arrive
     * (sinon c'est la carte par-dessus l'autre appli qui l'a déjà joué), jamais pendant un film, et après le cinéma
     * et Machine Head (sinon tout se mélange).
     */
    fun soundAllowed(frankInFront: Boolean, inPlayer: Boolean, cinemaShowing: Boolean, abattoirSoundPlaying: Boolean): Boolean =
        frankInFront && !inPlayer && !cinemaShowing && !abattoirSoundPlaying

    fun showOverlay(context: Context, title: String, titleGreen: Boolean, lines: List<String>, onDismiss: () -> Unit, posters: List<String?> = emptyList()): Boolean {
        if (!canOverlay(context)) return false
        val app = context.applicationContext
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        hideOverlay(app)
        return runCatching {
            fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), app.resources.displayMetrics).toInt()
            val box = object : LinearLayout(app) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_A -> {
                            if (event.action == KeyEvent.ACTION_UP) { hideOverlay(app); onDismiss() }
                            return true
                        }
                    }
                    return super.dispatchKeyEvent(event)
                }
            }.apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(Color.argb(245, 28, 26, 26))
                    setStroke(dp(2), Color.rgb(0x9B, 0x30, 0xD9))
                }
                isFocusable = true
                isFocusableInTouchMode = true
            }
            fun text(s: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(app).apply {
                this.text = s
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
                setTextColor(color)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 2
                setPadding(0, dp(2), 0, dp(2))
            }
            box.addView(text(title, 20f, if (titleGreen) Color.rgb(0x4C, 0xC3, 0x6B) else Color.WHITE, bold = true))
            // 666TVF (04/11) : l'affiche à côté de chaque ligne, comme dans 666TVF (JT RTL sur Orange)
            lines.take(4).forEachIndexed { i, line ->
                val url = posters.getOrNull(i)
                // 23/11 : clin d'œil à L'Exorciste : le 1er titre arrive à l'envers, puis se remet droit
                fun exorcise(v: android.view.View) {
                    if (i != 0) return
                    v.rotation = 180f
                    v.animate().rotation(0f).setStartDelay(com.nuvio.tv.core.horreur.Horreur666.UPSIDE_MS)
                        .setDuration(com.nuvio.tv.core.horreur.Horreur666.UPSIDE_TURN_MS)
                        .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f)).start()
                }
                if (url.isNullOrBlank()) { box.addView(text(line, 16f, Color.WHITE).also { exorcise(it) }); return@forEachIndexed }
                val row = LinearLayout(app).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(3), 0, dp(3))
                }
                val img = android.widget.ImageView(app).apply {
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    background = GradientDrawable().apply { cornerRadius = dp(4).toFloat(); setColor(Color.rgb(42, 38, 38)); setStroke(dp(1), Color.argb(136, 0x9B, 0x30, 0xD9)) }
                    clipToOutline = true
                }
                row.addView(img, LinearLayout.LayoutParams(dp(40), dp(60)).apply { rightMargin = dp(10) })
                row.addView(text(line, 16f, Color.WHITE), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                box.addView(row)
                exorcise(row)
                loadPoster(url) { bmp -> img.post { img.setImageBitmap(bmp) } }
            }
            box.addView(text("Disparaît toute seule · Détails dans le Calendrier FrankenStream", 13f, Color.rgb(0x8F, 0x8A, 0x85)))
            // 21/11 : Frank surgit à côté de l'annonce, aussi par-dessus les autres applis (comme dans le Calendrier)
            val frank = android.widget.ImageView(app).apply {
                setImageResource(com.nuvio.tv.R.drawable.frank_cri_666)
                adjustViewBounds = true
                scaleType = android.widget.ImageView.ScaleType.FIT_END
                alpha = 0f
                translationY = dp(80).toFloat()
            }
            val container = LinearLayout(app).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
                addView(frank, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(170)).apply { rightMargin = -dp(14) })
                addView(box, LinearLayout.LayoutParams(dp(420), LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            frank.animate().alpha(1f).translationY(0f).setStartDelay(120).setDuration(520)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.4f)).start()
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                // 24/11 (réparation, David sur Orange TV : son de la TV coupé) : la carte ne prend plus la
                // télécommande à l'autre appli (elle ne la met plus en pause) et disparaît seule après 20 s
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = dp(24)
                y = dp(24)
            }
            wm.addView(container, params)
            // 24/11 (réparation) : UN SEUL rire, ici, tout de suite (comme le cri d'avant). Le 23/11 il partait
            // deux fois (ici + Frank au retour) → rire haché et son de la TV coupé. Frank ne le rejoue plus.
            runCatching { com.nuvio.tv.core.horreur.Horreur666.playHeld(app, com.nuvio.tv.R.raw.rire_vf_666, 0.8f) }
            overlayView = container
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (overlayView === container) hideOverlay(app)
            }, OVERLAY_MS)
            true
        }.getOrDefault(false)
    }

    fun hideOverlay(context: Context) {
        val v = overlayView ?: return
        overlayView = null
        runCatching { (context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) }
    }

    val overlayShowing: Boolean get() = overlayView != null
}

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

    // ---------- Journal ----------
    fun log(context: Context, text: String, where: String, now: Long = System.currentTimeMillis()) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val old = JSONArray(prefs.getString("log", "[]") ?: "[]")
            val out = JSONArray().put(JSONObject().put("at", now).put("text", text).put("where", where))
            for (i in 0 until minOf(old.length(), MAX - 1)) out.put(old.get(i))
            prefs.edit().putString("log", out.toString()).apply()
        }
    }

    fun entries(context: Context): List<Entry> = runCatching {
        val arr = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("log", "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Entry(o.optLong("at"), o.optString("text"), o.optString("where"))
        }
    }.getOrDefault(emptyList())

    // ---------- Par-dessus les autres applis ----------
    fun canOverlay(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    @Volatile private var overlayView: View? = null

    /**
     * Affiche la bannière par-dessus l'appli en cours. Elle reste jusqu'à OK / Retour
     * (ce premier appui la ferme, il n'arrive pas à l'autre appli). À appeler sur le fil principal.
     */
    fun showOverlay(context: Context, title: String, titleGreen: Boolean, lines: List<String>, onDismiss: () -> Unit): Boolean {
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
            lines.take(4).forEach { box.addView(text(it, 16f, Color.WHITE)) }
            box.addView(text("Appuie sur OK pour fermer · Détails dans le Calendrier 666TVF", 13f, Color.rgb(0x8F, 0x8A, 0x85)))
            val params = WindowManager.LayoutParams(
                dp(420),
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = dp(24)
                y = dp(24)
            }
            wm.addView(box, params)
            box.requestFocus()
            overlayView = box
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

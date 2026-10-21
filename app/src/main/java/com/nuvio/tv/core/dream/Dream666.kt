package com.nuvio.tv.core.dream

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.View
import com.nuvio.tv.R
import com.nuvio.tv.core.tools.Clock666
import com.nuvio.tv.core.tools.Light666
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * 666TV : ce que l'écran de veille affiche (tes 3 derniers films / épisodes en cours).
 * Rempli par l'accueil, lu par l'écran de veille.
 */
object Dream666Data {
    private const val PREFS = "666tv_veille"

    fun save(context: Context, posters: List<String>, label: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("posters", posters.take(3).joinToString("\n"))
            .putString("label", label.orEmpty())
            .apply()
    }

    fun posters(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("posters", "").orEmpty()
            .split("\n").filter { it.startsWith("http") }

    fun label(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("label", "").orEmpty()
}

/** 666TV : écran de veille « cimetière » (à choisir dans Paramètres Shield → Écran de veille). */
class Dream666Service : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        Light666.init(this)
        setContentView(Dream666View(this))
    }
}

private class Dream666View(context: Context) : View(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val rng = Random(System.currentTimeMillis())
    private val light = Light666.active
    private val frameMs = if (light) 66L else 33L

    private val bg: Bitmap? = runCatching {
        BitmapFactory.decodeResource(resources, R.drawable.bg_cimetiere_666, BitmapFactory.Options().apply { inScaled = false })
    }.getOrNull()
    private val frame: Bitmap? = runCatching {
        BitmapFactory.decodeResource(resources, R.drawable.frame_tombe_666, BitmapFactory.Options().apply { inScaled = false })
    }.getOrNull()
    private val posters = arrayOfNulls<Bitmap>(3)
    private val label = Dream666Data.label(context)
    private var weather: Clock666.Weather? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(91, 227, 107)
        typeface = runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, R.font.creepster_666) }.getOrNull()
            ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER; setShadowLayer(18f, 0f, 0f, Color.rgb(31, 175, 58))
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(205, 205, 205); textAlign = Paint.Align.CENTER; setShadowLayer(10f, 0f, 3f, Color.BLACK)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(195, 18, 31); typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER; setShadowLayer(10f, 0f, 3f, Color.BLACK)
    }
    private val fogPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(240, 245, 255); style = Paint.Style.STROKE; strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND; setShadowLayer(24f, 0f, 0f, Color.rgb(170, 190, 255))
    }

    private val start = System.currentTimeMillis()
    private var nextBolt = start + 6_000L + rng.nextLong(8_000L)
    private var boltAt = 0L
    private var bolt: Path? = null
    // Nuages sombres qui filent en haut du ciel.
    private val clouds = List(9) { floatArrayOf(rng.nextFloat() * 1.3f, 0.03f + rng.nextFloat() * 0.25f, 0.12f + rng.nextFloat() * 0.18f, 0.6f + rng.nextFloat() * 0.8f) }
    private val puffs = List(14) { floatArrayOf(rng.nextFloat(), 0.62f + rng.nextFloat() * 0.38f, 0.15f + rng.nextFloat() * 0.25f, 0.2f + rng.nextFloat() * 0.8f) }

    private val tick = object : Runnable {
        override fun run() { invalidate(); handler.postDelayed(this, frameMs) }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        Thread {
            Dream666Data.posters(context).forEachIndexed { i, url -> if (i < 3) posters[i] = load(url) }
            weather = runCatching { Clock666.weather() }.getOrNull()
            postInvalidate()
        }.apply { isDaemon = true }.start()
    }

    private fun load(url: String): Bitmap? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 10000
        try { c.inputStream.use { BitmapFactory.decodeStream(it) } } finally { c.disconnect() }
    }.getOrNull()

    override fun onAttachedToWindow() { super.onAttachedToWindow(); handler.post(tick) }
    override fun onDetachedFromWindow() { handler.removeCallbacks(tick); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val now = System.currentTimeMillis()
        val t = (now - start) / 1000f
        // Petit décalage lent (évite de marquer l'écran).
        val driftX = sin(t / 37f) * w * 0.012f
        val driftY = sin(t / 53f) * h * 0.01f

        // Fond
        canvas.drawColor(Color.BLACK)
        bg?.let {
            val scale = max(w / it.width, h / it.height) * 1.06f
            val bw = it.width * scale; val bh = it.height * scale
            val dst = RectF((w - bw) / 2 + driftX, (h - bh) / 2 + driftY, (w + bw) / 2 + driftX, (h + bh) / 2 + driftY)
            paint.alpha = 200
            canvas.drawBitmap(it, null, dst, paint)
            paint.alpha = 255
        }

        // Éclair (flash + zébrure), de temps en temps.
        if (now >= nextBolt) {
            boltAt = now; nextBolt = now + 9_000L + rng.nextLong(16_000L); bolt = makeBolt(w, h)
        }
        val sinceBolt = now - boltAt
        val flash = if (boltAt > 0 && sinceBolt < 700) (1f - sinceBolt / 700f) else 0f

        // Tombes avec tes affiches.
        val sizes = floatArrayOf(0.12f, 0.17f, 0.12f)
        val xs = floatArrayOf(0.2f, 0.5f, 0.8f)
        val ys = floatArrayOf(0.5f, 0.42f, 0.5f)
        val dims = intArrayOf(150, 255, 150)
        for (i in 0..2) {
            val fw = w * sizes[i]
            val f = frame ?: break
            val fh = fw * f.height / f.width
            val left = w * xs[i] - fw / 2 + driftX * 0.5f
            val top = h * ys[i] + driftY * 0.5f
            val window = RectF(left + fw * 0.2381f, top + fh * 0.2456f, left + fw * 0.7651f, top + fh * 0.7379f)
            val p = posters[i]
            if (p != null) {
                paint.alpha = dims[i]
                canvas.drawBitmap(p, centerCrop(p, window), window, paint)
            } else {
                paint.color = Color.rgb(12, 12, 12); paint.alpha = 255; canvas.drawRect(window, paint)
            }
            paint.alpha = dims[i]
            canvas.drawBitmap(f, null, RectF(left, top, left + fw, top + fh), paint)
            paint.alpha = 255
        }

        // Nuages qui passent.
        for (c in clouds) {
            val x = ((c[0] + t * 0.06f * c[3]) % 1.6f - 0.3f) * w
            val y = c[1] * h
            val r = c[2] * w
            fogPaint.shader = android.graphics.RadialGradient(
                x, y, r, Color.argb(if (light) 55 else 85, 40, 44, 52), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            canvas.drawOval(x - r * 1.6f, y - r * 0.55f, x + r * 1.6f, y + r * 0.55f, fogPaint)
        }
        // Brouillard qui dérive en bas de l'écran.
        for (pf in puffs) {
            val x = ((pf[0] + t * 0.08f * pf[3]) % 1.4f - 0.2f) * w
            val y = pf[1] * h
            val r = pf[2] * w
            fogPaint.shader = android.graphics.RadialGradient(
                x, y, r, Color.argb(if (light) 40 else 70, 200, 205, 215), Color.TRANSPARENT, Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, r, fogPaint)
        }
        fogPaint.shader = null

        if (flash > 0f) {
            bolt?.let { boltPaint.alpha = (255 * flash).toInt(); canvas.drawPath(it, boltPaint) }
            paint.color = Color.WHITE; paint.alpha = (90 * flash).toInt(); canvas.drawRect(0f, 0f, w, h, paint)
            paint.alpha = 255
        }

        // Heure, date, météo.
        val d = Date(now)
        clockPaint.textSize = h * 0.13f
        canvas.drawText(SimpleDateFormat("HH:mm", Locale.FRANCE).format(d), w / 2 + driftX, h * 0.2f + driftY, clockPaint)
        smallPaint.textSize = h * 0.033f
        val wx = weather?.let { "${it.tempC}°  ·  " } ?: ""
        canvas.drawText(wx + SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(d), w / 2 + driftX, h * 0.27f + driftY, smallPaint)

        if (label.isNotBlank()) {
            labelPaint.textSize = h * 0.032f
            canvas.drawText("$label — reprends quand tu veux", w / 2 + driftX, h * 0.93f + driftY, labelPaint)
        }
    }

    private fun centerCrop(b: Bitmap, dst: RectF): Rect {
        val target = dst.width() / dst.height()
        val src = b.width.toFloat() / b.height
        return if (src > target) {
            val cw = (b.height * target).toInt(); val x = (b.width - cw) / 2
            Rect(x, 0, x + cw, b.height)
        } else {
            val ch = (b.width / target).toInt(); val y = (b.height - ch) / 2
            Rect(0, y, b.width, y + ch)
        }
    }

    private fun makeBolt(w: Float, h: Float): Path {
        val p = Path()
        var x = w * (0.1f + rng.nextFloat() * 0.8f); var y = 0f
        p.moveTo(x, y)
        while (y < h * 0.42f) {
            x += (rng.nextFloat() - 0.5f) * w * 0.05f
            y += h * (0.03f + rng.nextFloat() * 0.04f)
            p.lineTo(x, y)
        }
        return p
    }
}

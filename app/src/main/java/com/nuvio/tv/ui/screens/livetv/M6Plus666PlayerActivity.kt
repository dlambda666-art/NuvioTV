package com.nuvio.tv.ui.screens.livetv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.ExoMediaDrm
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallbackException
import com.nuvio.tv.core.m6.M6Plus666
import java.util.UUID
import kotlin.concurrent.thread

/**
 * 24/11 (David) : le lecteur M6+ de Frank. À part du lecteur principal (il ne peut rien y casser) :
 * DASH + Widevine de la Shield, licence drmtoday avec le jeton de lecture donné par M6.
 */
@OptIn(UnstableApi::class)
class M6Plus666PlayerActivity : Activity() {

    companion object {
        private const val EXTRA_LIVE = "live"
        private const val EXTRA_VIDEO = "video"
        private const val EXTRA_TITLE = "title"

        fun live(context: Context, channel: M6Plus666.Channel) {
            M6Plus666Activity.markOwnScreen()
            context.startActivity(Intent(context, M6Plus666PlayerActivity::class.java)
                .putExtra(EXTRA_LIVE, channel.liveId).putExtra(EXTRA_TITLE, channel.label))
        }

        fun replay(context: Context, videoId: String, title: String) {
            M6Plus666Activity.markOwnScreen()
            context.startActivity(Intent(context, M6Plus666PlayerActivity::class.java)
                .putExtra(EXTRA_VIDEO, videoId).putExtra(EXTRA_TITLE, title))
        }
    }

    private var player: ExoPlayer? = null
    // 25/11 (photo de David : IllegalStateException dans PlayerView.setUseController) : la PlayerView de la
    // bibliothèque de Frank n'a pas de barre de commandes → plantage à l'ouverture. Le lecteur M6+ dessine
    // maintenant l'image lui-même (SurfaceView) et gère la télécommande lui-même : rien qui puisse manquer.
    private lateinit var surface: android.view.SurfaceView
    private lateinit var info: TextView
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private var isLive = false
    private lateinit var status: TextView

    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        val app = applicationContext
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            M6Plus666.saveCrash(app, e)
            previousHandler?.uncaughtException(t, e)
        }
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        surface = android.view.SurfaceView(this)
        info = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt()); textSize = 18f
            setBackgroundColor(0xB0000000.toInt()); setPadding(28, 14, 28, 14)
            visibility = android.view.View.GONE
        }
        status = TextView(this).apply {
            setTextColor(0xFF5BE36B.toInt()); textSize = 20f; gravity = Gravity.CENTER; setPadding(80, 0, 80, 0)
            text = "M6+ : ouverture de ${intent.getStringExtra(EXTRA_TITLE).orEmpty()}…"
        }
        root.addView(surface, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        root.addView(status, FrameLayout.LayoutParams(-1, -1))
        root.addView(info, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(48, 0, 0, 48) })
        setContentView(root)

        val liveId = intent.getStringExtra(EXTRA_LIVE)
        val videoId = intent.getStringExtra(EXTRA_VIDEO)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        thread(name = "m6plus-open") {
            val result = runCatching {
                if (liveId != null) M6Plus666.liveStream(this, M6Plus666.CHANNELS.first { it.liveId == liveId })
                else M6Plus666.replayStream(this, videoId!!, title)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { start(it, live = liveId != null) }
                    .onFailure {
                        M6Plus666.noteError(this, if (liveId != null) "direct $title" else "replay $title", it.message)
                        status.text = it.message ?: "M6+ : impossible d'ouvrir la vidéo"
                    }
            }
        }
    }

    private var lastPlayable: M6Plus666.Playable? = null
    // 25/11 (David : « ça marche, mais c'est long à démarrer ») : le 1er essai (L1) échoue toujours sur ses
    // Shield → on retient que le 2e (L3) marche et on démarre directement en L3 les fois suivantes.
    private var attempt = -1

    private fun start(p: M6Plus666.Playable, live: Boolean) {
        if (attempt < 0) attempt = if (M6Plus666.prefersL3(this)) 1 else 0
        lastPlayable = p
        val drmCallback = object : MediaDrmCallback {
            private val provision = HttpMediaDrmCallback(null, DefaultHttpDataSource.Factory())
            override fun executeProvisionRequest(uuid: UUID, request: ExoMediaDrm.ProvisionRequest): ByteArray =
                provision.executeProvisionRequest(uuid, request)

            override fun executeKeyRequest(uuid: UUID, request: ExoMediaDrm.KeyRequest): ByteArray {
                try {
                    val text = M6Plus666.license(request.data, p.licenseToken)
                    val b64 = M6Plus666.licenseBase64(text)
                    return if (b64 != null) Base64.decode(b64, Base64.DEFAULT) else text.toByteArray(Charsets.ISO_8859_1)
                } catch (e: Exception) {
                    val uri = Uri.parse(M6Plus666.LICENSE_URL)
                    throw MediaDrmCallbackException(DataSpec(uri), uri, emptyMap(), 0, e)
                }
            }
        }
        val drm = DefaultDrmSessionManager.Builder()
            // 25/11 (photo de David : DECODING_FAILED · IllegalStateException) : en Widevine L1, la licence peut
            // exiger un décodeur sécurisé pour TOUT (le son aussi, qui n'en a pas) → le décodage casse.
            // 2e essai : Widevine logiciel (L3), comme les lecteurs Android qui marchent partout.
            .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID) { uuid ->
                val drm = FrameworkMediaDrm.newInstance(uuid)
                if (attempt >= 1) runCatching { drm.setPropertyString("securityLevel", "L3") }
                drm
            }
            .build(drmCallback)
        val source = DashMediaSource.Factory(
            DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
        ).setDrmSessionManagerProvider { drm }
            .createMediaSource(
                MediaItem.Builder().setUri(p.manifest).setMimeType(MimeTypes.APPLICATION_MPD)
                    .setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).build())
                    // 25/11 (« ça fige au début ») : le direct démarre un peu plus loin du bord (plus de réserve)
                    .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(20_000).build())
                    .build()
            )
        // 25/11 (photo de David : ERROR_CODE_DECODING_FAILED sur la Shield du haut) : comme le lecteur principal de
        // Frank (qui lit tout sur ces Shield) : si un décodeur échoue, on passe au suivant, et le décodeur logiciel
        // de Frank (FFmpeg) prend le son si besoin. 2e essai automatique : son AAC stéréo seulement.
        val renderers = androidx.media3.exoplayer.DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val selector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this).apply {
            parameters = buildUponParameters()
                .setPreferredAudioLanguage("fr")
                .setPreferredAudioMimeType(MimeTypes.AUDIO_AAC)
                .apply { if (attempt >= 1) setMaxAudioChannelCount(2).setExceedAudioConstraintsIfNecessary(false) }
                .build()
        }
        // 25/11 (« ça fige au début ») : démarre sur une qualité raisonnable puis monte, et attend 3 s de réserve
        // avant de lancer l'image (au lieu de partir trop vite puis de figer)
        val meter = androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(this)
            .setInitialBitrateEstimate(4_000_000L).build()
        val load = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(20_000, 50_000, 3_000, 5_000).build()
        val exo = ExoPlayer.Builder(this).setRenderersFactory(renderers).setTrackSelector(selector)
            .setBandwidthMeter(meter).setLoadControl(load).build()
        player = exo
        exo.setVideoSurfaceView(surface)
        isLive = live
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    status.text = ""
                    if (attempt >= 1) M6Plus666.rememberL3(this@M6Plus666PlayerActivity)
                }
                if (state == Player.STATE_ENDED && !live) finish()
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                // image entière, sans déformation (bandes noires si besoin)
                val vw = videoSize.width * videoSize.pixelWidthHeightRatio
                val vh = videoSize.height.toFloat()
                val root = surface.parent as? android.view.View ?: return
                if (vw <= 0f || vh <= 0f || root.width == 0) return
                val k = minOf(root.width / vw, root.height / vh)
                surface.layoutParams = FrameLayout.LayoutParams((vw * k).toInt(), (vh * k).toInt(), Gravity.CENTER)
            }

            override fun onPlayerError(error: PlaybackException) {
                val root = generateSequence(error as Throwable) { it.cause }.last()
                val where = root.stackTrace.take(2).joinToString(" ← ") { it.className.substringAfterLast('.') + "." + it.methodName }
                val detail = root.javaClass.simpleName + ": " + root.message + (if (where.isNotBlank()) " @ $where" else "")
                M6Plus666.noteError(this@M6Plus666PlayerActivity, "lecture ${p.title} (essai ${attempt + 1})", error.errorCodeName + " · " + detail)
                val decoder = error.errorCode in PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED
                if (decoder && attempt == 0) {
                    // 2e essai tout seul : Widevine logiciel (L3) + son AAC stéréo (le plus simple à décoder)
                    attempt = 1
                    status.text = "M6+ : nouvel essai…"
                    runCatching { player?.release() }; player = null
                    // nouveau jeton de lecture (un jeton peut ne servir qu'une fois)
                    val liveId = intent.getStringExtra(EXTRA_LIVE)
                    val videoId = intent.getStringExtra(EXTRA_VIDEO)
                    thread(name = "m6plus-retry") {
                        val again = runCatching {
                            if (liveId != null) M6Plus666.liveStream(this@M6Plus666PlayerActivity, M6Plus666.CHANNELS.first { it.liveId == liveId })
                            else M6Plus666.replayStream(this@M6Plus666PlayerActivity, videoId!!, p.title)
                        }
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            again.onSuccess { start(it, live) }.onFailure { status.text = it.message ?: "M6+ : impossible d'ouvrir la vidéo" }
                        }
                    }
                    return
                }
                status.text = "M6+ : lecture impossible (${error.errorCodeName})\n" + M6Plus666.safeMessage(detail).take(220)
            }
        })
        exo.setMediaSource(source)
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun fmt(ms: Long): String {
        val t = (ms / 1000).coerceAtLeast(0)
        return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
    }

    private fun showInfo(text: String) {
        val p = player
        val pos = if (p != null && !isLive && p.duration > 0) "   " + fmt(p.currentPosition) + " / " + fmt(p.duration) else ""
        info.text = text + pos
        info.visibility = android.view.View.VISIBLE
        ui.removeCallbacksAndMessages(null)
        ui.postDelayed({ info.visibility = android.view.View.GONE }, 2500)
    }

    /** Télécommande : OK = pause / lecture, ◀ ▶ = −10 s / +10 s (replay), ⏪ ⏩ = −30 s / +30 s, Retour = quitter. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) finish()
            return true
        }
        val p = player ?: return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN) return true
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                p.playWhenReady = !p.playWhenReady
                showInfo(if (p.playWhenReady) "▶ Lecture" else "⏸ Pause")
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (isLive) { showInfo("En direct"); return true }
                val step = when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> -10_000L
                    KeyEvent.KEYCODE_DPAD_RIGHT -> 10_000L
                    KeyEvent.KEYCODE_MEDIA_REWIND -> -30_000L
                    else -> 30_000L
                }
                val target = (p.currentPosition + step).coerceIn(0L, if (p.duration > 0) p.duration - 1_000L else Long.MAX_VALUE)
                p.seekTo(target)
                showInfo(if (step < 0) "⏪ ${-step / 1000} s" else "⏩ ${step / 1000} s")
            }
            else -> showInfo(if (p.playWhenReady) "▶ Lecture" else "⏸ Pause")
        }
        return true
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) previousHandler?.let { Thread.setDefaultUncaughtExceptionHandler(it) }
        player?.release(); player = null
        super.onDestroy()
    }
}

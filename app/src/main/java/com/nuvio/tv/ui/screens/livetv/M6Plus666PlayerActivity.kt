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
import androidx.media3.ui.PlayerView
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
    private lateinit var playerView: PlayerView
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
        playerView = PlayerView(this).apply {
            useController = true
            controllerShowTimeoutMs = 3500
            setShowNextButton(false); setShowPreviousButton(false)
        }
        status = TextView(this).apply {
            setTextColor(0xFF5BE36B.toInt()); textSize = 22f; gravity = Gravity.CENTER
            text = "M6+ : ouverture de ${intent.getStringExtra(EXTRA_TITLE).orEmpty()}…"
        }
        root.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        root.addView(status, FrameLayout.LayoutParams(-1, -1))
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

    private fun start(p: M6Plus666.Playable, live: Boolean) {
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
            .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .build(drmCallback)
        val source = DashMediaSource.Factory(
            DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
        ).setDrmSessionManagerProvider { drm }
            .createMediaSource(
                MediaItem.Builder().setUri(p.manifest).setMimeType(MimeTypes.APPLICATION_MPD)
                    .setDrmConfiguration(MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).build())
                    .build()
            )
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        playerView.player = exo
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) status.text = ""
                if (state == Player.STATE_ENDED && !live) finish()
            }

            override fun onPlayerError(error: PlaybackException) {
                M6Plus666.noteError(this@M6Plus666PlayerActivity, "lecture ${p.title}", error.errorCodeName + " · " + (error.cause?.let { c -> (c.cause ?: c).javaClass.simpleName + ": " + (c.cause ?: c).message } ?: ""))
                status.text = "M6+ : lecture impossible (${error.errorCodeName})"
            }
        })
        exo.setMediaSource(source)
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Retour ferme le lecteur ; le reste (lecture/pause, avance) passe par les commandes du lecteur
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && !playerView.isControllerFullyVisible) {
            finish(); return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && !playerView.isControllerFullyVisible && event.keyCode != KeyEvent.KEYCODE_BACK) {
            playerView.showController()
        }
        return super.dispatchKeyEvent(event)
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

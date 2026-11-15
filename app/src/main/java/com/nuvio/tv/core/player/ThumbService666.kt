package com.nuvio.tv.core.player

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * FrankenStream (15/11) : images d'aperçu « de secours » quand on avance dans un film.
 * Le lecteur d'images d'Android ne sait pas lire beaucoup de fichiers (MKV en 4K HDR,
 * Dolby Vision, liens debrid…) : les images n'apparaissaient presque jamais. Ici, FFmpeg
 * les lit tous. Il tourne dans un PROCESSUS À PART (« :thumbs666 ») : s'il plante sur un
 * fichier bizarre, seul ce petit processus tombe, jamais le film en cours.
 */
class ThumbService666 : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var messenger: Messenger
    private var retriever: wseemann.media.FFmpegMediaMetadataRetriever? = null
    private var openedUrl: String? = null

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("thumbs666").apply { start() }
        messenger = Messenger(object : Handler(thread.looper) {
            override fun handleMessage(msg: Message) {
                if (msg.what != WHAT_FRAME) return
                val data = msg.data
                val bytes = runCatching {
                    frame(data.getString(KEY_URL).orEmpty(), data.getBundle(KEY_HEADERS), data.getLong(KEY_TIME_US))
                }.onFailure { Log.d(TAG, "frame failed: ${it.message}") }.getOrNull()
                val reply = Message.obtain(null, WHAT_FRAME).apply {
                    arg1 = msg.arg1
                    this.data = Bundle().apply { if (bytes != null) putByteArray(KEY_JPEG, bytes) }
                }
                runCatching { msg.replyTo?.send(reply) }
            }
        })
    }

    private fun frame(url: String, headers: Bundle?, timeUs: Long): ByteArray? {
        if (!url.startsWith("http", ignoreCase = true)) return null
        if (url != openedUrl) {
            runCatching { retriever?.release() }
            retriever = null
            openedUrl = url
            val h = HashMap<String, String>()
            headers?.keySet()?.forEach { k -> headers.getString(k)?.let { h[k] = it } }
            retriever = wseemann.media.FFmpegMediaMetadataRetriever().also { it.setDataSource(url, h) }
        }
        val mmr = retriever ?: return null
        val bmp = mmr.getFrameAtTime(timeUs, wseemann.media.FFmpegMediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
        val scaled = Bitmap.createScaledBitmap(bmp, WIDTH, HEIGHT, true)
        if (scaled !== bmp) bmp.recycle()
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out); scaled.recycle(); out.toByteArray()
        }
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        runCatching { retriever?.release() }
        thread.quitSafely()
        super.onDestroy()
    }

    /** Côté lecteur : demande une image au processus à part, avec un délai maximum. */
    class Client(context: Context) {
        private val app = context.applicationContext
        @Volatile private var service: Messenger? = null
        @Volatile private var bound = false
        private val ids = AtomicInteger(0)
        private val waiting = java.util.concurrent.ConcurrentHashMap<Int, CompletableDeferred<ByteArray?>>()
        private val replies = Messenger(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                waiting.remove(msg.arg1)?.complete(msg.data?.getByteArray(KEY_JPEG))
            }
        })
        private val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { service = binder?.let { Messenger(it) } }
            override fun onServiceDisconnected(name: ComponentName?) {
                // le processus d'images est tombé (fichier bizarre) : Android le relancera
                service = null
                waiting.values.forEach { it.complete(null) }; waiting.clear()
            }
        }

        private fun ensureBound() {
            if (bound) return
            bound = runCatching { app.bindService(Intent(app, ThumbService666::class.java), connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        }

        suspend fun frame(url: String, headers: Map<String, String>, timeUs: Long, timeoutMs: Long = 8_000L): Bitmap? {
            ensureBound()
            // le premier lancement du processus prend un instant
            var waited = 0L
            while (service == null && waited < 3_000L) { kotlinx.coroutines.delay(100L); waited += 100L }
            val s = service ?: return null
            val id = ids.incrementAndGet()
            val deferred = CompletableDeferred<ByteArray?>()
            waiting[id] = deferred
            val msg = Message.obtain(null, WHAT_FRAME).apply {
                arg1 = id
                replyTo = replies
                data = Bundle().apply {
                    putString(KEY_URL, url)
                    putBundle(KEY_HEADERS, Bundle().apply { headers.forEach { (k, v) -> putString(k, v) } })
                    putLong(KEY_TIME_US, timeUs)
                }
            }
            if (runCatching { s.send(msg) }.isFailure) { waiting.remove(id); return null }
            val bytes = withTimeoutOrNull(timeoutMs) { deferred.await() } ?: run { waiting.remove(id); null } ?: return null
            return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }

        fun release() {
            if (bound) runCatching { app.unbindService(connection) }
            bound = false; service = null
        }
    }

    companion object {
        private const val TAG = "Thumb666"
        const val WHAT_FRAME = 1
        const val KEY_URL = "url"
        const val KEY_HEADERS = "headers"
        const val KEY_TIME_US = "t"
        const val KEY_JPEG = "jpeg"
        const val WIDTH = 384
        const val HEIGHT = 216
    }
}

package com.nuvio.tv.core.player

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 666TV : images d'aperçu quand on avance ou recule dans la vidéo.
 *
 * Idée de Nuvio Arc (ysosrs123/NuvioTV-Fork), en version légère : pas de fabrication
 * de vignettes en arrière-plan (pas de stockage usé, pas de téléchargement en continu).
 * On va chercher UNE image, à l'endroit visé, seulement quand on fait une pause sur la
 * barre de progression. Les images déjà vues restent en mémoire (cases de 10 s).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SeekThumbnailProvider {
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    private val mutex = Mutex()
    private var retriever: MediaMetadataRetriever? = null
    private var openedUrl: String? = null
    private var failedUrl: String? = null
    private val cache = object : LruCache<Long, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    }

    /** Image à [positionMs] dans [url], ou null si le fichier ne s'y prête pas. */
    suspend fun frameAt(url: String, headers: Map<String, String>, positionMs: Long): Bitmap? {
        if (url.isBlank() || !url.startsWith("http", ignoreCase = true)) return null
        val bucket = positionMs / BUCKET_MS
        return withContext(dispatcher) {
            mutex.withLock {
                if (url != openedUrl) reset(url)
                if (url == failedUrl) return@withLock null
                cache.get(bucket)?.let { return@withLock it }
                val mmr = retriever ?: open(url, headers) ?: return@withLock null
                val timeUs = (bucket * BUCKET_MS + BUCKET_MS / 2) * 1000L
                val frame = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        mmr.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, WIDTH, HEIGHT)
                    } else {
                        mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            ?.let { Bitmap.createScaledBitmap(it, WIDTH, HEIGHT, true) }
                    }
                }.onFailure { Log.d(TAG, "frame failed at ${timeUs / 1000}ms: ${it.message}") }.getOrNull()
                frame?.also { cache.put(bucket, it) }
            }
        }
    }

    private fun open(url: String, headers: Map<String, String>): MediaMetadataRetriever? {
        val mmr = MediaMetadataRetriever()
        return runCatching {
            mmr.setDataSource(url, headers)
            retriever = mmr
            mmr
        }.onFailure {
            Log.d(TAG, "cannot open stream for thumbnails: ${it.message}")
            runCatching { mmr.release() }
            failedUrl = url
        }.getOrNull()
    }

    private fun reset(url: String?) {
        retriever?.let { runCatching { it.release() } }
        retriever = null
        openedUrl = url
        cache.evictAll()
    }

    suspend fun release() = withContext(dispatcher) {
        mutex.withLock { reset(null) }
    }

    private companion object {
        const val TAG = "SeekThumbnail"
        const val BUCKET_MS = 10_000L
        const val WIDTH = 384
        const val HEIGHT = 216
        const val CACHE_BYTES = 24 * 1024 * 1024
    }
}

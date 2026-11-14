package com.nuvio.tv.core.tools

import android.content.Context
import android.os.Build
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 666TVF (12/11) : une Shield à jour installe elle-même la nouvelle version sur l'autre, par le réseau
 * de la maison (« débogage réseau » activé une fois sur l'autre Shield, et la demande acceptée une fois
 * sur sa TV). Rien ne sort de la maison ; l'APK envoyé est celui qui tourne ici (même signature).
 * Jamais pendant un film sur l'autre Shield.
 */
object Maj666 {
    private const val ADB_PORT = 5555
    private const val PREFS = "666tv_maj"

    @Suppress("DEPRECATION")
    fun myCode(context: Context): Long = runCatching {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
    }.getOrDefault(0L)

    fun myName(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /** Règle pure (testée) : faut-il envoyer notre version à l'autre Shield maintenant ? */
    fun shouldPush(mine: Long, theirs: Long, theirPlaying: Boolean, lastTriedCode: Long, lastTriedAt: Long, now: Long): Boolean =
        mine > theirs && !theirPlaying && !(lastTriedCode == mine && now - lastTriedAt < 3 * 3600_000L)

    /** Règle pure (testée) : l'APK qui convient à l'autre Shield (64 bits si elle sait, sinon 32 bits). */
    fun pickAbi(theirAbis: List<String>): String? = when {
        "arm64-v8a" in theirAbis -> "arm64-v8a"
        "armeabi-v7a" in theirAbis -> "armeabi-v7a"
        else -> null
    }

    /** L'APK qui tourne ici : 64 ou 32 bits (d'après ses bibliothèques). */
    fun myAbi(context: Context): String =
        if (context.applicationInfo.nativeLibraryDir.orEmpty().trimEnd('/').endsWith("arm64")) "arm64-v8a" else "armeabi-v7a"

    /** Même version, autre ABI : téléchargée depuis la publication GitHub de 666TV (gardée en cache). */
    private fun download(context: Context, version: String, abi: String): File {
        val name = "666TV-$version-$abi-release.apk"
        val out = File(context.cacheDir, "maj666/$name")
        if (out.exists() && out.length() > 1_000_000L) return out
        out.parentFile?.mkdirs()
        context.cacheDir.resolve("maj666").listFiles()?.filter { it.name != name }?.forEach { it.delete() }
        val url = java.net.URL("https://github.com/dlambda666-art/NuvioTV/releases/download/v$version/$name")
        val c = (url.openConnection() as java.net.HttpURLConnection).apply {
            instanceFollowRedirects = true; connectTimeout = 15_000; readTimeout = 60_000
        }
        try {
            if (c.responseCode !in 200..299) throw java.io.IOException("APK $abi introuvable (HTTP ${c.responseCode})")
            val tmp = File(out.path + ".part")
            c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            if (!tmp.renameTo(out)) throw java.io.IOException("APK $abi : enregistrement impossible")
        } finally { c.disconnect() }
        return out
    }

    private fun keyPair(context: Context): AdbKeyPair {
        val dir = File(context.filesDir, "adb666").apply { mkdirs() }
        val priv = File(dir, "adbkey"); val pub = File(dir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
        return AdbKeyPair.read(priv, pub)
    }

    /**
     * Si l'autre Shield a une version plus ancienne : on lui installe la nôtre.
     * Renvoie une ligne pour « Dernières annonces » (null = rien à faire).
     */
    private val busy666 = java.util.concurrent.atomic.AtomicBoolean(false)

    // 666TVF (14/11) : vérifié chaque minute (avant : toutes les 20 min) ; une seule installation à la fois
    suspend fun pushIfNewer(context: Context): String? {
        if (!busy666.compareAndSet(false, true)) return null
        return try { pushIfNewerLocked(context) } finally { busy666.set(false) }
    }

    private suspend fun pushIfNewerLocked(context: Context): String? = withContext(Dispatchers.IO) {
        val peer = Lien666.peer ?: return@withContext null
        val theirs = Lien666.peerVersion() ?: return@withContext null
        val mine = myCode(context)
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!shouldPush(mine, theirs.code, theirs.playing, p.getLong("code", 0L), p.getLong("at", 0L), now)) return@withContext null
        p.edit().putLong("code", mine).putLong("at", now).apply()
        val result = runCatching {
            Dadb.create(peer.host, ADB_PORT, keyPair(context)).use { d ->
                // 14/11 : la Shield du haut (tube) ne prend que l'APK 32 bits (armeabi-v7a) — l'APK d'ici
                // (64 bits) était refusé (INSTALL_FAILED_NO_MATCHING_ABIS). On envoie celui qui lui va.
                val theirAbis = runCatching { d.shell("getprop ro.product.cpu.abilist").output.trim() }.getOrDefault("")
                    .split(',').map { it.trim() }.filter { it.isNotEmpty() }
                val want = pickAbi(theirAbis) ?: myAbi(context)
                val apk = if (want == myAbi(context)) File(context.applicationInfo.sourceDir)
                    else download(context, myName(context), want)
                d.install(apk)
            }
        }
        val name = myName(context)
        if (result.isSuccess) {
            p.edit().putString("last", "$name installée sur ${peer.name}").apply()
            "⬆ Mise à jour $name installée sur l'autre Shield (${peer.name})"
        } else {
            // 14/11 : la vraie raison (connexion refusée = débogage réseau éteint ; délai = demande pas acceptée…)
            val e = result.exceptionOrNull()
            val detail = (e?.javaClass?.simpleName.orEmpty() + " " + e?.message.orEmpty()).replace(Regex("\\s+"), " ").trim().take(120)
            val hint = when {
                detail.contains("refused", true) || detail.contains("ECONNREFUSED") -> "le débogage réseau est éteint sur l'autre Shield (il se coupe parfois au redémarrage) : rallume-le"
                detail.contains("timeout", true) || detail.contains("timed out", true) -> "pas de réponse : accepte la demande sur la TV de l'autre Shield (coche « Toujours autoriser »)"
                detail.contains("INSTALL_FAILED", true) -> "l'autre Shield a refusé l'installation"
                else -> "active « Débogage réseau » sur l'autre Shield et accepte la demande sur sa TV"
            }
            val why = "Mise à jour de l'autre Shield impossible : $hint [$detail]"
            // échec : on réessaie dans 15 min (pas 3 h) — le temps de rallumer le débogage ou d'accepter
            p.edit().putString("last", why).putLong("at", now - 3 * 3600_000L + 15 * 60_000L).apply()
            "⚠ $why"
        }
    }

    fun lastStatus(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("last", null)
}

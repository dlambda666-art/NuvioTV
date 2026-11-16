package com.nuvio.tv.core.tools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FrankenStream (16/11) : internet, vu depuis la Shield.
 *
 *  - [internetUp] : la Shield sort-elle vraiment sur internet ? Avant d'accuser French-Stream ou un
 *    plugin (« HTTP 0 »), on vérifie que ce n'est pas la Shield qui a perdu son câble (alerte de
 *    23h52 le 15/11 : French-Stream allait bien, c'est la Shield du haut qui n'avait plus de réseau).
 *  - le **journal des coupures** : chaque perte de réseau (câble / Ethernet) ou d'internet (réseau
 *    branché mais la box ne sort plus), avec l'heure et la durée, et chaque redémarrage de la Shield
 *    (normal ou coupure de courant). Les deux Shield s'échangent leur journal (réseau de la maison) :
 *    même minute en haut et en bas → c'est la box ; seulement en haut → câble, port ou Shield.
 */
object Net666 {
    private const val PREFS = "666tv_net"
    private const val KEY = "log"
    private const val MAX_ENTRIES = 80
    private const val KEEP_MS = 30L * 24 * 3600_000L

    // ---------- Internet joignable ? ----------

    /** Un réseau est branché (câble ou WiFi). Ne dit pas si la box sort sur internet. */
    fun linked(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    private val PROBES = listOf(
        "https://www.google.com/generate_204",
        "https://cloudflare.com/cdn-cgi/trace",
        "https://api.themoviedb.org/3/configuration"
    )

    /**
     * Internet répond vraiment (petites requêtes, 4 s max chacune). À appeler hors du fil principal.
     * Un seul site qui répond suffit (même un refus 401 de TMDB prouve qu'on sort sur internet).
     */
    fun internetUp(): Boolean = PROBES.any { u ->
        runCatching {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = 4_000; c.readTimeout = 4_000; c.requestMethod = "HEAD"
            c.setRequestProperty("User-Agent", "Mozilla/5.0 FrankenStream")
            try { c.responseCode in 200..499 } finally { c.disconnect() }
        }.getOrDefault(false)
    }

    /** Règle pure (testée) : une panne de service ne compte que si la Shield, elle, a internet. */
    fun shouldBlameService(serviceAnswered: Boolean, shieldOnline: Boolean): Boolean =
        !serviceAnswered && shieldOnline

    // ---------- Journal des coupures ----------

    enum class Kind { LINK, INTERNET, BOOT, POWER }

    /** start/end en ms ; end = 0 tant que la coupure dure. BOOT/POWER : start = end = démarrage. */
    data class Entry(val kind: Kind, val start: Long, val end: Long = 0L)

    @Volatile private var appContext: Context? = null

    private fun prefs(context: Context? = appContext) =
        context?.applicationContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun entries(context: Context): List<Entry> = fromJson(prefs(context)?.getString(KEY, null))

    @Synchronized
    private fun edit(context: Context, f: (List<Entry>) -> List<Entry>) {
        val p = prefs(context) ?: return
        val now = System.currentTimeMillis()
        val next = prune(f(fromJson(p.getString(KEY, null))), now)
        p.edit().putString(KEY, toJson(next).toString()).apply()
    }

    /** Règle pure (testée) : ouvre une coupure (sauf si une du même genre est déjà en cours). */
    fun opened(list: List<Entry>, kind: Kind, now: Long): List<Entry> =
        if (list.any { it.kind == kind && it.end == 0L }) list else list + Entry(kind, now)

    /** Règle pure (testée) : ferme la coupure en cours de ce genre. Moins de 3 s : on l'oublie (micro-coupure). */
    fun closed(list: List<Entry>, kind: Kind, now: Long): List<Entry> = list.mapNotNull {
        if (it.kind == kind && it.end == 0L) {
            if (now - it.start < 3_000L) null else it.copy(end = now)
        } else it
    }

    /**
     * Règle pure (testée) : au démarrage de la Shield. Les coupures restées ouvertes se ferment à
     * l'heure du démarrage. Si la Shield s'était éteinte proprement (redémarrage, mise à jour), c'est
     * un redémarrage ; sinon (rien n'a prévenu), c'est sans doute une coupure de courant.
     */
    fun booted(list: List<Entry>, now: Long, cleanShutdown: Boolean): List<Entry> =
        list.map { if (it.end == 0L && it.kind != Kind.BOOT && it.kind != Kind.POWER) it.copy(end = now) else it } +
            Entry(if (cleanShutdown) Kind.BOOT else Kind.POWER, now, now)

    fun prune(list: List<Entry>, now: Long): List<Entry> =
        list.filter { it.end == 0L || now - it.end < KEEP_MS }.sortedBy { it.start }.takeLast(MAX_ENTRIES)

    /** Règle pure (testée) : il y a combien de temps que le réseau / internet est revenu (0 = coupé en ce moment, -1 = jamais coupé). */
    fun lastDropAgo(list: List<Entry>, now: Long): Long {
        val cuts = list.filter { it.kind == Kind.LINK || it.kind == Kind.INTERNET }
        if (cuts.any { it.end == 0L }) return 0L
        val last = cuts.maxOfOrNull { it.end } ?: return -1L
        return (now - last).coerceAtLeast(0L)
    }

    fun lastDropAgo(context: Context, now: Long = System.currentTimeMillis()): Long = lastDropAgo(entries(context), now)

    /** Texte d'une ligne du journal (testé). */
    fun line(e: Entry, now: Long, locale: Locale = Locale.FRANCE): String {
        val day = SimpleDateFormat("EEE d", locale)
        val hm = SimpleDateFormat("HH:mm", locale)
        val what = when (e.kind) {
            Kind.LINK -> "réseau coupé (câble / Ethernet)"
            Kind.INTERNET -> "internet coupé (câble OK, la box ne sort plus)"
            Kind.BOOT -> "Shield redémarrée"
            Kind.POWER -> "Shield rallumée sans s'être éteinte : coupure de courant ?"
        }
        val start = day.format(Date(e.start)) + " · " + hm.format(Date(e.start))
        return when {
            e.kind == Kind.BOOT || e.kind == Kind.POWER -> "$start · $what"
            e.end == 0L -> "$start → en cours (${duration(now - e.start)}) · $what"
            else -> "$start → ${hm.format(Date(e.end))} (${duration(e.end - e.start)}) · $what"
        }
    }

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "${s} s"
            s < 3600 -> "${s / 60} min"
            else -> "${s / 3600} h ${(s % 3600 / 60).toString().padStart(2, '0')}"
        }
    }

    fun toJson(list: List<Entry>): JSONArray = JSONArray().apply {
        list.forEach { put(JSONObject().put("k", it.kind.name).put("s", it.start).put("e", it.end)) }
    }

    fun fromJson(raw: String?): List<Entry> = runCatching {
        val arr = JSONArray(raw ?: return emptyList())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val k = runCatching { Kind.valueOf(o.getString("k")) }.getOrNull() ?: return@mapNotNull null
            Entry(k, o.optLong("s"), o.optLong("e"))
        }
    }.getOrDefault(emptyList())

    // ---------- Surveillance (dans le service toujours lancé de FrankenStream) ----------

    private var callback: ConnectivityManager.NetworkCallback? = null
    private var shutdownReceiver: BroadcastReceiver? = null
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var linkUp = true
    @Volatile private var validated = true
    private val noInternet = Runnable { appContext?.let { c -> if (linkUp && !validated) edit(c) { opened(it, Kind.INTERNET, System.currentTimeMillis()) } } }

    /** Au démarrage de la Shield (BOOT_COMPLETED). */
    fun onBoot(context: Context) {
        val p = prefs(context) ?: return
        val clean = p.getBoolean("clean_shutdown", false)
        p.edit().putBoolean("clean_shutdown", false).apply()
        val bootAt = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
        edit(context) { list ->
            // déjà noté pour ce démarrage (LOCKED_BOOT puis BOOT) : rien
            if (list.any { (it.kind == Kind.BOOT || it.kind == Kind.POWER) && kotlin.math.abs(it.start - bootAt) < 120_000L }) list
            else booted(list, bootAt, clean)
        }
    }

    fun start(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (callback != null) return
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val now = System.currentTimeMillis()
        linkUp = linked(app)
        validated = runCatching {
            cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        }.getOrDefault(true)
        // état au lancement : une coupure restée ouverte alors que tout va bien se ferme
        if (linkUp) edit(app) { closed(it, Kind.LINK, now) } else edit(app) { opened(it, Kind.LINK, now) }
        if (validated) edit(app) { closed(it, Kind.INTERNET, now) }
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                linkUp = true
                edit(app) { closed(it, Kind.LINK, System.currentTimeMillis()) }
            }

            override fun onLost(network: Network) {
                // une autre connexion a pris le relais ? alors pas de coupure
                main.postDelayed({
                    if (!linked(app)) {
                        linkUp = false
                        main.removeCallbacks(noInternet)
                        edit(app) { opened(closed(it, Kind.INTERNET, System.currentTimeMillis()), Kind.LINK, System.currentTimeMillis()) }
                    }
                }, 1_500L)
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val v = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (v == validated) return
                validated = v
                if (v) {
                    main.removeCallbacks(noInternet)
                    edit(app) { closed(it, Kind.INTERNET, System.currentTimeMillis()) }
                } else {
                    // Android met parfois quelques secondes à revalider : on attend 20 s avant de noter
                    main.removeCallbacks(noInternet)
                    main.postDelayed(noInternet, 20_000L)
                }
            }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) cm.registerDefaultNetworkCallback(cb)
            else cm.registerNetworkCallback(android.net.NetworkRequest.Builder().build(), cb)
            callback = cb
        }
        // extinction propre (redémarrage, mise à jour) : notée pour ne pas la prendre pour une coupure de courant
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                prefs(app)?.edit()?.putBoolean("clean_shutdown", true)?.commit()
            }
        }
        runCatching {
            val f = IntentFilter(Intent.ACTION_SHUTDOWN).apply { addAction("android.intent.action.REBOOT") }
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, f, Context.RECEIVER_EXPORTED) else app.registerReceiver(r, f)
            shutdownReceiver = r
        }
    }

    /** Le journal pour l'autre Shield (réseau de la maison) : heures seulement, rien d'autre. */
    fun export(context: Context): String = toJson(entries(context)).toString()

    /** Nom de cette Shield dans le journal : le tube (sif) est en haut. */
    fun whereAmI(device: String? = Build.DEVICE): String =
        if (device.orEmpty().trim().equals("sif", ignoreCase = true)) "Haut" else "Bas"
}

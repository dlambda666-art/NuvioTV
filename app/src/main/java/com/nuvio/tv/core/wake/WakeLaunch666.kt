package com.nuvio.tv.core.wake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 666TV : ouvre 666TV tout seul quand la Shield démarre ou sort de veille (TV allumée).
 * Android n'autorise une appli à s'ouvrir d'elle-même que si elle a le droit
 * « Afficher par-dessus d'autres applis » : sans lui, rien ne se passe.
 */
object WakeLaunch666 {
    const val EXTRA_WAKE = "666tv.wake"
    private const val TAG = "WakeLaunch666"

    private val _wakeNonce = MutableStateFlow(0)
    /** Augmente à chaque réveil : l'écran d'ouverture (pentagramme + orage) se rejoue. */
    val wakeNonce: StateFlow<Int> = _wakeNonce

    @Volatile private var lastWakeAt = 0L
    private val processStartAt = System.currentTimeMillis()

    fun notifyWake(now: Long = System.currentTimeMillis()) {
        // 23/11 : le réveil peut arriver deux fois (intention + retour à l'écran) : un seul cinéma
        if (!wakeAgain(lastWakeAt, now)) return
        // 23/11 (réparation) : juste après le démarrage, le cinéma du lancement est déjà là : pas de 2e cinéma
        if (now - processStartAt < 25_000L) { lastWakeAt = now; return }
        lastWakeAt = now
        _wakeNonce.value = _wakeNonce.value + 1
    }

    /** Règle pure (testée) : un nouveau réveil compte s'il arrive plus de 5 s après le précédent. */
    fun wakeAgain(lastAt: Long, now: Long): Boolean = lastAt <= 0L || now - lastAt > 5_000L

    // ---------- 23/11 : à chaque allumage de la TV, on repasse par le cinéma ----------
    /** L'écran s'est éteint (TV éteinte, veille de la Shield) depuis le dernier passage de Frank à l'écran. */
    @Volatile var screenWentOff: Boolean = false

    /**
     * Règle pure (testée) : en revenant à l'écran, faut-il rejouer le cinéma (puis les portes et le son) ?
     * Oui si l'écran s'était éteint entre-temps, sauf pendant un film.
     */
    fun replayCinemaOnResume(screenWentOff: Boolean, inPlayer: Boolean): Boolean = screenWentOff && !inPlayer

    /** 23/11 (David) : Frank est passé derrière une autre appli (Orange TV, YouTube…) : quand. 0 = non. */
    @Volatile var backgroundSince: Long = 0L

    /**
     * Règle pure (testée) : en revenant d'une autre appli (Orange TV ou autre), cinéma aussi. Pas pour un aller-retour
     * éclair (< 3 s), pas pendant un film, pas au retour d'un lecteur externe lancé par Frank.
     */
    fun replayCinemaAfterOtherApp(awayMs: Long, inPlayer: Boolean, externalPlayer: Boolean): Boolean =
        awayMs >= 3_000L && !inPlayer && !externalPlayer

    private val _homeNonce = MutableStateFlow(0)
    /** Augmente à chaque appui sur le bouton Accueil quand 666TV est le launcher. */
    val homeNonce: StateFlow<Int> = _homeNonce
    fun notifyHome() { _homeNonce.value = _homeNonce.value + 1 }

    /** FrankenStream est à l'écran (MainActivity entre onResume et onPause). */
    @Volatile var resumed: Boolean = false
    @Volatile var resumedOnce: Boolean = false

    fun onResumed(r: Boolean) { resumed = r; if (r) resumedOnce = true }

    /** Garde en mémoire (même si l'appli est tuée par une mise à jour) que FrankenStream était à l'écran. */
    fun onResumed(context: Context, r: Boolean) {
        onResumed(r)
        runCatching { context.getSharedPreferences(PREFS_OPEN, Context.MODE_PRIVATE).edit().putBoolean("was_front", r).apply() }
        if (r) Rescue666.onFrankInFront(context)
    }

    // ---------- 16/11 : se rouvrir après une mise à jour ----------
    fun markRelaunchAfterUpdate(context: Context) {
        context.getSharedPreferences(PREFS_OPEN, Context.MODE_PRIVATE).edit().putBoolean("relaunch_update", true).commit()
    }

    /**
     * Règle pure (testée) : après une mise à jour, faut-il rouvrir FrankenStream ? Oui si on l'installait
     * depuis la bannière, ou s'il était à l'écran quand la mise à jour l'a fermé. Non s'il était derrière une
     * autre appli (Orange TV en direct : on ne lui passe pas devant).
     */
    fun shouldRelaunchAfterUpdate(fromBanner: Boolean, wasFront: Boolean): Boolean = fromBanner || wasFront

    /** Appelé quand Android signale que FrankenStream vient d'être mis à jour (MY_PACKAGE_REPLACED). */
    fun onUpdated(context: Context) {
        val p = context.getSharedPreferences(PREFS_OPEN, Context.MODE_PRIVATE)
        val relaunch = shouldRelaunchAfterUpdate(p.getBoolean("relaunch_update", false), p.getBoolean("was_front", false))
        p.edit().putBoolean("relaunch_update", false).apply()
        start(context)
        if (relaunch) {
            // 20/11 : le service « Toujours revenir » rouvrira Frank (Android refuse souvent l'ouverture directe)
            Rescue666.markUpdatePending(context)
            launchApp(context)
        }
    }

    /** 16/11 : après un démarrage, on réessaie d'ouvrir FrankenStream (Android laisse parfois l'accueil Nvidia devant). */
    val BOOT_RETRIES_MS = listOf(5_000L, 15_000L, 30_000L, 60_000L)
    const val EXTRA_BOOT = "666tv.boot"

    /** Règle pure (testée) : relancer à ce essai du démarrage ? Pas si FrankenStream s'est déjà affiché. */
    fun shouldRetryBoot(resumedOnce: Boolean, resumedNow: Boolean): Boolean = !resumedOnce && !resumedNow

    // ---------- 16/11 : ouvrir sur l'Abattoir ----------
    private const val PREFS_OPEN = "666tv_open"

    /** Réglage (Outils 666) : à chaque retour sur FrankenStream, on arrive sur l'Abattoir. Activé par défaut. */
    fun abattoirOnOpen(context: Context): Boolean =
        context.getSharedPreferences(PREFS_OPEN, Context.MODE_PRIVATE).getBoolean("abattoir", true)

    fun setAbattoirOnOpen(context: Context, on: Boolean) =
        context.getSharedPreferences(PREFS_OPEN, Context.MODE_PRIVATE).edit().putBoolean("abattoir", on).apply()

    /**
     * Règle pure (testée) : en revenant sur FrankenStream (réveil de la Shield, retour depuis l'accueil
     * Nvidia, démarrage), faut-il repartir de l'Abattoir ? Oui, sauf réglage coupé ou film en cours.
     */
    fun shouldOpenAbattoir(settingOn: Boolean, inPlayer: Boolean): Boolean = settingOn && !inPlayer

    fun isDefaultHome(context: Context): Boolean = runCatching {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    }.getOrDefault(false)

    fun canAutoLaunch(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun start(context: Context, boot: Boolean = false) {
        val intent = Intent(context, WakeLaunchService::class.java).putExtra(EXTRA_BOOT, boot)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }.onFailure { Log.w(TAG, "service non démarré", it) }
    }

    fun launchApp(context: Context) {
        if (!canAutoLaunch(context)) return
        val launch = context.packageManager.getLeanbackLaunchIntentForPackage(context.packageName)
            ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        launch.putExtra(EXTRA_WAKE, true)
        runCatching { context.startActivity(launch) }.onFailure { Log.w(TAG, "ouverture refusée", it) }
    }
}

class WakeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // 16/11 : plus besoin de forcer l'arrêt après une mise à jour : FrankenStream se rouvre tout seul
            runCatching { WakeLaunch666.onUpdated(context) }
            return
        }
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            // 16/11 : journal des coupures (redémarrage ou coupure de courant)
            runCatching { com.nuvio.tv.core.tools.Net666.onBoot(context) }
            WakeLaunch666.start(context, boot = intent.action == Intent.ACTION_BOOT_COMPLETED)
            WakeLaunch666.launchApp(context)
            // 16/11 : un enregistrement coupé par la coupure de courant repart tout de suite, même si
            // FrankenStream ne s'ouvre pas (avant : seulement à l'ouverture de l'appli)
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) runCatching {
                val rec = com.nuvio.tv.core.tools.Record666
                if (rec.enabledHere()) {
                    rec.init(context)
                    if (rec.items.value.any {
                            it.status == com.nuvio.tv.core.tools.Record666.Status.QUEUED ||
                                it.status == com.nuvio.tv.core.tools.Record666.Status.RUNNING
                        })
                        com.nuvio.tv.core.tools.Record666Service.start(context)
                }
            }
        }
    }
}

class WakeLaunchService : Service() {
    private var receiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL, "FrankenStream au réveil", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val notification = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this))
            .setContentTitle("FrankenStream")
            .setContentText("S'ouvre quand la TV s'allume")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID, notification)
            }
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) WakeLaunch666.screenWentOff = true
                if (intent.action == Intent.ACTION_SCREEN_ON) WakeLaunch666.launchApp(context)
            }
        }
        receiver = r
        registerReceiver(r, IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) })
        // 16/11 : journal des coupures réseau (ce service tourne tout le temps)
        runCatching { com.nuvio.tv.core.tools.Net666.start(this) }
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var bootRetriesArmed = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 16/11 : après une coupure de courant, la Shield restait sur l'accueil Nvidia. On réessaie
        // d'ouvrir FrankenStream à 5, 15, 30 et 60 s, tant qu'il ne s'est pas affiché.
        if (intent?.getBooleanExtra(WakeLaunch666.EXTRA_BOOT, false) == true && !bootRetriesArmed) {
            bootRetriesArmed = true
            WakeLaunch666.BOOT_RETRIES_MS.forEach { ms ->
                handler.postDelayed({
                    if (WakeLaunch666.shouldRetryBoot(WakeLaunch666.resumedOnce, WakeLaunch666.resumed)) {
                        WakeLaunch666.launchApp(this)
                    }
                }, ms)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "666tv_wake"
        const val ID = 6660
    }
}

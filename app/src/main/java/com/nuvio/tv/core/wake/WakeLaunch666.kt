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

    fun notifyWake() { _wakeNonce.value = _wakeNonce.value + 1 }

    private val _homeNonce = MutableStateFlow(0)
    /** Augmente à chaque appui sur le bouton Accueil quand 666TV est le launcher. */
    val homeNonce: StateFlow<Int> = _homeNonce
    fun notifyHome() { _homeNonce.value = _homeNonce.value + 1 }

    fun isDefaultHome(context: Context): Boolean = runCatching {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    }.getOrDefault(false)

    fun canAutoLaunch(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun start(context: Context) {
        val intent = Intent(context, WakeLaunchService::class.java)
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
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            WakeLaunch666.start(context)
            WakeLaunch666.launchApp(context)
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
                NotificationChannel(CHANNEL, "666TV au réveil", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val notification = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this))
            .setContentTitle("666TV")
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
                if (intent.action == Intent.ACTION_SCREEN_ON) WakeLaunch666.launchApp(context)
            }
        }
        receiver = r
        registerReceiver(r, IntentFilter(Intent.ACTION_SCREEN_ON))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "666tv_wake"
        const val ID = 6660
    }
}

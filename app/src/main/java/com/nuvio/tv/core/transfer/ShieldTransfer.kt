package com.nuvio.tv.core.transfer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.data.local.PluginDataStore
import com.nuvio.tv.data.local.ProfileDataStoreFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 666TV : transfert des réglages d'une Shield à l'autre, en réseau local uniquement.
 *
 * Contenu : adresses des sources privées (Lumio/Frenchio/StreamFusion…) et réglages
 * du lecteur (voix, épisode suivant, langue…). Le paquet est chiffré (AES-GCM) avec
 * une clé dérivée d'un code à 6 chiffres affiché sur la Shield qui envoie : le code ne
 * circule jamais sur le réseau. Le serveur ne vit que quelques minutes.
 */
@Singleton
class ShieldTransferService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager,
    private val pluginDataStore: PluginDataStore
) {
    data class ImportSummary(val sources: Int, val settings: Int)

    suspend fun buildPayload(privateSourceIds: List<String>): String = withContext(Dispatchers.IO) {
        val sources = JSONObject()
        privateSourceIds.forEach { id ->
            val settings = pluginDataStore.getScraperSettings(id)
            if (settings.isNotEmpty()) sources.put(id, JSONObject(settings))
        }
        val prefs = factory.get(profileManager.activeProfileId.value, PLAYER_FEATURE).data.first()
        val player = JSONArray()
        prefs.asMap().forEach { (key, value) ->
            val (type, json) = when (value) {
                is Boolean -> "b" to value
                is Int -> "i" to value
                is Long -> "l" to value
                is Float -> "f" to value.toDouble()
                is Double -> "d" to value
                is String -> "s" to value
                is Set<*> -> "ss" to JSONArray(value.map { it.toString() })
                else -> return@forEach
            }
            player.put(JSONObject().put("k", key.name).put("t", type).put("v", json))
        }
        JSONObject()
            .put("version", 1)
            .put("from", Build.MODEL ?: "Shield")
            .put("sources", sources)
            .put("player", player)
            .toString()
    }

    suspend fun applyPayload(payload: String): ImportSummary = withContext(Dispatchers.IO) {
        val root = JSONObject(payload)
        val sources = root.optJSONObject("sources") ?: JSONObject()
        var sourceCount = 0
        sources.keys().forEach { id ->
            val obj = sources.optJSONObject(id) ?: return@forEach
            val map = HashMap<String, Any>()
            obj.keys().forEach { k -> map[k] = obj.get(k) }
            val current = pluginDataStore.getScraperSettings(id)
            pluginDataStore.setScraperSettings(id, current + map)
            sourceCount++
        }
        val player = root.optJSONArray("player") ?: JSONArray()
        var settingCount = 0
        factory.get(profileManager.activeProfileId.value, PLAYER_FEATURE).edit { prefs ->
            for (i in 0 until player.length()) {
                val entry = player.optJSONObject(i) ?: continue
                val name = entry.optString("k").takeIf { it.isNotBlank() } ?: continue
                if (applyEntry(prefs, name, entry.optString("t"), entry)) settingCount++
            }
        }
        ImportSummary(sourceCount, settingCount)
    }

    private fun applyEntry(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        name: String,
        type: String,
        entry: JSONObject
    ): Boolean {
        when (type) {
            "b" -> prefs[booleanPreferencesKey(name)] = entry.getBoolean("v")
            "i" -> prefs[intPreferencesKey(name)] = entry.getInt("v")
            "l" -> prefs[longPreferencesKey(name)] = entry.getLong("v")
            "f" -> prefs[floatPreferencesKey(name)] = entry.getDouble("v").toFloat()
            "d" -> prefs[doublePreferencesKey(name)] = entry.getDouble("v")
            "s" -> prefs[stringPreferencesKey(name)] = entry.getString("v")
            "ss" -> {
                val arr = entry.getJSONArray("v")
                prefs[stringSetPreferencesKey(name)] = (0 until arr.length()).map { arr.getString(it) }.toSet()
            }
            else -> return false
        }
        return true
    }

    companion object {
        const val PLAYER_FEATURE = "player_settings"
        const val SERVICE_TYPE = "_six66tv._tcp."
        const val PORT = 8095
        const val PATH = "/api/666/transfer"
    }
}

/** Chiffrement du paquet avec le code à 6 chiffres (PBKDF2 → AES-256-GCM). */
object ShieldTransferCrypto {
    private const val ITERATIONS = 120_000

    fun newPin(): String = "%06d".format(SecureRandom().nextInt(1_000_000))

    fun encrypt(plain: String, pin: String): String {
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(pin, salt), GCMParameterSpec(128, iv))
        val data = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("salt", b64(salt))
            .put("iv", b64(iv))
            .put("data", b64(data))
            .toString()
    }

    /** Renvoie null si le code est faux (tag GCM invalide). */
    fun decrypt(bundle: String, pin: String): String? = runCatching {
        val root = JSONObject(bundle)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(pin, unb64(root.getString("salt"))),
            GCMParameterSpec(128, unb64(root.getString("iv")))
        )
        String(cipher.doFinal(unb64(root.getString("data"))), Charsets.UTF_8)
    }.getOrNull()

    private fun key(pin: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)
}

/** Mini serveur local : sert le paquet chiffré, puis signale qu'il a été récupéré. */
class ShieldTransferServer(
    private val bundle: String,
    private val onServed: () -> Unit
) : NanoHTTPD(ShieldTransferService.PORT) {
    override fun serve(session: IHTTPSession): Response =
        if (session.method == Method.GET && session.uri == ShieldTransferService.PATH) {
            onServed()
            newFixedLengthResponse(Response.Status.OK, "application/json", bundle)
        } else {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
}

/** Annonce et découverte des Shield 666TV sur le réseau local (mDNS / NSD). */
class ShieldTransferNsd(context: Context) {
    data class Peer(val name: String, val host: String, val port: Int)

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var ownName: String? = null

    fun register(name: String) {
        unregister()
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = ShieldTransferService.SERVICE_TYPE
            port = ShieldTransferService.PORT
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) { ownName = info.serviceName }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        runCatching { nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun unregister() {
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
    }

    @Suppress("DEPRECATION")
    fun discover(onPeer: (Peer) -> Unit) {
        stopDiscovery()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceName == ownName) return
                runCatching {
                    nsd.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val host = info.host?.hostAddress ?: return
                            onPeer(Peer(info.serviceName, host, info.port))
                        }
                    })
                }
            }
        }
        discovery = listener
        runCatching { nsd.discoverServices(ShieldTransferService.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun stopDiscovery() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discovery = null
    }
}

/** Récupère le paquet chiffré chez l'autre Shield. */
suspend fun fetchShieldBundle(host: String, port: Int): String? = withContext(Dispatchers.IO) {
    runCatching {
        val url = URL("http://${if (host.contains(':')) "[$host]" else host}:$port${ShieldTransferService.PATH}")
        (url.openConnection() as HttpURLConnection).run {
            connectTimeout = 5_000
            readTimeout = 10_000
            try {
                if (responseCode != 200) null else inputStream.bufferedReader().readText()
            } finally {
                disconnect()
            }
        }
    }.getOrNull()
}

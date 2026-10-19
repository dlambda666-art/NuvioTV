package com.nuvio.tv.core.tools

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

/**
 * 666TV : « Envoyer depuis le téléphone ». 666TV ouvre une petite page sur le Wi-Fi de la
 * maison (http://IP-de-la-Shield:6666). Tu tapes un titre sur ton téléphone, la recherche
 * s'ouvre sur la TV avec les résultats. Rien ne sort de chez toi.
 */
object PhoneLink666 {
    const val PORT = 6666
    private const val TAG = "PhoneLink666"

    data class Request(val query: String, val nonce: Long = System.nanoTime())

    private val _requests = MutableStateFlow<Request?>(null)
    val requests: StateFlow<Request?> = _requests

    var enabled by mutableStateOf(true)
        private set

    @Volatile private var server: ServerSocket? = null

    fun init(context: Context) {
        enabled = context.getSharedPreferences("666tv_outils", Context.MODE_PRIVATE).getBoolean("phone_link", true)
        if (enabled) start(context.applicationContext)
    }

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences("666tv_outils", Context.MODE_PRIVATE).edit().putBoolean("phone_link", value).apply()
        enabled = value
        if (value) start(context.applicationContext) else stop()
    }

    fun consume(request: Request) {
        if (_requests.value == request) _requests.value = null
    }

    /** Adresse à taper sur le téléphone (null si pas de réseau local). */
    fun address(context: Context): String? = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
        val ip = props.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?: return null
        "http://${ip.hostAddress}:$PORT"
    }.getOrNull()

    @Synchronized
    private fun start(appContext: Context) {
        if (server != null) return
        val socket = runCatching { ServerSocket(PORT) }.onFailure { Log.w(TAG, "port busy: ${it.message}") }.getOrNull() ?: return
        server = socket
        Thread({
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                Thread({ handle(appContext, client) }, "PhoneLink666-client").apply { isDaemon = true }.start()
            }
        }, "PhoneLink666").apply { isDaemon = true }.start()
    }

    @Synchronized
    private fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun handle(appContext: Context, client: Socket) {
        client.use { s ->
            s.soTimeout = 8000
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val path = requestLine.split(" ").getOrNull(1) ?: "/"
            val query = path.substringAfter("?", "")
                .split("&")
                .firstOrNull { it.startsWith("q=") }
                ?.substringAfter("q=")
                ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                ?.trim()
                ?.take(120)
            val body = if (path.startsWith("/envoyer") && !query.isNullOrBlank()) {
                _requests.value = Request(query)
                bringToFront(appContext)
                page("Envoyé sur la TV ✓", "« ${escape(query)} » — regarde ta TV 😈", showForm = true)
            } else {
                page("666TV", "Tape un film ou une série, la recherche s'ouvre sur la TV.", showForm = true)
            }
            val bytes = body.toByteArray(Charsets.UTF_8)
            val out = s.getOutputStream()
            out.write(
                ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n").toByteArray()
            )
            out.write(bytes)
            out.flush()
        }
    }

    private fun bringToFront(appContext: Context) {
        runCatching {
            val launch = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
                ?: appContext.packageManager.getLeanbackLaunchIntentForPackage(appContext.packageName)
                ?: return
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            appContext.startActivity(launch)
        }
    }

    private fun escape(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun page(title: String, message: String, showForm: Boolean): String = """
<!doctype html><html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>666TV</title>
<style>
body{margin:0;min-height:100vh;background:#0b0b0b;color:#e6e0da;font-family:system-ui,sans-serif;
display:flex;flex-direction:column;align-items:center;justify-content:center;padding:24px;box-sizing:border-box}
h1{color:#c3121f;font-size:34px;margin:0 0 8px;letter-spacing:2px}
p{color:#c8c3be;text-align:center;max-width:420px}
form{display:flex;gap:8px;width:100%;max-width:440px;margin-top:16px}
input{flex:1;font-size:18px;padding:14px;border-radius:10px;border:2px solid #4cc36b;background:#151515;color:#fff}
button{font-size:18px;padding:14px 18px;border-radius:10px;border:0;background:#c3121f;color:#fff;font-weight:700}
</style></head><body>
<h1>${escape(title)}</h1><p>$message</p>
${if (showForm) """<form action="/envoyer" method="get"><input name="q" placeholder="Chucky, Dune 2…" autofocus autocomplete="off"><button>Envoyer</button></form>""" else ""}
</body></html>
""".trimIndent()
}

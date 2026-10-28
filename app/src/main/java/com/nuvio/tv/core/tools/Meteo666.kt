package com.nuvio.tv.core.tools

import android.content.Context
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 666TVF : météo façon Kodi — maintenant + 7 jours, pour TA ville (réglable dans Outils 666,
 * Grâce-Hollogne par défaut). Service gratuit Open-Meteo, sans clé.
 */
object Meteo666 {
    private const val PREFS = "666tv_meteo"

    data class City(val name: String, val lat: Double, val lon: Double, val detail: String = "")
    val DEFAULT_CITY = City("Grâce-Hollogne", 50.6386, 5.4794, "Liège, Belgique")

    data class Now(
        val tempC: Int, val feelsC: Int, val humidity: Int, val windKmh: Int, val windDir: String,
        val code: Int, val day: Boolean, val sunrise: String?, val sunset: String?
    )
    data class Day(val date: java.time.LocalDate, val code: Int, val maxC: Int, val minC: Int, val rainPct: Int)
    data class Forecast(val city: City, val now: Now, val days: List<Day>, val at: Long)

    @Volatile private var appContext: Context? = null
    @Volatile private var cache: Forecast? = null

    fun init(context: Context) { appContext = context.applicationContext }

    fun city(context: Context? = appContext): City = runCatching {
        val p = context!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val name = p.getString("name", null) ?: return@runCatching DEFAULT_CITY
        City(name, p.getString("lat", null)!!.toDouble(), p.getString("lon", null)!!.toDouble(), p.getString("detail", "").orEmpty())
    }.getOrDefault(DEFAULT_CITY)

    fun setCity(context: Context, c: City) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", c.name).putString("lat", c.lat.toString()).putString("lon", c.lon.toString())
            .putString("detail", c.detail).apply()
        cache = null
    }

    /** Recherche d'une ville par son nom (pour un déménagement). */
    fun search(query: String): List<City> = runCatching {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val raw = Http666.get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=8&language=fr&format=json") ?: return emptyList()
        val arr = JSONObject(raw).optJSONArray("results") ?: return emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            City(
                o.optString("name"), o.optDouble("latitude"), o.optDouble("longitude"),
                listOf(o.optString("admin2"), o.optString("admin1"), o.optString("country")).filter { it.isNotBlank() }.distinct().joinToString(", ")
            )
        }
    }.getOrDefault(emptyList())

    /** Prévisions (gardées 20 min). */
    fun forecast(force: Boolean = false): Forecast? {
        val c = city()
        cache?.let { if (!force && it.city == c && System.currentTimeMillis() - it.at < 20 * 60_000L) return it }
        return runCatching {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${c.lat}&longitude=${c.lon}" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,wind_direction_10m,weather_code,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
                "&timezone=auto&forecast_days=7"
            parse(c, Http666.get(url) ?: return null)
        }.getOrNull()?.also { cache = it }
    }

    internal fun parse(c: City, raw: String, now: Long = System.currentTimeMillis()): Forecast {
        val root = JSONObject(raw)
        val cur = root.getJSONObject("current")
        val daily = root.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val days = (0 until dates.length()).map { i ->
            Day(
                java.time.LocalDate.parse(dates.getString(i)),
                daily.getJSONArray("weather_code").optInt(i),
                Math.round(daily.getJSONArray("temperature_2m_max").optDouble(i)).toInt(),
                Math.round(daily.getJSONArray("temperature_2m_min").optDouble(i)).toInt(),
                daily.optJSONArray("precipitation_probability_max")?.optInt(i) ?: 0
            )
        }
        fun hm(key: String) = daily.optJSONArray(key)?.optString(0)?.substringAfter("T")?.take(5)
        val n = Now(
            Math.round(cur.optDouble("temperature_2m")).toInt(),
            Math.round(cur.optDouble("apparent_temperature")).toInt(),
            cur.optInt("relative_humidity_2m"),
            Math.round(cur.optDouble("wind_speed_10m")).toInt(),
            windDir(cur.optDouble("wind_direction_10m")),
            cur.optInt("weather_code"),
            cur.optInt("is_day", 1) == 1,
            hm("sunrise"), hm("sunset")
        )
        return Forecast(c, n, days, now)
    }

    fun windDir(deg: Double): String {
        val names = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
        return names[((deg % 360 + 360) % 360 / 45.0 + 0.5).toInt() % 8]
    }

    fun label(code: Int, day: Boolean = true): String = when (code) {
        0 -> if (day) "Ciel dégagé" else "Nuit claire"
        1 -> "Peu nuageux"
        2 -> "Partiellement nuageux"
        3 -> "Couvert"
        45, 48 -> "Brouillard"
        in 51..57 -> "Bruine"
        in 61..67 -> "Pluie"
        in 71..77 -> "Neige"
        in 80..82 -> "Averses"
        85, 86 -> "Averses de neige"
        in 95..99 -> "Orage"
        else -> "Nuageux"
    }
}

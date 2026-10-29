package com.nuvio.tv.ui.screens.livetv

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.core.tools.Clock666
import com.nuvio.tv.core.tools.Meteo666
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val MGreen = Color(0xFF4CC36B)
private val MMauve = Color(0xFF9B30D9)
private val MGrey = Color(0xFFC8C3BE)

/** 666TVF : météo façon Kodi — maintenant + 7 jours, pour ta ville (Outils 666). */
@Composable
fun MeteoScreen666() {
    var fc by remember { mutableStateOf<Meteo666.Forecast?>(null) }
    var failed by remember { mutableStateOf(false) }
    // 666TVF : le haut de l'écran suit le jour choisi (aujourd'hui = maintenant)
    var selected by remember { mutableStateOf(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        fc = withContext(Dispatchers.IO) { Meteo666.forecast() }
        failed = fc == null
        runCatching { first.requestFocus() }
    }
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = androidx.compose.ui.res.painterResource(com.nuvio.tv.R.drawable.fond_collections_666),
            contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.14f, modifier = Modifier.fillMaxSize()
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 30.dp)) {
            Text("MÉTÉO", fontSize = 40.sp, fontWeight = FontWeight.Black, color = MGreen)
            val f = fc
            Text(
                (f?.city ?: Meteo666.city()).name +
                    (f?.let { " · mise à jour " + java.text.SimpleDateFormat("HH:mm", Locale.FRANCE).format(java.util.Date(it.at)) } ?: ""),
                color = MGrey, fontSize = 16.sp
            )
            if (f == null) {
                Spacer(Modifier.height(40.dp))
                Text(if (failed) "Météo injoignable pour l'instant" else "Chargement…", color = MGrey, fontSize = 20.sp)
                return@Column
            }
            val sel = f.days.getOrNull(selected)
            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selected == 0 || sel == null) {
                    Text(Clock666.iconFor(f.now.code, f.now.day), fontSize = 96.sp)
                    Spacer(Modifier.width(28.dp))
                    Text("${f.now.tempC}°", fontSize = 80.sp, fontWeight = FontWeight.Light, color = MGreen)
                    Spacer(Modifier.width(34.dp))
                    Column {
                        Text("Maintenant · ${Meteo666.label(f.now.code, f.now.day)} · ressenti ${f.now.feelsC}°", color = MGrey, fontSize = 18.sp)
                        Text("Vent ${f.now.windKmh} km/h ${f.now.windDir} · humidité ${f.now.humidity} %", color = MGrey, fontSize = 18.sp)
                        if (f.now.sunrise != null && f.now.sunset != null) {
                            Text("Lever ${f.now.sunrise} · coucher ${f.now.sunset}", color = MGrey, fontSize = 18.sp)
                        }
                    }
                } else {
                    Text(Clock666.iconFor(sel.code, true), fontSize = 96.sp)
                    Spacer(Modifier.width(28.dp))
                    Text("${sel.maxC}°", fontSize = 80.sp, fontWeight = FontWeight.Light, color = MGreen)
                    Text(" / ${sel.minC}°", fontSize = 40.sp, fontWeight = FontWeight.Light, color = MGrey)
                    Spacer(Modifier.width(34.dp))
                    Column {
                        Text("${dayName(sel.date, false)} ${sel.date.dayOfMonth} · ${Meteo666.label(sel.code, true)}", color = MGrey, fontSize = 18.sp)
                        Text(
                            "Pluie ${sel.rainPct} %" + (if (sel.windKmh > 0) " · vent jusqu'à ${sel.windKmh} km/h ${sel.windDir}" else ""),
                            color = MGrey, fontSize = 18.sp
                        )
                        if (sel.sunrise != null && sel.sunset != null) {
                            Text("Lever ${sel.sunrise} · coucher ${sel.sunset}", color = MGrey, fontSize = 18.sp)
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                f.days.take(7).forEachIndexed { i, d ->
                    DayCard(
                        d, i == 0,
                        Modifier.weight(1f)
                            .onFocusChanged { if (it.isFocused || it.hasFocus) selected = i }
                            .let { if (i == 0) it.focusRequester(first) else it }
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Ville réglable dans Outils 666 → « Ville de la météo »", color = Color(0xFF8F8A85), fontSize = 13.sp)
        }
    }
}

@Composable
private fun DayCard(d: Meteo666.Day, today: Boolean, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = {},
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xD919201D),
            focusedContainerColor = MMauve.copy(alpha = 0.28f)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = androidx.tv.material3.Border(androidx.compose.foundation.BorderStroke(2.dp, MMauve), shape = shape)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(dayName(d.date, today), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White, textAlign = TextAlign.Center)
            Text(d.date.dayOfMonth.toString(), color = Color(0xFF8F8A85), fontSize = 13.sp)
            Text(Clock666.iconFor(d.code, true), fontSize = 40.sp, modifier = Modifier.padding(vertical = 8.dp))
            Text("${d.maxC}°", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MGreen)
            Text("${d.minC}°", fontSize = 17.sp, color = MGrey)
            Text("💧 ${d.rainPct} %", fontSize = 13.sp, color = Color(0xFF9FC3FF), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

internal fun dayName(date: LocalDate, today: Boolean): String =
    if (today) "Aujourd'hui"
    else date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRANCE).replaceFirstChar { it.uppercase() }

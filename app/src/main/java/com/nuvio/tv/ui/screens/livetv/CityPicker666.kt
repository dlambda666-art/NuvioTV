package com.nuvio.tv.ui.screens.livetv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.core.tools.Clock666
import com.nuvio.tv.core.tools.Meteo666
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PMauve = Color(0xFF9B30D9)
private val PGreen = Color(0xFF4CC36B)

/**
 * 666TVF : ville de la météo (déménagement). On tape le nom, on choisit dans la liste.
 * Grâce-Hollogne par défaut.
 */
@Composable
fun CityPicker666() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var city by remember { mutableStateOf(Meteo666.city(context)) }
    var editing by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Meteo666.City>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    LaunchedEffect(editing) { if (editing) { runCatching { field.requestFocus() }; keyboard?.show() } }

    fun search() {
        if (query.isBlank()) return
        keyboard?.hide()
        searching = true
        scope.launch {
            results = withContext(Dispatchers.IO) { Meteo666.search(query) }
            searching = false
        }
    }

    Column {
        Surface(
            onClick = { editing = true },
            modifier = Modifier.fillMaxWidth(),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color(0x33000000),
                focusedContainerColor = PMauve,
                contentColor = PGreen,
                focusedContentColor = Color.White
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text("Ville de la météo : ${city.name}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (city.detail.isNotBlank()) city.detail + " · OK pour changer (déménagement)" else "OK pour changer (déménagement)",
                    fontSize = 13.sp, color = Color(0xFFE6E0DA)
                )
            }
        }
        if (editing) {
            Box(
                Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(field)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSearch = { search() }, onDone = { search() }),
                    textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                    cursorBrush = SolidColor(PGreen),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Tape le nom de ta nouvelle ville…", color = Color(0xFF8F8A85), fontSize = 18.sp)
                        inner()
                    }
                )
            }
            if (searching) Text("Recherche…", color = Color(0xFFC8C3BE), modifier = Modifier.padding(start = 18.dp))
            results.forEach { c ->
                Surface(
                    onClick = {
                        Meteo666.setCity(context, c)
                        Clock666.clearWeather()
                        city = c
                        editing = false
                        results = emptyList()
                        query = ""
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color(0x22000000),
                        focusedContainerColor = PMauve.copy(alpha = 0.5f)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(BorderStroke(2.dp, PMauve), shape = RoundedCornerShape(10.dp))
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f)
                ) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                        Text(c.name, color = PGreen, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        if (c.detail.isNotBlank()) Text(c.detail, color = Color(0xFFC8C3BE), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

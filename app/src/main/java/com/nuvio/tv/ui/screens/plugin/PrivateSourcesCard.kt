package com.nuvio.tv.ui.screens.plugin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * 666TV : « Comet666 – mes sources ».
 * Les adresses de config (qui contiennent les clés debrid) restent
 * uniquement sur cet appareil, dans les réglages des plugins (non
 * synchronisés). Les plugins les lisent via SCRAPER_SETTINGS.url.
 */
data class PrivateSource(val scraperId: String, val label: String)

val PRIVATE_SOURCES = listOf(
    PrivateSource("lumio666", "Lumio"),
    PrivateSource("frenchio666", "Frenchio"),
    PrivateSource("streamfusion666", "StreamFusion"),
    PrivateSource("wastream666", "Wastream")
)

const val PRIVATE_SOURCE_URL_KEY = "url"

/** stremio://… → https://…, sans /manifest.json ni / final. */
fun normalizePrivateSourceUrl(raw: String): String? {
    var url = raw.trim()
    if (url.startsWith("stremio://", ignoreCase = true)) {
        url = "https://" + url.substring("stremio://".length)
    }
    url = url.removeSuffix("/").removeSuffix("/manifest.json").removeSuffix("/")
    if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) {
        return null
    }
    return url
}

/** Uniquement l'hôte, pour l'affichage : jamais la config complète. */
fun privateSourceHost(url: String): String =
    url.substringAfter("://").substringBefore("/")

@Composable
internal fun PrivateSourcesCard(
    savedHosts: Map<String, String>,
    onSave: (scraperId: String, url: String) -> Unit,
    onClear: (scraperId: String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NuvioTheme.colors.BackgroundCard),
        shape = RoundedCornerShape(NuvioTheme.radii.md)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Comet666 – mes sources",
                style = MaterialTheme.typography.titleMedium,
                color = NuvioTheme.colors.TextPrimary
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.xs))
            Text(
                text = "Colle l'adresse de config (ou du manifest) de chaque addon. " +
                    "Tout reste sur cet appareil uniquement et n'est jamais affiché.",
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextSecondary
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)) {
                PRIVATE_SOURCES.forEach { source ->
                    PrivateSourceRow(
                        source = source,
                        savedHost = savedHosts[source.scraperId],
                        onSave = { onSave(source.scraperId, it) },
                        onClear = { onClear(source.scraperId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivateSourceRow(
    source: PrivateSource,
    savedHost: String?,
    onSave: (String) -> Unit,
    onClear: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var isEditing by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    fun submit() {
        if (value.isNotBlank()) {
            onSave(value)
            value = ""
        }
        isEditing = false
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = source.label,
            style = MaterialTheme.typography.bodyLarge,
            color = NuvioTheme.colors.TextPrimary,
            modifier = Modifier.width(130.dp)
        )
        Surface(
            onClick = { isEditing = true },
            modifier = Modifier.weight(1f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundElevated,
                focusedContainerColor = NuvioTheme.colors.BackgroundElevated
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(
                    border = BorderStroke(NuvioTheme.spacing.hairline, NuvioTheme.colors.Border),
                    shape = RoundedCornerShape(NuvioTheme.radii.md)
                ),
                focusedBorder = Border(
                    border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                    shape = RoundedCornerShape(NuvioTheme.radii.md)
                )
            ),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NuvioTheme.radii.md)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f)
        ) {
            Box(modifier = Modifier.padding(NuvioTheme.spacing.md)) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (!it.isFocused && isEditing) {
                                isEditing = false
                                keyboardController?.hide()
                            }
                        },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = NuvioTheme.colors.TextPrimary
                    ),
                    cursorBrush = SolidColor(if (isEditing) NuvioTheme.colors.Primary else Color.Transparent),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(
                                text = if (savedHost != null) {
                                    "✓ Enregistrée ($savedHost) – colle une nouvelle adresse pour remplacer"
                                } else {
                                    "Non configurée – colle l'adresse ici"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (savedHost != null) NuvioTheme.colors.Secondary
                                else NuvioTheme.colors.TextTertiary
                            )
                        }
                        inner()
                    }
                )
            }
        }
        Button(
            onClick = { submit() },
            enabled = value.isNotBlank(),
            colors = ButtonDefaults.colors(
                containerColor = NuvioTheme.colors.Secondary,
                focusedContainerColor = NuvioTheme.colors.SecondaryVariant,
                contentColor = NuvioTheme.colors.OnSecondary,
                focusedContentColor = NuvioTheme.colors.OnSecondaryVariant
            ),
            border = ButtonDefaults.border(
                focusedBorder = Border(
                    border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                    shape = RoundedCornerShape(50)
                )
            )
        ) {
            Text("Enregistrer")
        }
        if (savedHost != null) {
            Button(
                onClick = onClear,
                colors = ButtonDefaults.colors(
                    containerColor = NuvioTheme.colors.BackgroundElevated,
                    contentColor = NuvioTheme.colors.TextPrimary
                ),
                border = ButtonDefaults.border(
                    focusedBorder = Border(
                        border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                        shape = RoundedCornerShape(50)
                    )
                )
            ) {
                Text("Effacer")
            }
        }
    }
}

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.nuvio.tv.core.transfer.ShieldTransferNsd
import com.nuvio.tv.ui.theme.NuvioTheme

/** 666TV : copier sources privées + réglages du lecteur d'une Shield à l'autre. */
@Composable
internal fun ShieldTransferCard(
    onImported: () -> Unit,
    viewModel: ShieldTransferViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        if (state is ShieldTransferState.Imported) onImported()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = NuvioTheme.colors.BackgroundCard),
        shape = RoundedCornerShape(NuvioTheme.radii.md)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Copier vers une autre Shield",
                style = MaterialTheme.typography.titleMedium,
                color = NuvioTheme.colors.TextPrimary
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.xs))
            Text(
                text = "Mes sources + réglages du lecteur (voix, épisode suivant, langue…). " +
                    "Par le réseau de la maison uniquement, chiffré avec un code à 6 chiffres.",
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextSecondary
            )
            Spacer(modifier = Modifier.height(NuvioTheme.spacing.md))

            when (val s = state) {
                ShieldTransferState.Idle -> Row(horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)) {
                    TransferButton("Envoyer mes réglages", primary = true) { viewModel.startSending() }
                    TransferButton("Recevoir d'une autre Shield") { viewModel.startReceiving() }
                }

                ShieldTransferState.Preparing -> Info("Préparation…")

                is ShieldTransferState.Sending -> Column {
                    Text(
                        text = s.pin.chunked(3).joinToString(" "),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = NuvioTheme.colors.Secondary
                    )
                    Info(
                        "Sur l'autre Shield : Plugins → « Recevoir d'une autre Shield », puis tape ce code. " +
                            "Encore %d:%02d.".format(s.secondsLeft / 60, s.secondsLeft % 60)
                    )
                    Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
                    TransferButton("Annuler") { viewModel.reset() }
                }

                ShieldTransferState.Sent -> Column {
                    Info("✓ L'autre Shield a récupéré tes réglages.")
                    Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
                    TransferButton("Terminer", primary = true) { viewModel.reset() }
                }

                is ShieldTransferState.Searching -> Column {
                    Info(if (s.peers.isEmpty()) "Recherche des Shield sur le réseau…" else "Choisis la Shield qui envoie :")
                    Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)) {
                        s.peers.forEach { peer ->
                            TransferButton(peer.name, primary = true) { viewModel.choosePeer(peer) }
                        }
                        TransferButton("Annuler") { viewModel.reset() }
                    }
                }

                is ShieldTransferState.EnterPin -> PinEntry(
                    peer = s.peer,
                    wrongPin = s.wrongPin,
                    onSubmit = { viewModel.importWithPin(s.peer, it) },
                    onCancel = { viewModel.reset() }
                )

                ShieldTransferState.Importing -> Info("Import en cours…")

                is ShieldTransferState.Imported -> Column {
                    Info("✓ ${s.sources} source(s) et ${s.settings} réglage(s) importés. Ils s'appliquent dès la prochaine lecture.")
                    Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
                    TransferButton("OK", primary = true) { viewModel.reset() }
                }

                is ShieldTransferState.Failed -> Column {
                    Info(s.message)
                    Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
                    TransferButton("OK", primary = true) { viewModel.reset() }
                }
            }
        }
    }
}

@Composable
private fun Info(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = NuvioTheme.colors.TextPrimary
    )
}

@Composable
private fun TransferButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = if (primary) {
            ButtonDefaults.colors(
                containerColor = NuvioTheme.colors.Secondary,
                focusedContainerColor = NuvioTheme.colors.SecondaryVariant,
                contentColor = NuvioTheme.colors.OnSecondary,
                focusedContentColor = NuvioTheme.colors.OnSecondaryVariant
            )
        } else {
            ButtonDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundElevated,
                contentColor = NuvioTheme.colors.TextPrimary
            )
        },
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = NuvioTheme.focusRing.border(NuvioTheme.spacing.xxs),
                shape = RoundedCornerShape(50)
            )
        )
    ) {
        Text(label)
    }
}

@Composable
private fun PinEntry(
    peer: ShieldTransferNsd.Peer,
    wrongPin: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var isEditing by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column {
        Info(
            if (wrongPin) "Code incorrect : retape le code affiché sur « ${peer.name} »."
            else "Tape le code affiché sur « ${peer.name} » :"
        )
        Spacer(modifier = Modifier.height(NuvioTheme.spacing.sm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { isEditing = true },
                modifier = Modifier.width(220.dp),
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
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            keyboardController?.hide()
                            isEditing = false
                            onSubmit(pin)
                        }),
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = NuvioTheme.colors.TextPrimary),
                        cursorBrush = SolidColor(NuvioTheme.colors.Primary),
                        decorationBox = { inner ->
                            if (pin.isEmpty()) {
                                Text(
                                    text = "000000",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = NuvioTheme.colors.TextTertiary
                                )
                            }
                            inner()
                        }
                    )
                }
            }
            TransferButton("Importer", primary = true) {
                keyboardController?.hide()
                onSubmit(pin)
            }
            TransferButton("Annuler", onClick = onCancel)
        }
    }
}

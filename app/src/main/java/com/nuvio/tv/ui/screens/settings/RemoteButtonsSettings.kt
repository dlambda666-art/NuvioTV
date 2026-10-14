package com.nuvio.tv.ui.screens.settings

import android.view.KeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsRemote
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.data.local.RemoteButtonBinding
import com.nuvio.tv.data.local.RemoteButtonJob
import com.nuvio.tv.data.local.RemoteButtonMap
import com.nuvio.tv.data.local.RemoteButtonsDataStore
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 666TV : réglages « Boutons de la télécommande ». */
@HiltViewModel
class RemoteButtonsSettingsViewModel @Inject constructor(
    private val dataStore: RemoteButtonsDataStore
) : ViewModel() {
    val map: StateFlow<RemoteButtonMap> = dataStore.map
        .stateIn(viewModelScope, SharingStarted.Eagerly, RemoteButtonMap.EMPTY)

    fun assign(keyCode: Int, binding: RemoteButtonBinding) {
        val current = map.value
        val next = if (binding.job == RemoteButtonJob.NOTHING && keyCode !in RemoteButtonMap.DEFAULT_ROW_KEYS) {
            current.without(keyCode)
        } else {
            current.with(keyCode, binding)
        }
        viewModelScope.launch { dataStore.save(next) }
    }

    fun resetAll() {
        viewModelScope.launch { dataStore.save(RemoteButtonMap.EMPTY) }
    }
}

/** Lignes de la section, à placer dans la liste des réglages de lecture. */
internal fun LazyListScope.remoteButtonsSettingsItems(
    map: RemoteButtonMap,
    onEditKey: (Int) -> Unit,
    onLearnKey: () -> Unit,
    onResetAll: () -> Unit,
    onItemFocused: () -> Unit
) {
    item(key = "remote_buttons_intro") {
        Text(
            text = "Choisis ce que fait chaque touche pendant un film. « Comportement normal » " +
                "garde l'action habituelle. Une touche de ta télécommande n'est pas dans la liste ? " +
                "Utilise « Ajouter une touche ».",
            style = MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.TextSecondary
        )
    }
    map.rowKeys.forEach { keyCode ->
        item(key = "remote_button_$keyCode") {
            SettingsActionRow(
                title = RemoteButtonMap.keyLabel(keyCode),
                subtitle = null,
                value = map.bindingFor(keyCode).label,
                onClick = { onEditKey(keyCode) },
                modifier = Modifier.fillMaxWidth(),
                onFocused = onItemFocused,
                leadingIcon = Icons.Default.SettingsRemote
            )
        }
    }
    item(key = "remote_buttons_learn") {
        SettingsActionRow(
            title = "Ajouter une touche",
            subtitle = "Appuie sur n'importe quelle touche de ta télécommande pour lui donner une action",
            onClick = onLearnKey,
            modifier = Modifier.fillMaxWidth(),
            onFocused = onItemFocused,
            leadingIcon = Icons.Default.Add
        )
    }
    item(key = "remote_buttons_reset") {
        SettingsActionRow(
            title = "Tout remettre par défaut",
            subtitle = "Toutes les touches retrouvent leur comportement habituel",
            onClick = onResetAll,
            modifier = Modifier.fillMaxWidth(),
            onFocused = onItemFocused,
            leadingIcon = Icons.Default.Refresh
        )
    }
}

@Composable
internal fun RemoteButtonPickerDialog(
    keyCode: Int,
    current: RemoteButtonBinding,
    onSelected: (RemoteButtonBinding) -> Unit,
    onDismiss: () -> Unit
) {
    SettingsSingleChoiceDialog(
        title = RemoteButtonMap.keyLabel(keyCode),
        subtitle = "Pendant la lecture, cette touche…",
        options = RemoteButtonMap.ALL_BINDINGS.map { SettingsPickerOption(it, it.label) },
        selectedValue = current,
        onOptionSelected = onSelected,
        onDismiss = onDismiss,
        width = 460.dp,
        maxHeight = 480.dp
    )
}

/** Attend l'appui sur une touche de la télécommande et la renvoie. */
@Composable
internal fun RemoteButtonLearnDialog(
    onKeyLearned: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var message by remember { mutableStateOf("Appuie maintenant sur la touche à personnaliser…") }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    NuvioDialog(
        onDismiss = onDismiss,
        title = "Ajouter une touche",
        subtitle = "Retour pour annuler",
        width = 460.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    if (native.keyCode == KeyEvent.KEYCODE_BACK || native.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                        if (native.action == KeyEvent.ACTION_UP) onDismiss()
                        return@onKeyEvent true
                    }
                    if (native.action != KeyEvent.ACTION_DOWN) return@onKeyEvent true
                    if (RemoteButtonMap.isAssignable(native.keyCode)) {
                        onKeyLearned(native.keyCode)
                    } else {
                        message = "« ${RemoteButtonMap.keyLabel(native.keyCode)} » sert à se déplacer " +
                            "ou à quitter : choisis une autre touche."
                    }
                    true
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = NuvioTheme.colors.TextPrimary
            )
        }
    }
}

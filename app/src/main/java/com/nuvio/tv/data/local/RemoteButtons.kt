package com.nuvio.tv.data.local

import android.view.KeyEvent
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.core.profile.ProfileManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 666TV : boutons de la télécommande personnalisables pendant la lecture.
 * Idée reprise de Nuvio Arc (ysosrs123/NuvioTV-Fork, GPL-3.0), réécrite pour 666TV.
 */
enum class RemoteButtonJob(val id: String, val label: String) {
    NOTHING("nothing", "Comportement normal"),
    PLAY_PAUSE("play_pause", "Lecture / pause"),
    SKIP_FORWARD("skip_forward", "Avancer"),
    SKIP_BACK("skip_back", "Reculer"),
    SUBTITLES_TOGGLE("subtitles_toggle", "Sous-titres oui / non"),
    SUBTITLES_MENU("subtitles", "Menu des sous-titres"),
    NEXT_AUDIO("next_audio", "Piste audio suivante"),
    AUDIO_MENU("audio", "Menu audio"),
    SOURCES("sources", "Changer de source"),
    EPISODES("episodes", "Liste des épisodes"),
    SKIP_INTRO_OR_NEXT("skip_intro_or_next", "Passer l'intro / épisode suivant"),
    NEXT_EPISODE("next_episode", "Épisode suivant"),
    RESTART("restart", "Recommencer au début"),
    SPEED("speed", "Vitesse de lecture"),
    ASPECT("aspect", "Format de l'image"),
    STATS("stats", "Statistiques à l'écran"),
    STREAM_INFO("stream_info", "Infos du flux");

    val needsSkipSize: Boolean get() = this == SKIP_FORWARD || this == SKIP_BACK

    companion object {
        fun fromId(id: String): RemoteButtonJob? = entries.firstOrNull { it.id == id }
    }
}

data class RemoteButtonBinding(val job: RemoteButtonJob, val skipMs: Long = 0L) {
    val label: String
        get() = if (job.needsSkipSize) "${job.label} de ${formatSkip(skipMs)}" else job.label

    companion object {
        val NOTHING = RemoteButtonBinding(RemoteButtonJob.NOTHING)

        fun formatSkip(ms: Long): String = when {
            ms < 60_000L -> "${ms / 1000} s"
            else -> "${ms / 60_000L} min"
        }
    }
}

/** Touche → action. Une touche absente garde son comportement habituel. */
class RemoteButtonMap(val custom: Map<Int, RemoteButtonBinding> = emptyMap()) {

    fun bindingFor(keyCode: Int): RemoteButtonBinding =
        custom[keyCode] ?: RemoteButtonBinding.NOTHING

    /** L'action à faire à la place du lecteur, ou null pour laisser la touche tranquille. */
    fun overrideFor(keyCode: Int): RemoteButtonBinding? {
        if (!isAssignable(keyCode)) return null
        return custom[keyCode]?.takeIf { it.job != RemoteButtonJob.NOTHING }
    }

    /** Touches affichées dans les réglages : la liste de départ, puis les touches apprises. */
    val rowKeys: List<Int>
        get() = DEFAULT_ROW_KEYS + custom.keys.filter { it !in DEFAULT_ROW_KEYS }.sorted()

    fun with(keyCode: Int, binding: RemoteButtonBinding): RemoteButtonMap =
        if (binding.job == RemoteButtonJob.NOTHING && keyCode in DEFAULT_ROW_KEYS) {
            RemoteButtonMap(custom - keyCode)
        } else {
            RemoteButtonMap(custom + (keyCode to binding))
        }

    fun without(keyCode: Int): RemoteButtonMap = RemoteButtonMap(custom - keyCode)

    fun encode(): String = custom.entries.joinToString(",") { (key, binding) ->
        buildString {
            append(key).append('=').append(binding.job.id)
            if (binding.job.needsSkipSize) append('@').append(binding.skipMs)
        }
    }

    override fun equals(other: Any?): Boolean = other is RemoteButtonMap && other.custom == custom
    override fun hashCode(): Int = custom.hashCode()

    companion object {
        val EMPTY = RemoteButtonMap()

        val SKIP_SIZES_MS = listOf(10_000L, 30_000L, 60_000L, 300_000L, 600_000L)

        /** Touches proposées d'office (télécommande Shield + télécommande de la TV via HDMI). */
        val DEFAULT_ROW_KEYS = listOf(
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_PROG_RED,
            KeyEvent.KEYCODE_PROG_GREEN,
            KeyEvent.KEYCODE_PROG_YELLOW,
            KeyEvent.KEYCODE_PROG_BLUE,
            KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_CAPTIONS
        )

        /** Touches qui servent à se déplacer ou à sortir : jamais modifiables. */
        private val LOCKED_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_SLEEP, KeyEvent.KEYCODE_WAKEUP,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_MUTE, KeyEvent.KEYCODE_ASSIST, KeyEvent.KEYCODE_VOICE_ASSIST,
            KeyEvent.KEYCODE_SEARCH, KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_UNKNOWN,
            KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_SETTINGS
        )

        fun isAssignable(keyCode: Int): Boolean = keyCode !in LOCKED_KEYS

        fun keyLabel(keyCode: Int): String = when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "Avance rapide ⏩"
            KeyEvent.KEYCODE_MEDIA_REWIND -> "Retour rapide ⏪"
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> "Lecture / pause ⏯"
            KeyEvent.KEYCODE_MENU -> "Menu ≡"
            KeyEvent.KEYCODE_PROG_RED -> "Touche rouge 🔴"
            KeyEvent.KEYCODE_PROG_GREEN -> "Touche verte 🟢"
            KeyEvent.KEYCODE_PROG_YELLOW -> "Touche jaune 🟡"
            KeyEvent.KEYCODE_PROG_BLUE -> "Touche bleue 🔵"
            KeyEvent.KEYCODE_INFO -> "Info ⓘ"
            KeyEvent.KEYCODE_CHANNEL_UP -> "Chaîne +"
            KeyEvent.KEYCODE_CHANNEL_DOWN -> "Chaîne −"
            KeyEvent.KEYCODE_CAPTIONS -> "Sous-titres (CC)"
            KeyEvent.KEYCODE_MEDIA_NEXT -> "Suivant ⏭"
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> "Précédent ⏮"
            KeyEvent.KEYCODE_GUIDE -> "Guide"
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> "Chiffre ${keyCode - KeyEvent.KEYCODE_0}"
            else -> KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").replace('_', ' ')
                .lowercase().replaceFirstChar { it.uppercase() }
        }

        /** Toutes les actions proposées dans la liste (avancer / reculer pour chaque durée). */
        val ALL_BINDINGS: List<RemoteButtonBinding> = RemoteButtonJob.entries.flatMap { job ->
            if (job.needsSkipSize) SKIP_SIZES_MS.map { RemoteButtonBinding(job, it) }
            else listOf(RemoteButtonBinding(job))
        }

        fun decode(raw: String?): RemoteButtonMap {
            if (raw.isNullOrBlank()) return EMPTY
            val map = LinkedHashMap<Int, RemoteButtonBinding>()
            raw.split(',').forEach { part ->
                val key = part.substringBefore('=').trim().toIntOrNull() ?: return@forEach
                val value = part.substringAfter('=', "")
                val job = RemoteButtonJob.fromId(value.substringBefore('@')) ?: return@forEach
                val skip = value.substringAfter('@', "").toLongOrNull() ?: 0L
                if (!isAssignable(key)) return@forEach
                map[key] = RemoteButtonBinding(job, if (job.needsSkipSize) skip.coerceAtLeast(5_000L) else 0L)
            }
            return RemoteButtonMap(map)
        }
    }
}

/**
 * Stockage par profil, dans les réglages du lecteur : la copie entre Shield
 * (« Copier vers une autre Shield ») emporte donc aussi les boutons.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RemoteButtonsDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    private val key = stringPreferencesKey("remote_buttons_666")

    val map: Flow<RemoteButtonMap> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { RemoteButtonMap.decode(it[key]) }
    }.distinctUntilChanged()

    suspend fun save(map: RemoteButtonMap) {
        factory.get(profileManager.activeProfileId.value, FEATURE).edit { prefs ->
            val encoded = map.encode()
            if (encoded.isBlank()) prefs.remove(key) else prefs[key] = encoded
        }
    }

    private companion object {
        const val FEATURE = "player_settings"
    }
}

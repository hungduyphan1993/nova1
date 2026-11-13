package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "novastream_player_prefs")

enum class PlayerEngine(val shortLabel: String, val displayName: String, val badge: String) {
    EXOPLAYER("ExoPlayer", "Media3 ExoPlayer", "EXO"),
    VLC_EMBEDDED("LibVLC", "Embedded VLC (LibVLC)", "VLC"),
    MPV_EMBEDDED("MPV", "Embedded MPV (libmpv)", "MPV");

    fun next(): PlayerEngine = when (this) {
        EXOPLAYER -> VLC_EMBEDDED
        VLC_EMBEDDED -> MPV_EMBEDDED
        MPV_EMBEDDED -> EXOPLAYER
    }
}

enum class ExternalPlayerTarget(val label: String, val packageName: String?) {
    VLC_EXTERNAL("External VLC", "org.videolan.vlc"),
    MPV_EXTERNAL("External MPV", "is.xyz.mpv"),
    MX_PLAYER("MX Player", "com.mxtech.videoplayer.ad"),
    SYSTEM_CHOOSER("Other Video App", null)
}

enum class DecoderMode(val label: String, val description: String) {
    HW_PLUS("HW+", "Hardware+ Accelerated (MediaCodec + Extension)"),
    HW("HW", "Standard Hardware Decoder"),
    SW("SW", "Software CPU Fallback (FFmpeg/VLC/MPV)");

    fun next(): DecoderMode = when (this) {
        HW_PLUS -> HW
        HW -> SW
        SW -> HW_PLUS
    }
}

enum class AspectRatioMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
    ZOOM("Zoom");

    fun next(): AspectRatioMode = when (this) {
        FIT -> FILL
        FILL -> RATIO_16_9
        RATIO_16_9 -> RATIO_4_3
        RATIO_4_3 -> ZOOM
        ZOOM -> FIT
    }
}

enum class ThemePreset(val label: String) {
    OBSIDIAN("Obsidian Broadcast"),
    OLED_BLACK("OLED Pure Black"),
    EMERALD_STUDIO("Emerald Matrix"),
    DAYLIGHT("Broadcast Daylight")
}

data class PlayerSettings(
    val defaultEngine: PlayerEngine = PlayerEngine.EXOPLAYER,
    val autoFallbackEngine: Boolean = true,
    val decoderMode: DecoderMode = DecoderMode.HW_PLUS,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val bufferSeconds: Int = 12,
    val userAgent: String = "NovaStreamIPTV/1.0 (Android; Media3/LibVLC/libmpv)",
    val parentalPin: String = "0000",
    val parentalEnabled: Boolean = true,
    val themePreset: ThemePreset = ThemePreset.OBSIDIAN
)

class PlayerPreferencesRepository(private val context: Context) {

    private object Keys {
        val DEFAULT_ENGINE = stringPreferencesKey("default_engine")
        val AUTO_FALLBACK = booleanPreferencesKey("auto_fallback_engine")
        val DECODER_MODE = stringPreferencesKey("decoder_mode")
        val ASPECT_RATIO = stringPreferencesKey("aspect_ratio")
        val BUFFER_SECONDS = intPreferencesKey("buffer_seconds")
        val USER_AGENT = stringPreferencesKey("user_agent")
        val PARENTAL_PIN = stringPreferencesKey("parental_pin")
        val PARENTAL_ENABLED = booleanPreferencesKey("parental_enabled")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
    }

    val settingsFlow: Flow<PlayerSettings> = context.dataStore.data.map { prefs ->
        PlayerSettings(
            defaultEngine = prefs[Keys.DEFAULT_ENGINE]?.let {
                runCatching { PlayerEngine.valueOf(it) }.getOrNull()
            } ?: PlayerEngine.EXOPLAYER,
            autoFallbackEngine = prefs[Keys.AUTO_FALLBACK] ?: true,
            decoderMode = prefs[Keys.DECODER_MODE]?.let {
                runCatching { DecoderMode.valueOf(it) }.getOrNull()
            } ?: DecoderMode.HW_PLUS,
            aspectRatioMode = prefs[Keys.ASPECT_RATIO]?.let {
                runCatching { AspectRatioMode.valueOf(it) }.getOrNull()
            } ?: AspectRatioMode.FIT,
            bufferSeconds = (prefs[Keys.BUFFER_SECONDS] ?: 12).coerceIn(1, 60),
            userAgent = prefs[Keys.USER_AGENT] ?: "NovaStreamIPTV/1.0 (Android; Media3/LibVLC/libmpv)",
            parentalPin = prefs[Keys.PARENTAL_PIN] ?: "0000",
            parentalEnabled = prefs[Keys.PARENTAL_ENABLED] ?: true,
            themePreset = prefs[Keys.THEME_PRESET]?.let {
                runCatching { ThemePreset.valueOf(it) }.getOrNull()
            } ?: ThemePreset.OBSIDIAN
        )
    }

    suspend fun setDefaultEngine(engine: PlayerEngine) {
        context.dataStore.edit { it[Keys.DEFAULT_ENGINE] = engine.name }
    }

    suspend fun setAutoFallback(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_FALLBACK] = enabled }
    }

    suspend fun setDecoderMode(mode: DecoderMode) {
        context.dataStore.edit { it[Keys.DECODER_MODE] = mode.name }
    }

    suspend fun setAspectRatioMode(mode: AspectRatioMode) {
        context.dataStore.edit { it[Keys.ASPECT_RATIO] = mode.name }
    }

    suspend fun setBufferSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.BUFFER_SECONDS] = seconds.coerceIn(1, 60) }
    }

    suspend fun setUserAgent(agent: String) {
        context.dataStore.edit {
            it[Keys.USER_AGENT] = agent.ifBlank { "NovaStreamIPTV/1.0 (Android; Media3/LibVLC/libmpv)" }
        }
    }

    suspend fun setParentalPin(pin: String) {
        if (pin.length == 4 && pin.all { it.isDigit() }) {
            context.dataStore.edit { it[Keys.PARENTAL_PIN] = pin }
        }
    }

    suspend fun setParentalEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PARENTAL_ENABLED] = enabled }
    }

    suspend fun setThemePreset(preset: ThemePreset) {
        context.dataStore.edit { it[Keys.THEME_PRESET] = preset.name }
    }
}

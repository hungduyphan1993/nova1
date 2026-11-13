package com.example.player

import android.content.Context
import android.net.Uri
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.preferences.AspectRatioMode
import com.example.data.preferences.DecoderMode
import com.example.data.preferences.PlayerEngine
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.concurrent.TimeUnit

data class TrackOption(
    val id: String,
    val label: String,
    val isSelected: Boolean
)

data class PlaybackTelemetryState(
    val engine: PlayerEngine = PlayerEngine.EXOPLAYER,
    val isPlaying: Boolean = true,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val resolutionLabel: String = "Auto FHD",
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val errorMessage: String? = null
)

class UnifiedPlayerHandle {
    internal var onTogglePlayPause: (() -> Unit)? = null
    internal var onSetPlayWhenReady: ((Boolean) -> Unit)? = null
    internal var onSeekToMs: ((Long) -> Unit)? = null
    internal var onSelectAudioTrack: ((String) -> Unit)? = null
    internal var onSelectSubtitleTrack: ((String) -> Unit)? = null
    internal var onAddExternalSubtitleUrl: ((String) -> Unit)? = null

    fun togglePlayPause() = onTogglePlayPause?.invoke()
    fun setPlaying(play: Boolean) = onSetPlayWhenReady?.invoke(play)
    fun seekTo(positionMs: Long) = onSeekToMs?.invoke(positionMs.coerceAtLeast(0L))
    fun selectAudioTrack(trackId: String) = onSelectAudioTrack?.invoke(trackId)
    fun selectSubtitleTrack(trackId: String) = onSelectSubtitleTrack?.invoke(trackId)
    fun loadExternalSubtitle(subUrl: String) = onAddExternalSubtitleUrl?.invoke(subUrl)
}

/**
 * Thread-safe instance wrapper around native `dev.jdtech.mpv.MPVLib` 1.0.0.
 * Supports independent native instances across Fullscreen, Mini-Player, and 4-Way Multi-Screen!
 */
class MpvPlayerSession(
    context: Context,
    decoderMode: DecoderMode,
    bufferSeconds: Int,
    userAgent: String
) {
    private val lock = Any()
    private var mpv: MPVLib? = null
    private var attachedSurface: Surface? = null

    val isAvailable: Boolean

    init {
        val created = runCatching {
            val inst = MPVLib.create(context.applicationContext) ?: return@runCatching null
            inst.setOptionString("config", "no")
            inst.setOptionString("vo", "gpu")
            inst.setOptionString("gpu-context", "android")
            inst.setOptionString("ao", "audiotrack,opensles")
            inst.setOptionString("keep-open", "yes")
            inst.setOptionString("force-window", "yes")
            val hwdec = when (decoderMode) {
                DecoderMode.HW_PLUS -> "mediacodec-copy"
                DecoderMode.HW -> "mediacodec"
                DecoderMode.SW -> "no"
            }
            inst.setOptionString("hwdec", hwdec)
            inst.setOptionString("user-agent", userAgent)
            inst.setOptionString("cache", "yes")
            inst.setOptionString("cache-secs", bufferSeconds.coerceIn(1, 60).toString())
            inst.setOptionString("demuxer-max-bytes", "${bufferSeconds.coerceIn(2, 60) * 2 * 1024 * 1024}")
            inst.init()
            inst
        }.getOrNull()
        mpv = created
        isAvailable = created != null
    }

    fun attachSurface(surface: Surface, width: Int, height: Int) {
        synchronized(lock) {
            val inst = mpv ?: return
            runCatching {
                if (attachedSurface != null && attachedSurface !== surface) {
                    inst.detachSurface()
                }
                inst.attachSurface(surface)
                attachedSurface = surface
                if (width > 0 && height > 0) {
                    inst.setPropertyString("android-surface-size", "${width}x${height}")
                }
            }
        }
    }

    fun updateSurfaceSize(width: Int, height: Int) {
        synchronized(lock) {
            val inst = mpv ?: return
            if (width <= 0 || height <= 0) return
            runCatching { inst.setPropertyString("android-surface-size", "${width}x${height}") }
        }
    }

    fun detachSurface(surface: Surface?) {
        synchronized(lock) {
            val inst = mpv ?: return
            if (surface == null || attachedSurface === surface) {
                runCatching { inst.detachSurface() }
                attachedSurface = null
            }
        }
    }

    fun loadUrl(url: String, startPosMs: Long, isMuted: Boolean) {
        synchronized(lock) {
            val inst = mpv ?: return
            if (url.isBlank()) return
            runCatching {
                inst.command(arrayOf("loadfile", url, "replace"))
                inst.setPropertyBoolean("mute", isMuted)
                inst.setPropertyBoolean("pause", false)
                if (startPosMs > 1500L) {
                    inst.setPropertyString("start", "+${startPosMs / 1000}")
                }
            }
        }
    }

    fun setPaused(paused: Boolean) {
        synchronized(lock) {
            val inst = mpv ?: return
            runCatching { inst.setPropertyBoolean("pause", paused) }
        }
    }

    fun setMuted(muted: Boolean) {
        synchronized(lock) {
            val inst = mpv ?: return
            runCatching { inst.setPropertyBoolean("mute", muted) }
        }
    }

    fun seekToSeconds(seconds: Double) {
        synchronized(lock) {
            val inst = mpv ?: return
            runCatching { inst.command(arrayOf("seek", seconds.toString(), "absolute")) }
        }
    }

    fun cycleAudioTrack() {
        synchronized(lock) {
            val inst = mpv ?: return
            runCatching { inst.command(arrayOf("cycle", "aid")) }
        }
    }

    fun cycleSubtitleTrack() {
        synchronized(lock) {
            val inst = mpv ?: return
            runCatching { inst.command(arrayOf("cycle", "sid")) }
        }
    }

    fun addExternalSubtitle(subUrl: String) {
        synchronized(lock) {
            val inst = mpv ?: return
            if (subUrl.isBlank()) return
            runCatching { inst.command(arrayOf("sub-add", subUrl, "select")) }
        }
    }

    fun applyAspectRatio(mode: AspectRatioMode) {
        synchronized(lock) {
            val inst = mpv ?: return
            runCatching {
                when (mode) {
                    AspectRatioMode.FIT -> {
                        inst.setPropertyString("video-aspect-override", "-1")
                        inst.setPropertyDouble("panscan", 0.0)
                    }
                    AspectRatioMode.FILL -> {
                        inst.setPropertyString("video-aspect-override", "-1")
                        inst.setPropertyDouble("panscan", 1.0)
                    }
                    AspectRatioMode.RATIO_16_9 -> {
                        inst.setPropertyString("video-aspect-override", "16:9")
                        inst.setPropertyDouble("panscan", 0.0)
                    }
                    AspectRatioMode.RATIO_4_3 -> {
                        inst.setPropertyString("video-aspect-override", "4:3")
                        inst.setPropertyDouble("panscan", 0.0)
                    }
                    AspectRatioMode.ZOOM -> {
                        inst.setPropertyString("video-aspect-override", "-1")
                        inst.setPropertyDouble("panscan", 0.75)
                    }
                }
            }
        }
    }

    fun readSnapshot(): Triple<Boolean, Long, Long> {
        synchronized(lock) {
            val inst = mpv ?: return Triple(false, 0L, 0L)
            return runCatching {
                val paused = inst.getPropertyBoolean("pause") ?: false
                val posSec = inst.getPropertyDouble("time-pos") ?: 0.0
                val durSec = inst.getPropertyDouble("duration") ?: 0.0
                Triple(!paused, (posSec * 1000.0).toLong().coerceAtLeast(0L), (durSec * 1000.0).toLong().coerceAtLeast(0L))
            }.getOrDefault(Triple(true, 0L, 0L))
        }
    }

    fun release() {
        synchronized(lock) {
            val inst = mpv ?: return
            runCatching {
                if (attachedSurface != null) {
                    inst.detachSurface()
                    attachedSurface = null
                }
                inst.destroy()
            }
            mpv = null
        }
    }
}

@Composable
fun UnifiedVideoSurface(
    streamUrl: String,
    engine: PlayerEngine,
    decoderMode: DecoderMode,
    aspectRatioMode: AspectRatioMode,
    bufferSeconds: Int,
    userAgent: String,
    isMuted: Boolean,
    initialPositionMs: Long = 0L,
    autoFallbackOnError: Boolean = true,
    handle: UnifiedPlayerHandle = remember { UnifiedPlayerHandle() },
    onEngineFallback: ((PlayerEngine) -> Unit)? = null,
    onTelemetryUpdate: ((PlaybackTelemetryState) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var lastKnownPosMs by remember(streamUrl) { mutableLongStateOf(initialPositionMs) }
    var telemetry by remember(streamUrl, engine) {
        mutableStateOf(PlaybackTelemetryState(engine = engine, isBuffering = true))
    }

    val latestTelemetryCallback by rememberUpdatedState(onTelemetryUpdate)
    val latestFallbackCallback by rememberUpdatedState(onEngineFallback)

    LaunchedEffect(telemetry) {
        latestTelemetryCallback?.invoke(telemetry)
    }

    val containerModifier = when (aspectRatioMode) {
        AspectRatioMode.RATIO_16_9 -> modifier.aspectRatio(16f / 9f, matchHeightConstraintsFirst = false)
        AspectRatioMode.RATIO_4_3 -> modifier.aspectRatio(4f / 3f, matchHeightConstraintsFirst = true)
        AspectRatioMode.ZOOM -> modifier.graphicsLayer(scaleX = 1.18f, scaleY = 1.18f)
        else -> modifier
    }

    Box(
        modifier = containerModifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when (engine) {
            PlayerEngine.EXOPLAYER -> ExoPlayerSurfaceImpl(
                streamUrl = streamUrl,
                decoderMode = decoderMode,
                aspectRatioMode = aspectRatioMode,
                bufferSeconds = bufferSeconds,
                userAgent = userAgent,
                isMuted = isMuted,
                startPositionMs = lastKnownPosMs,
                handle = handle,
                onStateChange = { state ->
                    if (state.positionMs > 0L) lastKnownPosMs = state.positionMs
                    telemetry = state
                    if (state.errorMessage != null && autoFallbackOnError && latestFallbackCallback != null) {
                        latestFallbackCallback?.invoke(PlayerEngine.VLC_EMBEDDED)
                    }
                }
            )
            PlayerEngine.VLC_EMBEDDED -> LibVlcSurfaceImpl(
                streamUrl = streamUrl,
                decoderMode = decoderMode,
                aspectRatioMode = aspectRatioMode,
                bufferSeconds = bufferSeconds,
                userAgent = userAgent,
                isMuted = isMuted,
                startPositionMs = lastKnownPosMs,
                handle = handle,
                onStateChange = { state ->
                    if (state.positionMs > 0L) lastKnownPosMs = state.positionMs
                    telemetry = state
                    if (state.errorMessage != null && autoFallbackOnError && latestFallbackCallback != null) {
                        latestFallbackCallback?.invoke(PlayerEngine.MPV_EMBEDDED)
                    }
                }
            )
            PlayerEngine.MPV_EMBEDDED -> LibMpvSurfaceImpl(
                streamUrl = streamUrl,
                decoderMode = decoderMode,
                aspectRatioMode = aspectRatioMode,
                bufferSeconds = bufferSeconds,
                userAgent = userAgent,
                isMuted = isMuted,
                startPositionMs = lastKnownPosMs,
                handle = handle,
                onStateChange = { state ->
                    if (state.positionMs > 0L) lastKnownPosMs = state.positionMs
                    telemetry = state
                }
            )
        }

        if (telemetry.isBuffering && telemetry.errorMessage == null) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
        }

        telemetry.errorMessage?.let { errMsg ->
            Column(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.82f), MaterialTheme.shapes.medium)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Playback error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${engine.displayName}: $errMsg",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
                if (latestFallbackCallback != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { latestFallbackCallback?.invoke(engine.next()) },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Switch to ${engine.next().shortLabel}", modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ExoPlayerSurfaceImpl(
    streamUrl: String,
    decoderMode: DecoderMode,
    aspectRatioMode: AspectRatioMode,
    bufferSeconds: Int,
    userAgent: String,
    isMuted: Boolean,
    startPositionMs: Long,
    handle: UnifiedPlayerHandle,
    onStateChange: (PlaybackTelemetryState) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnState by rememberUpdatedState(onStateChange)
    var externalSubtitleUri by remember(streamUrl) { mutableStateOf<String?>(null) }

    val exoPlayer = remember(decoderMode, bufferSeconds, userAgent) {
        val httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val dataSourceFactory = OkHttpDataSource.Factory(httpClient)
            .setUserAgent(userAgent)

        val minBufferMs = (bufferSeconds.coerceIn(1, 60) * 1000).coerceAtLeast(1500)
        val maxBufferMs = (minBufferMs * 2).coerceAtLeast(15_000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBufferMs, maxBufferMs, 1000, 2000)
            .build()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
            setExtensionRendererMode(
                when (decoderMode) {
                    DecoderMode.HW_PLUS -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    DecoderMode.HW -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    DecoderMode.SW -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                }
            )
        }

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    LaunchedEffect(exoPlayer, isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(exoPlayer, streamUrl, externalSubtitleUri) {
        if (streamUrl.isBlank()) return@LaunchedEffect
        val builder = MediaItem.Builder().setUri(Uri.parse(streamUrl))
        externalSubtitleUri?.takeIf { it.isNotBlank() }?.let { sub ->
            builder.setSubtitleConfigurations(
                listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub))
                        .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                        .setLanguage("en")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            )
        }
        exoPlayer.setMediaItem(builder.build())
        exoPlayer.prepare()
        if (startPositionMs > 1000L) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer) {
        handle.onTogglePlayPause = {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        }
        handle.onSetPlayWhenReady = { play ->
            exoPlayer.playWhenReady = play
        }
        handle.onSeekToMs = { ms ->
            exoPlayer.seekTo(ms)
        }
        handle.onSelectAudioTrack = { trackKey ->
            val parts = trackKey.split(":")
            val gIdx = parts.getOrNull(0)?.toIntOrNull()
            val tIdx = parts.getOrNull(1)?.toIntOrNull()
            if (gIdx != null && tIdx != null && gIdx < exoPlayer.currentTracks.groups.size) {
                val group = exoPlayer.currentTracks.groups[gIdx]
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, tIdx))
                    .build()
            }
        }
        handle.onSelectSubtitleTrack = { trackKey ->
            if (trackKey == "OFF") {
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            } else {
                val parts = trackKey.split(":")
                val gIdx = parts.getOrNull(0)?.toIntOrNull()
                val tIdx = parts.getOrNull(1)?.toIntOrNull()
                if (gIdx != null && tIdx != null && gIdx < exoPlayer.currentTracks.groups.size) {
                    val group = exoPlayer.currentTracks.groups[gIdx]
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, tIdx))
                        .build()
                }
            }
        }
        handle.onAddExternalSubtitleUrl = { url ->
            externalSubtitleUri = url
        }

        var currentRes = "1080p FHD"
        var currentError: String? = null

        fun emitSnapshot() {
            val (audios, subs) = extractExoTracks(exoPlayer.currentTracks)
            latestOnState(
                PlaybackTelemetryState(
                    engine = PlayerEngine.EXOPLAYER,
                    isPlaying = exoPlayer.isPlaying,
                    isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                    positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    durationMs = exoPlayer.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L,
                    resolutionLabel = currentRes,
                    audioTracks = audios,
                    subtitleTracks = subs,
                    errorMessage = currentError
                )
            )
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) currentError = null
                emitSnapshot()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) = emitSnapshot()
            override fun onTracksChanged(tracks: Tracks) = emitSnapshot()

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) {
                    currentRes = when {
                        videoSize.height >= 2160 -> "4K UHD (${videoSize.width}x${videoSize.height})"
                        videoSize.height >= 1080 -> "1080p FHD"
                        videoSize.height >= 720 -> "720p HD"
                        else -> "${videoSize.width}x${videoSize.height}"
                    }
                    emitSnapshot()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                currentError = error.errorCodeName
                emitSnapshot()
            }
        }

        exoPlayer.addListener(listener)
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (isActive) {
            delay(500L)
            val (audios, subs) = extractExoTracks(exoPlayer.currentTracks)
            latestOnState(
                PlaybackTelemetryState(
                    engine = PlayerEngine.EXOPLAYER,
                    isPlaying = exoPlayer.isPlaying,
                    isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING,
                    positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    durationMs = exoPlayer.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L,
                    audioTracks = audios,
                    subtitleTracks = subs
                )
            )
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                player = exoPlayer
            }
        },
        update = { view ->
            view.player = exoPlayer
            view.resizeMode = when (aspectRatioMode) {
                AspectRatioMode.FIT, AspectRatioMode.RATIO_16_9, AspectRatioMode.RATIO_4_3 ->
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        }
    )
}

private fun extractExoTracks(tracks: Tracks): Pair<List<TrackOption>, List<TrackOption>> {
    val audios = ArrayList<TrackOption>()
    val subs = ArrayList<TrackOption>()
    subs.add(TrackOption("OFF", "Subtitles Off", isSelected = true))
    var anySubSelected = false

    tracks.groups.forEachIndexed { gIdx, group ->
        for (tIdx in 0 until group.length) {
            val fmt = group.getTrackFormat(tIdx)
            val selected = group.isTrackSelected(tIdx)
            val label = buildString {
                append(fmt.label ?: fmt.language?.uppercase() ?: "Track ${tIdx + 1}")
                fmt.sampleMimeType?.substringAfter('/')?.uppercase()?.let { append(" • $it") }
                if (fmt.channelCount > 0) append(" (${fmt.channelCount}ch)")
            }
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> audios.add(TrackOption("$gIdx:$tIdx", label, selected))
                C.TRACK_TYPE_TEXT -> {
                    if (selected) anySubSelected = true
                    subs.add(TrackOption("$gIdx:$tIdx", label, selected))
                }
            }
        }
    }
    if (anySubSelected && subs.isNotEmpty()) {
        subs[0] = subs[0].copy(isSelected = false)
    }
    return audios to subs
}

@Composable
private fun LibVlcSurfaceImpl(
    streamUrl: String,
    decoderMode: DecoderMode,
    aspectRatioMode: AspectRatioMode,
    bufferSeconds: Int,
    userAgent: String,
    isMuted: Boolean,
    startPositionMs: Long,
    handle: UnifiedPlayerHandle,
    onStateChange: (PlaybackTelemetryState) -> Unit
) {
    val context = LocalContext.current
    val latestOnState by rememberUpdatedState(onStateChange)

    val vlcBundle = remember(bufferSeconds, userAgent) {
        runCatching {
            val cacheMs = (bufferSeconds.coerceIn(1, 60) * 1000)
            val options = arrayListOf(
                "--network-caching=$cacheMs",
                "--live-caching=$cacheMs",
                "--http-user-agent=$userAgent",
                "--rtsp-tcp",
                "--no-drop-late-frames"
            )
            val libVlc = LibVLC(context.applicationContext, options)
            val mp = MediaPlayer(libVlc)
            libVlc to mp
        }.getOrNull()
    }

    if (vlcBundle == null) {
        LaunchedEffect(Unit) {
            latestOnState(
                PlaybackTelemetryState(
                    engine = PlayerEngine.VLC_EMBEDDED,
                    isPlaying = false,
                    errorMessage = "LibVLC JNI unavailable in this environment"
                )
            )
        }
        return
    }

    val (libVlc, mediaPlayer) = vlcBundle

    LaunchedEffect(mediaPlayer, isMuted) {
        mediaPlayer.volume = if (isMuted) 0 else 100
    }

    LaunchedEffect(mediaPlayer, aspectRatioMode) {
        when (aspectRatioMode) {
            AspectRatioMode.FIT -> {
                mediaPlayer.aspectRatio = null
                mediaPlayer.scale = 0f
            }
            AspectRatioMode.FILL -> {
                mediaPlayer.aspectRatio = "16:9"
                mediaPlayer.scale = 0f
            }
            AspectRatioMode.RATIO_16_9 -> {
                mediaPlayer.aspectRatio = "16:9"
                mediaPlayer.scale = 0f
            }
            AspectRatioMode.RATIO_4_3 -> {
                mediaPlayer.aspectRatio = "4:3"
                mediaPlayer.scale = 0f
            }
            AspectRatioMode.ZOOM -> {
                mediaPlayer.aspectRatio = null
                mediaPlayer.scale = 1.2f
            }
        }
    }

    LaunchedEffect(mediaPlayer, streamUrl, decoderMode) {
        if (streamUrl.isBlank()) return@LaunchedEffect
        val cacheMs = bufferSeconds.coerceIn(1, 60) * 1000
        val media = Media(libVlc, Uri.parse(streamUrl)).apply {
            when (decoderMode) {
                DecoderMode.HW_PLUS -> setHWDecoderEnabled(true, true)
                DecoderMode.HW -> setHWDecoderEnabled(true, false)
                DecoderMode.SW -> setHWDecoderEnabled(false, false)
            }
            addOption(":network-caching=$cacheMs")
            addOption(":http-user-agent=$userAgent")
        }
        mediaPlayer.media = media
        media.release()
        mediaPlayer.play()
        if (startPositionMs > 1500L) {
            delay(400L)
            mediaPlayer.time = startPositionMs
        }
    }

    DisposableEffect(mediaPlayer) {
        handle.onTogglePlayPause = {
            if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
        }
        handle.onSetPlayWhenReady = { play ->
            if (play) mediaPlayer.play() else mediaPlayer.pause()
        }
        handle.onSeekToMs = { ms ->
            mediaPlayer.time = ms
        }
        handle.onSelectAudioTrack = { idStr ->
            idStr.toIntOrNull()?.let { mediaPlayer.audioTrack = it }
        }
        handle.onSelectSubtitleTrack = { idStr ->
            if (idStr == "OFF") {
                mediaPlayer.spuTrack = -1
            } else {
                idStr.toIntOrNull()?.let { mediaPlayer.spuTrack = it }
            }
        }
        handle.onAddExternalSubtitleUrl = { subUrl ->
            if (subUrl.isNotBlank()) {
                mediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, Uri.parse(subUrl), true)
            }
        }

        mediaPlayer.setEventListener { event ->
            if (event.type == MediaPlayer.Event.EncounteredError) {
                latestOnState(
                    PlaybackTelemetryState(
                        engine = PlayerEngine.VLC_EMBEDDED,
                        isPlaying = false,
                        errorMessage = "LibVLC stream decode error"
                    )
                )
            }
        }

        onDispose {
            runCatching {
                mediaPlayer.stop()
                mediaPlayer.detachViews()
                mediaPlayer.release()
                libVlc.release()
            }
        }
    }

    LaunchedEffect(mediaPlayer) {
        while (isActive) {
            delay(500L)
            val activeAudio = mediaPlayer.audioTrack
            val audioOpts = mediaPlayer.audioTracks?.map {
                TrackOption(it.id.toString(), it.name ?: "Audio ${it.id}", it.id == activeAudio)
            }.orEmpty()
            val activeSpu = mediaPlayer.spuTrack
            val subOpts = buildList {
                add(TrackOption("OFF", "Subtitles Off", activeSpu == -1))
                mediaPlayer.spuTracks?.forEach {
                    if (it.id != -1) {
                        add(TrackOption(it.id.toString(), it.name ?: "Subtitle ${it.id}", it.id == activeSpu))
                    }
                }
            }
            latestOnState(
                PlaybackTelemetryState(
                    engine = PlayerEngine.VLC_EMBEDDED,
                    isPlaying = mediaPlayer.isPlaying,
                    isBuffering = false,
                    positionMs = mediaPlayer.time.coerceAtLeast(0L),
                    durationMs = mediaPlayer.length.coerceAtLeast(0L),
                    resolutionLabel = "LibVLC • ${decoderMode.label}",
                    audioTracks = audioOpts,
                    subtitleTracks = subOpts
                )
            )
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            VLCVideoLayout(ctx).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                mediaPlayer.attachViews(this, null, true, false)
            }
        }
    )
}

@Composable
private fun LibMpvSurfaceImpl(
    streamUrl: String,
    decoderMode: DecoderMode,
    aspectRatioMode: AspectRatioMode,
    bufferSeconds: Int,
    userAgent: String,
    isMuted: Boolean,
    startPositionMs: Long,
    handle: UnifiedPlayerHandle,
    onStateChange: (PlaybackTelemetryState) -> Unit
) {
    val context = LocalContext.current
    val latestOnState by rememberUpdatedState(onStateChange)

    val session = remember(decoderMode, bufferSeconds, userAgent) {
        MpvPlayerSession(context, decoderMode, bufferSeconds, userAgent)
    }

    DisposableEffect(session) {
        if (!session.isAvailable) {
            latestOnState(
                PlaybackTelemetryState(
                    engine = PlayerEngine.MPV_EMBEDDED,
                    isPlaying = false,
                    errorMessage = "libmpv native surface unavailable"
                )
            )
        }
        handle.onTogglePlayPause = {
            val (playing, _, _) = session.readSnapshot()
            session.setPaused(playing)
        }
        handle.onSetPlayWhenReady = { play ->
            session.setPaused(!play)
        }
        handle.onSeekToMs = { ms ->
            session.seekToSeconds(ms / 1000.0)
        }
        handle.onSelectAudioTrack = {
            session.cycleAudioTrack()
        }
        handle.onSelectSubtitleTrack = { id ->
            if (id != "OFF") session.cycleSubtitleTrack()
        }
        handle.onAddExternalSubtitleUrl = { subUrl ->
            session.addExternalSubtitle(subUrl)
        }

        onDispose {
            session.release()
        }
    }

    LaunchedEffect(session, streamUrl) {
        if (session.isAvailable && streamUrl.isNotBlank()) {
            withContext(Dispatchers.IO) {
                session.loadUrl(streamUrl, startPositionMs, isMuted)
            }
        }
    }

    LaunchedEffect(session, isMuted) {
        if (session.isAvailable) {
            withContext(Dispatchers.IO) { session.setMuted(isMuted) }
        }
    }

    LaunchedEffect(session, aspectRatioMode) {
        if (session.isAvailable) {
            withContext(Dispatchers.IO) { session.applyAspectRatio(aspectRatioMode) }
        }
    }

    LaunchedEffect(session) {
        if (!session.isAvailable) return@LaunchedEffect
        while (isActive) {
            delay(500L)
            val (playing, posMs, durMs) = withContext(Dispatchers.IO) {
                session.readSnapshot()
            }
            latestOnState(
                PlaybackTelemetryState(
                    engine = PlayerEngine.MPV_EMBEDDED,
                    isPlaying = playing,
                    isBuffering = false,
                    positionMs = posMs,
                    durationMs = durMs,
                    resolutionLabel = "libmpv GPU • ${decoderMode.label}",
                    audioTracks = listOf(TrackOption("CYCLE_AUDIO", "Cycle MPV Audio Track (aid)", true)),
                    subtitleTracks = listOf(TrackOption("CYCLE_SUB", "Cycle MPV Subtitle Track (sid)", true))
                )
            )
        }
    }

    if (session.isAvailable) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            val rect = holder.surfaceFrame
                            session.attachSurface(holder.surface, rect.width(), rect.height())
                            if (streamUrl.isNotBlank()) {
                                session.loadUrl(streamUrl, startPositionMs, isMuted)
                            }
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            session.updateSurfaceSize(width, height)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            session.detachSurface(holder.surface)
                        }
                    })
                }
            }
        )
    }
}

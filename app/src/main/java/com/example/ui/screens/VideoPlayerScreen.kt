package com.example.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.EpgProgramEntity
import com.example.data.local.StreamEntity
import com.example.data.preferences.AspectRatioMode
import com.example.data.preferences.DecoderMode
import com.example.data.preferences.ExternalPlayerTarget
import com.example.data.preferences.PlayerEngine
import com.example.data.preferences.PlayerSettings
import com.example.player.ExternalPlayerAndAbiManager
import com.example.player.PlaybackTelemetryState
import com.example.player.UnifiedPlayerHandle
import com.example.player.UnifiedVideoSurface
import com.example.ui.components.StylizedMediaArtwork
import com.example.ui.components.TelemetryBadgePill
import com.example.ui.components.formatDurationClock
import com.example.ui.components.formatTimeShort
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LiveCrimson
import com.example.ui.viewmodel.PlaybackRequest
import kotlinx.coroutines.delay

@Composable
fun VideoPlayerScreen(
    request: PlaybackRequest,
    liveChannels: List<StreamEntity>,
    epgMap: Map<String, List<EpgProgramEntity>>,
    isFavorite: Boolean,
    settings: PlayerSettings,
    abiPrimary: String,
    onBack: () -> Unit,
    onSwitchChannel: (StreamEntity, PlayerEngine) -> Unit,
    onToggleFavorite: () -> Unit,
    onUpdateDecoder: (DecoderMode) -> Unit,
    onUpdateAspectRatio: (AspectRatioMode) -> Unit,
    onSaveProgress: (Long, Long, PlayerEngine) -> Unit
) {
    val context = LocalContext.current
    val playerHandle = remember { UnifiedPlayerHandle() }

    var activeEngine by remember(request.streamKey, request.overrideEngine) {
        mutableStateOf(request.overrideEngine ?: settings.defaultEngine)
    }
    var activeDecoder by remember(settings.decoderMode) { mutableStateOf(settings.decoderMode) }
    var activeAspect by remember(settings.aspectRatioMode) { mutableStateOf(settings.aspectRatioMode) }
    var isMuted by remember { mutableStateOf(false) }
    var hudVisible by remember { mutableStateOf(true) }
    var zappingDrawerOpen by remember { mutableStateOf(false) }
    var tracksDialogOpen by remember { mutableStateOf(false) }
    var externalMenuOpen by remember { mutableStateOf(false) }

    var telemetry by remember { mutableStateOf(PlaybackTelemetryState(engine = activeEngine)) }
    var scrubSliderFraction by remember { mutableFloatStateOf(0f) }
    var isUserScrubbing by remember { mutableStateOf(false) }

    // Auto-hide HUD after 5.5 seconds of inactivity while playing
    LaunchedEffect(hudVisible, telemetry.isPlaying, zappingDrawerOpen, tracksDialogOpen) {
        if (hudVisible && telemetry.isPlaying && !zappingDrawerOpen && !tracksDialogOpen && !isUserScrubbing) {
            delay(5500L)
            hudVisible = false
        }
    }

    LaunchedEffect(telemetry.positionMs, telemetry.durationMs) {
        if (!isUserScrubbing && telemetry.durationMs > 0L) {
            scrubSliderFraction = (telemetry.positionMs.toFloat() / telemetry.durationMs.toFloat()).coerceIn(0f, 1f)
        }
    }

    DisposableEffect(request.streamKey, activeEngine) {
        onDispose {
            onSaveProgress(telemetry.positionMs, telemetry.durationMs, activeEngine)
        }
    }

    val nowMillis = System.currentTimeMillis()
    val channelEpg = remember(epgMap, request.tvgId) { epgMap[request.tvgId].orEmpty() }
    val nowProgram = channelEpg.firstOrNull { nowMillis in it.startTimeMillis..it.endTimeMillis }
    val nextProgram = channelEpg.firstOrNull { it.startTimeMillis > (nowProgram?.startTimeMillis ?: nowMillis) }

    fun zapChannel(delta: Int) {
        if (liveChannels.isEmpty()) return
        val idx = liveChannels.indexOfFirst { it.streamKey == request.streamKey }
        val nextIdx = if (idx < 0) 0 else (idx + delta + liveChannels.size) % liveChannels.size
        onSwitchChannel(liveChannels[nextIdx], activeEngine)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    hudVisible = true
                    when (event.key) {
                        Key.DirectionUp, Key.ChannelUp -> {
                            if (request.isLive) zapChannel(-1)
                            true
                        }
                        Key.DirectionDown, Key.ChannelDown -> {
                            if (request.isLive) zapChannel(1)
                            true
                        }
                        Key.DirectionLeft -> {
                            playerHandle.seekTo((telemetry.positionMs - 15_000L).coerceAtLeast(0L))
                            true
                        }
                        Key.DirectionRight -> {
                            playerHandle.seekTo(telemetry.positionMs + 15_000L)
                            true
                        }
                        Key.MediaPlayPause, Key.DirectionCenter -> {
                            playerHandle.togglePlayPause()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (zappingDrawerOpen) zappingDrawerOpen = false
                        else hudVisible = !hudVisible
                    },
                    onDoubleTap = {
                        val nextAspect = activeAspect.next()
                        activeAspect = nextAspect
                        onUpdateAspectRatio(nextAspect)
                    }
                )
            }
    ) {
        // UNOBSCURED FULL-BLEED VIDEO CANVAS (Zero floating center buttons!)
        UnifiedVideoSurface(
            streamUrl = request.streamUrl,
            engine = activeEngine,
            decoderMode = activeDecoder,
            aspectRatioMode = activeAspect,
            bufferSeconds = settings.bufferSeconds,
            userAgent = settings.userAgent,
            isMuted = isMuted,
            initialPositionMs = request.resumePositionMs,
            autoFallbackOnError = settings.autoFallbackEngine,
            handle = playerHandle,
            onEngineFallback = { fallbackEngine -> activeEngine = fallbackEngine },
            onTelemetryUpdate = { state -> telemetry = state },
            modifier = Modifier.fillMaxSize()
        )

        // UNIFIED DOCKED BOTTOM INFO BAR HUD (Video canvas center remains 100% clear)
        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0xD9050812),
                                Color(0xF5070B17)
                            )
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consume taps inside HUD */ }
            ) {
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ROW 1: Channel & EPG Info Header + Technical Stream Telemetry Badges
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                                .testTag("player_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        StylizedMediaArtwork(
                            imageUrl = request.logoUrl,
                            title = request.title,
                            isChannelLogo = true,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (request.channelNumber > 0) {
                                    TelemetryBadgePill(
                                        text = "CH ${request.channelNumber}",
                                        containerColor = Color.White.copy(alpha = 0.15f),
                                        contentColor = ElectricCyan
                                    )
                                }
                                TelemetryBadgePill(
                                    text = when {
                                        request.isCatchUp -> "CATCH-UP"
                                        request.isLive -> "LIVE"
                                        else -> "VOD"
                                    },
                                    containerColor = if (request.isLive) LiveCrimson else ElectricCyan.copy(alpha = 0.25f),
                                    contentColor = Color.White
                                )
                                Text(
                                    text = request.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = if (nowProgram != null) {
                                    "NOW (${formatTimeShort(nowProgram.startTimeMillis)}): ${nowProgram.title}   •   NEXT (${formatTimeShort(nextProgram?.startTimeMillis ?: 0L)}): ${nextProgram?.title ?: "Scheduled"}"
                                } else {
                                    request.subtitle.ifBlank { "Direct Stream • Multi-Engine Ready" }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Technical Stream Telemetry Strip + Favorite Heart
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            TelemetryBadgePill(
                                text = telemetry.resolutionLabel.ifBlank { request.qualityBadge },
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            )
                            TelemetryBadgePill(
                                text = activeEngine.badge,
                                containerColor = ElectricCyan.copy(alpha = 0.24f),
                                contentColor = ElectricCyan
                            )
                            TelemetryBadgePill(
                                text = activeDecoder.label,
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            )
                            TelemetryBadgePill(
                                text = activeAspect.label,
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            )
                            TelemetryBadgePill(
                                text = "Buf ${settings.bufferSeconds}s • $abiPrimary",
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color(0xFFB8F8FF)
                            )
                            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFavorite) LiveCrimson else Color.White
                                )
                            }
                        }
                    }

                    // ROW 2: Integrated Timeshift / VOD Scrub Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = formatDurationClock(telemetry.positionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                        Slider(
                            value = scrubSliderFraction,
                            onValueChange = { frac ->
                                isUserScrubbing = true
                                scrubSliderFraction = frac
                            },
                            onValueChangeFinished = {
                                if (telemetry.durationMs > 0L) {
                                    playerHandle.seekTo((scrubSliderFraction * telemetry.durationMs).toLong())
                                }
                                isUserScrubbing = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricCyan,
                                activeTrackColor = ElectricCyan,
                                inactiveTrackColor = Color.White.copy(alpha = 0.24f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                        )
                        Text(
                            text = if (telemetry.durationMs > 0L) formatDurationClock(telemetry.durationMs)
                            else "TIMESHIFT LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }

                    // ROW 3: Unified Bottom Control Deck (Transport + Zapping + Engine/Utility Pills)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Channel Zapping & Transport Group
                        HudActionPill(label = "CH -", icon = Icons.Default.SkipPrevious) { zapChannel(-1) }
                        HudActionPill(label = "-15s", icon = Icons.Default.Replay10) {
                            playerHandle.seekTo((telemetry.positionMs - 15_000L).coerceAtLeast(0L))
                        }

                        // Primary Play/Pause Pill
                        Button(
                            onClick = { playerHandle.togglePlayPause() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = Color(0xFF041318)
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("player_play_pause_button")
                        ) {
                            Icon(
                                if (telemetry.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play or Pause",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (telemetry.isPlaying) "Pause" else "Play",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        HudActionPill(label = "+15s", icon = Icons.Default.Forward10) {
                            playerHandle.seekTo(telemetry.positionMs + 15_000L)
                        }
                        HudActionPill(label = "CH +", icon = Icons.Default.SkipNext) { zapChannel(1) }
                        HudActionPill(
                            label = if (isMuted) "Unmute" else "Mute",
                            icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp
                        ) { isMuted = !isMuted }

                        // Quick Utility & Tri-Engine Switcher Pills
                        HudActionPill(
                            label = "Engine: ${activeEngine.shortLabel}",
                            icon = Icons.Default.SwapHoriz,
                            highlighted = true,
                            tag = "hud_cycle_engine_pill"
                        ) {
                            activeEngine = activeEngine.next()
                        }

                        if (liveChannels.isNotEmpty()) {
                            HudActionPill(label = "Channel List", icon = Icons.AutoMirrored.Filled.List) {
                                zappingDrawerOpen = !zappingDrawerOpen
                            }
                        }

                        HudActionPill(label = "Ratio: ${activeAspect.label}", icon = Icons.Default.AspectRatio) {
                            val next = activeAspect.next()
                            activeAspect = next
                            onUpdateAspectRatio(next)
                        }

                        HudActionPill(label = "Audio & CC", icon = Icons.Default.ClosedCaption) {
                            tracksDialogOpen = true
                        }

                        HudActionPill(label = "Decoder: ${activeDecoder.label}", icon = Icons.Default.Memory) {
                            val next = activeDecoder.next()
                            activeDecoder = next
                            onUpdateDecoder(next)
                        }

                        HudActionPill(label = "PiP", icon = Icons.Default.PictureInPictureAlt) {
                            val activity = context as? Activity
                            if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                runCatching {
                                    activity.enterPictureInPictureMode(
                                        PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9))
                                            .build()
                                    )
                                }
                            }
                        }

                        Box {
                            HudActionPill(label = "VLC / MPV Ext", icon = Icons.Default.OpenInNew) {
                                externalMenuOpen = true
                            }
                            DropdownMenu(expanded = externalMenuOpen, onDismissRequest = { externalMenuOpen = false }) {
                                ExternalPlayerTarget.entries.forEach { ext ->
                                    DropdownMenuItem(
                                        text = { Text("Open in ${ext.label}") },
                                        onClick = {
                                            externalMenuOpen = false
                                            ExternalPlayerAndAbiManager.launchExternalPlayer(
                                                context = context,
                                                target = ext,
                                                streamUrl = request.streamUrl,
                                                title = request.title,
                                                userAgent = settings.userAgent,
                                                positionMs = telemetry.positionMs
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Channel Zapping Overlay Drawer (Video keeps playing behind it!)
        AnimatedVisibility(
            visible = zappingDrawerOpen,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Surface(
                color = Color(0xEB091022),
                modifier = Modifier
                    .width(290.dp)
                    .fillMaxHeight()
                    .border(1.dp, ElectricCyan.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Text(
                        text = "Quick Channel Zapping",
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricCyan
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(liveChannels.filter { !it.isAdult }, key = { it.streamKey }) { ch ->
                            val isCurrent = ch.streamKey == request.streamKey
                            Surface(
                                color = if (isCurrent) ElectricCyan.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        zappingDrawerOpen = false
                                        onSwitchChannel(ch, activeEngine)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${ch.channelNumber}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ElectricCyan
                                    )
                                    Text(
                                        text = ch.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Audio & Subtitle Track Selector + External Subtitle URL Loader Dialog
        if (tracksDialogOpen) {
            var customSubUrl by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { tracksDialogOpen = false },
                title = { Text("Audio & Subtitle Tracks (${activeEngine.shortLabel})") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Audio Tracks", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        if (telemetry.audioTracks.isEmpty()) {
                            Text("Default Embedded Audio Stream Active", style = MaterialTheme.typography.bodySmall)
                        } else {
                            telemetry.audioTracks.forEach { t ->
                                OutlinedButton(
                                    onClick = {
                                        playerHandle.selectAudioTrack(t.id)
                                        tracksDialogOpen = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (t.isSelected) "✓ ${t.label}" else t.label)
                                }
                            }
                        }

                        Text("Subtitle / CC Tracks", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        telemetry.subtitleTracks.forEach { s ->
                            OutlinedButton(
                                onClick = {
                                    playerHandle.selectSubtitleTrack(s.id)
                                    tracksDialogOpen = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (s.isSelected) "✓ ${s.label}" else s.label)
                            }
                        }

                        OutlinedTextField(
                            value = customSubUrl,
                            onValueChange = { customSubUrl = it },
                            label = { Text("Load External .SRT / OpenSubtitles URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    if (customSubUrl.isNotBlank()) {
                        Button(
                            onClick = {
                                playerHandle.loadExternalSubtitle(customSubUrl.trim())
                                tracksDialogOpen = false
                            }
                        ) {
                            Text("Load Subtitle")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { tracksDialogOpen = false }) { Text("Close") }
                }
            )
        }
    }
}

@Composable
private fun HudActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    highlighted: Boolean = false,
    tag: String? = null,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (highlighted) ElectricCyan.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.12f),
            contentColor = if (highlighted) ElectricCyan else Color.White
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier
            .height(36.dp)
            .let { if (tag != null) it.testTag(tag) else it }
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

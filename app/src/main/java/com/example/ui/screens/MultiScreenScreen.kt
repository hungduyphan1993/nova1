package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.StreamEntity
import com.example.data.preferences.AspectRatioMode
import com.example.data.preferences.PlayerEngine
import com.example.data.preferences.PlayerSettings
import com.example.player.UnifiedVideoSurface
import com.example.ui.components.TelemetryBadgePill
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline

@Composable
fun MultiScreenScreen(
    channels: List<StreamEntity>,
    quadrantChannelKeys: List<String?>,
    quadrantEngines: List<PlayerEngine>,
    activeAudioQuadrant: Int,
    settings: PlayerSettings,
    onBack: () -> Unit,
    onSelectAudioQuadrant: (Int) -> Unit,
    onAssignChannel: (Int, String?) -> Unit,
    onCycleQuadrantEngine: (Int) -> Unit,
    onExpandQuadrant: (StreamEntity, PlayerEngine) -> Unit
) {
    val nonAdultChannels = remember(channels) { channels.filter { !it.isAdult } }
    var pickerForQuadrant by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF04070F))
    ) {
        // Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("multiscreen_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text("4-Way Multi-Screen Broadcast Wall (2x2)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap any screen to switch active audio focus • Mix ExoPlayer, LibVLC & MPV",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                TelemetryBadgePill(
                    text = "AUDIO: QUAD #${activeAudioQuadrant + 1}",
                    containerColor = EmeraldOnline.copy(alpha = 0.2f),
                    contentColor = EmeraldOnline
                )
            }
        }

        // 2x2 Grid of 4 Simultaneous Independent Player Instances
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (row in 0..1) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 0..1) {
                        val slot = row * 2 + col
                        val assignedKey = quadrantChannelKeys.getOrNull(slot)
                        val channel = nonAdultChannels.firstOrNull { it.streamKey == assignedKey }
                        val slotEngine = quadrantEngines.getOrNull(slot) ?: PlayerEngine.EXOPLAYER
                        val hasAudioFocus = activeAudioQuadrant == slot

                        MultiScreenQuadrantCell(
                            slotIndex = slot,
                            channel = channel,
                            engine = slotEngine,
                            hasAudioFocus = hasAudioFocus,
                            settings = settings,
                            onFocusAudio = { onSelectAudioQuadrant(slot) },
                            onOpenChannelPicker = { pickerForQuadrant = slot },
                            onCycleEngine = { onCycleQuadrantEngine(slot) },
                            onExpand = {
                                if (channel != null) onExpandQuadrant(channel, slotEngine)
                            },
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    pickerForQuadrant?.let { targetSlot ->
        AlertDialog(
            onDismissRequest = { pickerForQuadrant = null },
            title = { Text("Select Channel for Screen #${targetSlot + 1}") },
            text = {
                LazyColumn(
                    modifier = Modifier.height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(nonAdultChannels, key = { it.streamKey }) { ch ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAssignChannel(targetSlot, ch.streamKey)
                                    pickerForQuadrant = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("CH ${ch.channelNumber}", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
                                Text(ch.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerForQuadrant = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MultiScreenQuadrantCell(
    slotIndex: Int,
    channel: StreamEntity?,
    engine: PlayerEngine,
    hasAudioFocus: Boolean,
    settings: PlayerSettings,
    onFocusAudio: () -> Unit,
    onOpenChannelPicker: () -> Unit,
    onCycleEngine: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black)
            .border(
                width = if (hasAudioFocus) 2.5.dp else 1.dp,
                color = if (hasAudioFocus) ElectricCyan else Color.White.copy(alpha = 0.2f),
                shape = shape
            )
            .clickable { onFocusAudio() }
            .testTag("multiscreen_quad_$slotIndex")
    ) {
        if (channel == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FilledTonalButton(onClick = onOpenChannelPicker) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Assign Channel #${slotIndex + 1}")
                }
            }
        } else {
            UnifiedVideoSurface(
                streamUrl = channel.streamUrl,
                engine = engine,
                decoderMode = settings.decoderMode,
                aspectRatioMode = AspectRatioMode.FIT,
                bufferSeconds = settings.bufferSeconds,
                userAgent = settings.userAgent,
                isMuted = !hasAudioFocus,
                autoFallbackOnError = true,
                onEngineFallback = { onCycleEngine() },
                modifier = Modifier.fillMaxSize()
            )

            // Top Overlay Badge Strip
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (hasAudioFocus) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = null,
                        tint = if (hasAudioFocus) EmeraldOnline else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "#${slotIndex + 1} • ${channel.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TelemetryBadgePill(
                        text = engine.badge,
                        containerColor = ElectricCyan.copy(alpha = 0.25f),
                        contentColor = ElectricCyan,
                        modifier = Modifier.clickable { onCycleEngine() }
                    )
                    IconButton(onClick = onOpenChannelPicker, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Tv, contentDescription = "Change channel", tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                    IconButton(onClick = onExpand, modifier = Modifier.size(26.dp)) {
                        Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

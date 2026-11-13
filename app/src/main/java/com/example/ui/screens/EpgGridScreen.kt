package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.local.EpgProgramEntity
import com.example.data.local.StreamEntity
import com.example.data.preferences.PlayerEngine
import com.example.ui.components.StylizedMediaArtwork
import com.example.ui.components.TelemetryBadgePill
import com.example.ui.components.formatTimeShort
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LiveCrimson
import java.util.Calendar

@Composable
fun EpgGridScreen(
    channels: List<StreamEntity>,
    epgMap: Map<String, List<EpgProgramEntity>>,
    adultUnlocked: Boolean,
    onBack: () -> Unit,
    onPlayProgram: (StreamEntity, EpgProgramEntity, PlayerEngine?) -> Unit
) {
    var isGridView by rememberSaveable { mutableStateOf(true) }
    val visibleChannels = remember(channels, adultUnlocked) {
        channels.filter { adultUnlocked || !it.isAdult }
    }

    val nowMillis = remember { System.currentTimeMillis() }
    val timelineStartMillis = remember {
        Calendar.getInstance().apply {
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, -6)
        }.timeInMillis
    }
    val totalSlots = 48 // 24 hours in 30-min increments
    val slotDurationMs = 30L * 60L * 1000L
    val slotWidthDp: Dp = 150.dp
    val density = LocalDensity.current

    val horizontalScrollState = rememberScrollState()

    // Auto-scroll 2D timeline to current NOW slot on launch
    LaunchedEffect(isGridView) {
        if (isGridView) {
            val minutesFromStart = ((nowMillis - timelineStartMillis) / 60_000L).coerceAtLeast(0L)
            val pxPerMinute = with(density) { (slotWidthDp.toPx() / 30f) }
            val targetPx = (minutesFromStart * pxPerMinute - with(density) { 120.dp.toPx() }).toInt().coerceAtLeast(0)
            horizontalScrollState.scrollTo(targetPx)
        }
    }

    var selectedPair by remember(visibleChannels, epgMap) {
        val ch = visibleChannels.firstOrNull()
        val prog = ch?.let { epgMap[it.tvgId]?.firstOrNull { p -> nowMillis in p.startTimeMillis..p.endTimeMillis } }
            ?: ch?.let { epgMap[it.tvgId]?.firstOrNull() }
        mutableStateOf(if (ch != null && prog != null) ch to prog else null)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header with 2D Grid / List View Toggle
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
                    IconButton(onClick = onBack, modifier = Modifier.testTag("epg_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text("Electronic Program Guide (EPG)", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "24h XMLTV Timeline • Tap any slot to inspect or launch Catch-Up",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                FilterChip(
                    selected = !isGridView,
                    onClick = { isGridView = !isGridView },
                    leadingIcon = {
                        Icon(
                            if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridOn,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(if (isGridView) "List View" else "2D Grid") },
                    modifier = Modifier.testTag("epg_view_mode_toggle")
                )
            }
        }

        if (isGridView) {
            // Synchronized 2D Scrollable EPG Timeline Grid
            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Sticky Top Time Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .width(156.dp)
                            .height(38.dp)
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("CHANNEL", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }

                    Box(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                        Row {
                            for (i in 0 until totalSlots) {
                                val slotTime = timelineStartMillis + i * slotDurationMs
                                val isCurrentSlot = nowMillis in slotTime until (slotTime + slotDurationMs)
                                Box(
                                    modifier = Modifier
                                        .width(slotWidthDp)
                                        .height(38.dp)
                                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        text = if (isCurrentSlot) "● ${formatTimeShort(slotTime)} NOW" else formatTimeShort(slotTime),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isCurrentSlot) LiveCrimson else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // Channel Rows + Synchronized Horizontal EPG Blocks + Live NOW Marker Line
                val nowOffsetDp = (((nowMillis - timelineStartMillis).toFloat() / slotDurationMs.toFloat()) * slotWidthDp.value).dp

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(visibleChannels, key = { it.streamKey }) { ch ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        ) {
                            // Sticky Left Channel Header Cell
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .width(156.dp)
                                    .fillMaxHeight()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StylizedMediaArtwork(
                                        imageUrl = ch.logoUrl,
                                        title = ch.name,
                                        isChannelLogo = true,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${ch.channelNumber}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = ch.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Synchronized 2D Program Cells
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .horizontalScroll(horizontalScrollState)
                            ) {
                                val progs = epgMap[ch.tvgId].orEmpty()
                                    .filter { it.endTimeMillis > timelineStartMillis }
                                    .sortedBy { it.startTimeMillis }

                                Box(modifier = Modifier.width(slotWidthDp * totalSlots).fillMaxHeight()) {
                                    progs.forEach { prog ->
                                        val clampedStart = prog.startTimeMillis.coerceAtLeast(timelineStartMillis)
                                        val startMin = (clampedStart - timelineStartMillis) / 60_000f
                                        val durMin = ((prog.endTimeMillis - clampedStart) / 60_000f).coerceAtLeast(15f)
                                        val xOffset = (startMin / 30f * slotWidthDp.value).dp
                                        val blockWidth = (durMin / 30f * slotWidthDp.value).dp
                                        val isNow = nowMillis in prog.startTimeMillis..prog.endTimeMillis
                                        val isSelected = selectedPair?.second?.id == prog.id

                                        Box(
                                            modifier = Modifier
                                                .offset(x = xOffset)
                                                .width(blockWidth - 2.dp)
                                                .fillMaxHeight()
                                                .padding(vertical = 3.dp)
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(
                                                    when {
                                                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                        isNow -> ElectricCyan.copy(alpha = 0.16f)
                                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                                                    }
                                                )
                                                .border(
                                                    width = if (isSelected) 1.5.dp else 0.5.dp,
                                                    color = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                                    shape = RoundedCornerShape(7.dp)
                                                )
                                                .clickable { selectedPair = ch to prog }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = prog.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${formatTimeShort(prog.startTimeMillis)} - ${formatTimeShort(prog.endTimeMillis)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    // Glowing Live NOW Vertical Marker Line
                                    if (nowOffsetDp > 0.dp) {
                                        Box(
                                            modifier = Modifier
                                                .offset(x = nowOffsetDp)
                                                .width(2.dp)
                                                .fillMaxHeight()
                                                .background(LiveCrimson)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // EPG Program Listings View Mode
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                visibleChannels.forEach { ch ->
                    val currentAndNext = epgMap[ch.tvgId].orEmpty()
                        .filter { it.endTimeMillis > nowMillis - 2 * 3600_000L }
                        .take(5)
                    items(currentAndNext, key = { "${ch.streamKey}_${it.id}" }) { prog ->
                        val isNow = nowMillis in prog.startTimeMillis..prog.endTimeMillis
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                .clickable { selectedPair = ch to prog }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StylizedMediaArtwork(
                                    imageUrl = ch.logoUrl,
                                    title = ch.name,
                                    isChannelLogo = true,
                                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${ch.name} • ${formatTimeShort(prog.startTimeMillis)} - ${formatTimeShort(prog.endTimeMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isNow) LiveCrimson else MaterialTheme.colorScheme.primary
                                    )
                                    Text(prog.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text(
                                        prog.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Button(
                                    onClick = { onPlayProgram(ch, prog, null) },
                                    modifier = Modifier.height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Text(if (prog.endTimeMillis <= nowMillis) "Replay" else "Watch", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Docked Bottom EPG Info & Control Bar
        selectedPair?.let { (ch, prog) ->
            val isArchive = prog.endTimeMillis <= nowMillis
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryBadgePill(
                            text = if (isArchive) "CATCH-UP ARCHIVE" else "LIVE / SCHEDULED",
                            containerColor = if (isArchive) ElectricCyan.copy(alpha = 0.2f) else LiveCrimson,
                            contentColor = if (isArchive) ElectricCyan else Color.White
                        )
                        Text(
                            text = "CH ${ch.channelNumber} ${ch.name} • ${formatTimeShort(prog.startTimeMillis)} - ${formatTimeShort(prog.endTimeMillis)}",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                    Text(prog.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    if (prog.description.isNotBlank()) {
                        Text(
                            prog.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onPlayProgram(ch, prog, null) },
                            modifier = Modifier.weight(1.2f).height(36.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Icon(
                                if (isArchive) Icons.Default.History else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArchive) "Play Catch-Up" else "Watch Live", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { onPlayProgram(ch, prog, PlayerEngine.VLC_EMBEDDED) },
                            modifier = Modifier.weight(0.8f).height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Play VLC", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { onPlayProgram(ch, prog, PlayerEngine.MPV_EMBEDDED) },
                            modifier = Modifier.weight(0.8f).height(36.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Play MPV", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

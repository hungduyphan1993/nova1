package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CategoryEntity
import com.example.data.local.EpgProgramEntity
import com.example.data.local.StreamEntity
import com.example.data.preferences.AspectRatioMode
import com.example.data.preferences.PlayerEngine
import com.example.data.preferences.PlayerSettings
import com.example.player.UnifiedPlayerHandle
import com.example.player.UnifiedVideoSurface
import com.example.ui.components.EngineAndExternalLauncherButton
import com.example.ui.components.ParentalPinUnlockDialog
import com.example.ui.components.StylizedMediaArtwork
import com.example.ui.components.TelemetryBadgePill
import com.example.ui.components.formatTimeShort
import com.example.ui.components.tvFocusableCard
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LiveCrimson

@Composable
fun LiveTvScreen(
    categories: List<CategoryEntity>,
    channels: List<StreamEntity>,
    epgMap: Map<String, List<EpgProgramEntity>>,
    settings: PlayerSettings,
    adultUnlocked: Boolean,
    catchUpOnly: Boolean,
    onBack: () -> Unit,
    onOpenEpgGrid: () -> Unit,
    onPlayFullscreen: (StreamEntity, PlayerEngine?) -> Unit,
    onToggleFavorite: (StreamEntity) -> Unit,
    onVerifyParentalPin: (String) -> Boolean,
    onRefreshShortEpg: (StreamEntity) -> Unit
) {
    var selectedCategoryId by rememberSaveable { mutableStateOf("ALL") }
    var pendingLockedCategoryId by remember { mutableStateOf<String?>(null) }
    var showPortraitPreview by rememberSaveable { mutableStateOf(false) }
    var miniPlayerEngine by remember(settings.defaultEngine) { mutableStateOf(settings.defaultEngine) }
    var miniMuted by rememberSaveable { mutableStateOf(false) }
    var miniPlaying by rememberSaveable { mutableStateOf(true) }
    val miniPlayerHandle = remember { UnifiedPlayerHandle() }

    val baseChannels = remember(channels, adultUnlocked, settings.parentalEnabled, catchUpOnly) {
        channels.filter { ch ->
            val allowedByParental = !settings.parentalEnabled || adultUnlocked || !ch.isAdult
            val allowedByCatchup = !catchUpOnly || ch.catchupDays > 0
            allowedByParental && allowedByCatchup
        }
    }

    val filteredChannels = remember(baseChannels, selectedCategoryId, channels, adultUnlocked) {
        when (selectedCategoryId) {
            "ALL" -> baseChannels
            "FAV" -> baseChannels.filter { it.isFavorite }
            else -> channels.filter {
                it.categoryId == selectedCategoryId && (!catchUpOnly || it.catchupDays > 0)
            }
        }
    }

    var activeChannelKey by rememberSaveable { mutableStateOf<String?>(null) }
    val activeChannel = remember(filteredChannels, activeChannelKey) {
        filteredChannels.firstOrNull { it.streamKey == activeChannelKey } ?: filteredChannels.firstOrNull()
    }

    LaunchedEffect(activeChannel?.streamKey) {
        activeChannel?.let { onRefreshShortEpg(it) }
    }

    val nowMillis = System.currentTimeMillis()

    if (pendingLockedCategoryId != null) {
        ParentalPinUnlockDialog(
            onDismiss = { pendingLockedCategoryId = null },
            onVerifyPin = onVerifyParentalPin,
            onUnlocked = {
                selectedCategoryId = pendingLockedCategoryId ?: "ALL"
                pendingLockedCategoryId = null
            }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWideThreePane = maxWidth >= 820.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Compact Single-Line Top Bar
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.testTag("livetv_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Column {
                            Text(
                                text = if (catchUpOnly) "Catch-Up & Replay TV" else "Live TV Broadcasts",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1
                            )
                            Text(
                                text = "${filteredChannels.size} channels listed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isWideThreePane) {
                            FilterChip(
                                selected = showPortraitPreview,
                                onClick = { showPortraitPreview = !showPortraitPreview },
                                label = { Text(if (showPortraitPreview) "Hide Preview" else "Preview") },
                                leadingIcon = {
                                    Icon(
                                        if (showPortraitPreview) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("toggle_mini_preview_chip")
                            )
                        }

                        EngineAndExternalLauncherButton(
                            activeEngine = miniPlayerEngine,
                            streamUrl = activeChannel?.streamUrl.orEmpty(),
                            streamTitle = activeChannel?.name.orEmpty(),
                            userAgent = settings.userAgent,
                            onSelectEmbeddedEngine = { miniPlayerEngine = it },
                            compactLabel = !isWideThreePane
                        )
                    }
                }
            }

            if (isWideThreePane) {
                // Smarters Pro 3-Pane Landscape / TV Layout: Category Sidebar | Channel List | Mini-Player + Short EPG
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CategorySidebarPane(
                        categories = categories,
                        allCount = baseChannels.size,
                        favCount = baseChannels.count { it.isFavorite },
                        countsByCat = channels.groupingBy { it.categoryId }.eachCount(),
                        selectedCategoryId = selectedCategoryId,
                        parentalEnabled = settings.parentalEnabled && !adultUnlocked,
                        onSelectCategory = { catId, locked ->
                            if (locked && settings.parentalEnabled && !adultUnlocked) {
                                pendingLockedCategoryId = catId
                            } else {
                                selectedCategoryId = catId
                            }
                        },
                        modifier = Modifier
                            .width(210.dp)
                            .fillMaxHeight()
                    )

                    ChannelListingPane(
                        channels = filteredChannels,
                        activeChannelKey = activeChannel?.streamKey,
                        epgMap = epgMap,
                        nowMillis = nowMillis,
                        onSelectChannel = { ch ->
                            if (activeChannel?.streamKey == ch.streamKey) {
                                onPlayFullscreen(ch, miniPlayerEngine)
                            } else {
                                activeChannelKey = ch.streamKey
                            }
                        },
                        onDirectWatch = { ch -> onPlayFullscreen(ch, miniPlayerEngine) },
                        modifier = Modifier
                            .weight(1.25f)
                            .fillMaxHeight()
                    )

                    MiniPlayerAndShortEpgPane(
                        channel = activeChannel,
                        shortEpg = activeChannel?.let { epgMap[it.tvgId] }.orEmpty(),
                        nowMillis = nowMillis,
                        engine = miniPlayerEngine,
                        settings = settings,
                        isMuted = miniMuted,
                        isPlaying = miniPlaying,
                        handle = miniPlayerHandle,
                        onCycleEngine = { miniPlayerEngine = miniPlayerEngine.next() },
                        onTogglePlay = {
                            miniPlayerHandle.togglePlayPause()
                            miniPlaying = !miniPlaying
                        },
                        onToggleMute = { miniMuted = !miniMuted },
                        onExpandFullscreen = {
                            activeChannel?.let { onPlayFullscreen(it, miniPlayerEngine) }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                // Mobile / Portrait Layout: Category Strip + Optional Inline Mini-Player + Full-Height Channel Listings
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    HorizontalCategoryStrip(
                        categories = categories,
                        selectedCategoryId = selectedCategoryId,
                        parentalEnabled = settings.parentalEnabled && !adultUnlocked,
                        onSelectCategory = { catId, locked ->
                            if (locked && settings.parentalEnabled && !adultUnlocked) {
                                pendingLockedCategoryId = catId
                            } else {
                                selectedCategoryId = catId
                            }
                        }
                    )

                    if (showPortraitPreview && activeChannel != null) {
                        MiniPlayerBoxWithControls(
                            channel = activeChannel,
                            engine = miniPlayerEngine,
                            settings = settings,
                            isMuted = miniMuted,
                            isPlaying = miniPlaying,
                            handle = miniPlayerHandle,
                            onCycleEngine = { miniPlayerEngine = miniPlayerEngine.next() },
                            onTogglePlay = {
                                miniPlayerHandle.togglePlayPause()
                                miniPlaying = !miniPlaying
                            },
                            onToggleMute = { miniMuted = !miniMuted },
                            onExpand = { onPlayFullscreen(activeChannel, miniPlayerEngine) },
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        )
                    }

                    ChannelListingPane(
                        channels = filteredChannels,
                        activeChannelKey = activeChannel?.streamKey,
                        epgMap = epgMap,
                        nowMillis = nowMillis,
                        onSelectChannel = { ch -> activeChannelKey = ch.streamKey },
                        onDirectWatch = { ch -> onPlayFullscreen(ch, miniPlayerEngine) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }

            // Docked Bottom Live TV Channel Info & Control Bar
            LiveTvBottomControlDock(
                activeChannel = activeChannel,
                epgList = activeChannel?.let { epgMap[it.tvgId] }.orEmpty(),
                nowMillis = nowMillis,
                onPrevChannel = {
                    if (filteredChannels.isNotEmpty() && activeChannel != null) {
                        val idx = filteredChannels.indexOfFirst { it.streamKey == activeChannel.streamKey }
                        val prevIdx = if (idx <= 0) filteredChannels.lastIndex else idx - 1
                        activeChannelKey = filteredChannels[prevIdx].streamKey
                    }
                },
                onNextChannel = {
                    if (filteredChannels.isNotEmpty() && activeChannel != null) {
                        val idx = filteredChannels.indexOfFirst { it.streamKey == activeChannel.streamKey }
                        val nextIdx = (idx + 1) % filteredChannels.size
                        activeChannelKey = filteredChannels[nextIdx].streamKey
                    }
                },
                onToggleFavorite = { activeChannel?.let(onToggleFavorite) },
                onOpenEpgGuide = onOpenEpgGrid,
                onFullscreen = { activeChannel?.let { onPlayFullscreen(it, miniPlayerEngine) } }
            )
        }
    }
}

@Composable
private fun CategorySidebarPane(
    categories: List<CategoryEntity>,
    allCount: Int,
    favCount: Int,
    countsByCat: Map<String, Int>,
    selectedCategoryId: String,
    parentalEnabled: Boolean,
    onSelectCategory: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        LazyColumn(
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                SidebarCategoryItem(
                    label = "All Channels",
                    count = allCount,
                    selected = selectedCategoryId == "ALL",
                    locked = false,
                    onClick = { onSelectCategory("ALL", false) }
                )
            }
            item {
                SidebarCategoryItem(
                    label = "★ Favorites",
                    count = favCount,
                    selected = selectedCategoryId == "FAV",
                    locked = false,
                    onClick = { onSelectCategory("FAV", false) }
                )
            }
            items(categories, key = { it.id }) { cat ->
                SidebarCategoryItem(
                    label = cat.name,
                    count = countsByCat[cat.categoryId] ?: 0,
                    selected = selectedCategoryId == cat.categoryId,
                    locked = cat.isAdultLocked && parentalEnabled,
                    onClick = { onSelectCategory(cat.categoryId, cat.isAdultLocked) }
                )
            }
        }
    }
}

@Composable
private fun SidebarCategoryItem(
    label: String,
    count: Int,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusableCard(shape = shape, selected = selected, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (locked) {
                    Icon(Icons.Default.Lock, contentDescription = "Locked", modifier = Modifier.size(14.dp), tint = LiveCrimson)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HorizontalCategoryStrip(
    categories: List<CategoryEntity>,
    selectedCategoryId: String,
    parentalEnabled: Boolean,
    onSelectCategory: (String, Boolean) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedCategoryId == "ALL",
                onClick = { onSelectCategory("ALL", false) },
                label = { Text("All") }
            )
        }
        item {
            FilterChip(
                selected = selectedCategoryId == "FAV",
                onClick = { onSelectCategory("FAV", false) },
                label = { Text("Favorites") }
            )
        }
        items(categories, key = { it.id }) { cat ->
            FilterChip(
                selected = selectedCategoryId == cat.categoryId,
                onClick = { onSelectCategory(cat.categoryId, cat.isAdultLocked) },
                leadingIcon = if (cat.isAdultLocked && parentalEnabled) {
                    { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp)) }
                } else null,
                label = { Text(cat.name, maxLines = 1) }
            )
        }
    }
}

@Composable
private fun ChannelListingPane(
    channels: List<StreamEntity>,
    activeChannelKey: String?,
    epgMap: Map<String, List<EpgProgramEntity>>,
    nowMillis: Long,
    onSelectChannel: (StreamEntity) -> Unit,
    onDirectWatch: (StreamEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(channels, key = { it.streamKey }) { ch ->
            val selected = ch.streamKey == activeChannelKey
            val programs = epgMap[ch.tvgId].orEmpty()
            val nowProg = programs.firstOrNull { nowMillis in it.startTimeMillis..it.endTimeMillis }
            val nextProg = programs.firstOrNull { it.startTimeMillis > (nowProg?.startTimeMillis ?: nowMillis) }
            val progress = if (nowProg != null && nowProg.endTimeMillis > nowProg.startTimeMillis) {
                ((nowMillis - nowProg.startTimeMillis).toFloat() / (nowProg.endTimeMillis - nowProg.startTimeMillis).toFloat())
                    .coerceIn(0.03f, 0.98f)
            } else 0.35f

            val shape = RoundedCornerShape(12.dp)
            Surface(
                color = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                shape = shape,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        shape = shape
                    )
                    .tvFocusableCard(shape = shape, selected = selected, onClick = { onSelectChannel(ch) })
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = String.format("%03d", ch.channelNumber),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    StylizedMediaArtwork(
                        imageUrl = ch.logoUrl,
                        title = ch.name,
                        isChannelLogo = true,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = ch.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (ch.catchupDays > 0) {
                                TelemetryBadgePill(
                                    text = "${ch.catchupDays}d Replay",
                                    containerColor = ElectricCyan.copy(alpha = 0.16f),
                                    contentColor = ElectricCyan
                                )
                            }
                        }

                        Text(
                            text = if (nowProg != null) {
                                "NOW (${formatTimeShort(nowProg.startTimeMillis)}): ${nowProg.title}"
                            } else {
                                "NOW: Live Broadcast Stream"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = if (nextProg != null) {
                                "NEXT (${formatTimeShort(nextProg.startTimeMillis)}): ${nextProg.title}"
                            } else {
                                "NEXT: Scheduled Programming"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Button(
                        onClick = { onDirectWatch(ch) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("watch_channel_${ch.providerStreamId}")
                    ) {
                        Text("Watch", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerAndShortEpgPane(
    channel: StreamEntity?,
    shortEpg: List<EpgProgramEntity>,
    nowMillis: Long,
    engine: PlayerEngine,
    settings: PlayerSettings,
    isMuted: Boolean,
    isPlaying: Boolean,
    handle: UnifiedPlayerHandle,
    onCycleEngine: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleMute: () -> Unit,
    onExpandFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        if (channel == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a channel to preview", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MiniPlayerBoxWithControls(
                    channel = channel,
                    engine = engine,
                    settings = settings,
                    isMuted = isMuted,
                    isPlaying = isPlaying,
                    handle = handle,
                    onCycleEngine = onCycleEngine,
                    onTogglePlay = onTogglePlay,
                    onToggleMute = onToggleMute,
                    onExpand = onExpandFullscreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                )

                Text(
                    text = "Upcoming Short EPG • ${channel.name}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val upcoming = shortEpg.filter { it.endTimeMillis > nowMillis }.take(6)
                    items(upcoming) { prog ->
                        val isNow = nowMillis in prog.startTimeMillis..prog.endTimeMillis
                        Surface(
                            color = if (isNow) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "${formatTimeShort(prog.startTimeMillis)} - ${formatTimeShort(prog.endTimeMillis)}${if (isNow) " • LIVE NOW" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isNow) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = prog.title,
                                    style = MaterialTheme.typography.titleSmall,
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
}

@Composable
private fun MiniPlayerBoxWithControls(
    channel: StreamEntity,
    engine: PlayerEngine,
    settings: PlayerSettings,
    isMuted: Boolean,
    isPlaying: Boolean,
    handle: UnifiedPlayerHandle,
    onCycleEngine: () -> Unit,
    onTogglePlay: () -> Unit,
    onToggleMute: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable { onExpand() }
    ) {
        UnifiedVideoSurface(
            streamUrl = channel.streamUrl,
            engine = engine,
            decoderMode = settings.decoderMode,
            aspectRatioMode = AspectRatioMode.FIT,
            bufferSeconds = settings.bufferSeconds,
            userAgent = settings.userAgent,
            isMuted = isMuted,
            handle = handle,
            onEngineFallback = { onCycleEngine() },
            modifier = Modifier.fillMaxSize()
        )

        // Compact Mini-Player Bottom Control Strip
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onTogglePlay, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play or Pause",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onToggleMute, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Mute or Unmute",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                TelemetryBadgePill(
                    text = engine.badge,
                    containerColor = ElectricCyan.copy(alpha = 0.22f),
                    contentColor = ElectricCyan,
                    modifier = Modifier.clickable { onCycleEngine() }
                )
            }
            FilledTonalButton(
                onClick = onExpand,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Icon(Icons.Default.Fullscreen, contentDescription = "Expand", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Expand", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LiveTvBottomControlDock(
    activeChannel: StreamEntity?,
    epgList: List<EpgProgramEntity>,
    nowMillis: Long,
    onPrevChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenEpgGuide: () -> Unit,
    onFullscreen: () -> Unit
) {
    if (activeChannel == null) return
    val nowProg = epgList.firstOrNull { nowMillis in it.startTimeMillis..it.endTimeMillis }
    val nextProg = epgList.firstOrNull { it.startTimeMillis > (nowProg?.startTimeMillis ?: nowMillis) }

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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StylizedMediaArtwork(
                    imageUrl = activeChannel.logoUrl,
                    title = activeChannel.name,
                    isChannelLogo = true,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CH ${activeChannel.channelNumber} • ${activeChannel.name}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "NOW: ${nowProg?.title ?: "Live Stream"}  •  NEXT: ${nextProg?.title ?: "Upcoming"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (activeChannel.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (activeChannel.isFavorite) LiveCrimson else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPrevChannel,
                    modifier = Modifier.weight(0.8f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("CH -", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onNextChannel,
                    modifier = Modifier.weight(0.8f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text("CH +", style = MaterialTheme.typography.labelSmall)
                    Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                FilledTonalButton(
                    onClick = onOpenEpgGuide,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.CalendarViewDay, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("EPG Guide", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                Button(
                    onClick = onFullscreen,
                    modifier = Modifier.weight(1.1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Fullscreen", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

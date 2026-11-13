package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CalendarViewDay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.EpgProgramEntity
import com.example.data.local.PlaybackProgressEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.StreamEntity
import com.example.data.local.StreamType
import com.example.data.preferences.PlayerEngine
import com.example.data.preferences.PlayerSettings
import com.example.player.AbiAndEngineReport
import com.example.ui.components.RatingBadge
import com.example.ui.components.StylizedMediaArtwork
import com.example.ui.components.TelemetryBadgePill
import com.example.ui.components.formatExpirationDate
import com.example.ui.components.tvFocusableCard
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SignalViolet
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.QuadCounts
import com.example.ui.viewmodel.SyncBannerState

@Composable
fun DashboardScreen(
    activePlaylist: PlaylistEntity?,
    counts: QuadCounts,
    liveStreams: List<StreamEntity>,
    vodStreams: List<StreamEntity>,
    seriesStreams: List<StreamEntity>,
    favorites: List<StreamEntity>,
    continueWatching: List<PlaybackProgressEntity>,
    epgMap: Map<String, List<EpgProgramEntity>>,
    settings: PlayerSettings,
    syncState: SyncBannerState,
    abiReport: AbiAndEngineReport,
    onNavigate: (AppScreen) -> Unit,
    onPlayStream: (StreamEntity, PlayerEngine?) -> Unit,
    onOpenDetail: (StreamEntity) -> Unit,
    onResumeProgress: (PlaybackProgressEntity) -> Unit,
    onOpenSearch: () -> Unit,
    onSyncNow: () -> Unit,
    onCycleEngine: () -> Unit
) {
    val nowMillis = remember(epgMap) { System.currentTimeMillis() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWide = maxWidth >= 700.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Broadcast Header
            DashboardTopBar(
                activePlaylist = activePlaylist,
                settings = settings,
                isWide = isWide,
                onOpenSearch = onOpenSearch,
                onOpenProfiles = { onNavigate(AppScreen.Profiles) },
                onOpenSettings = { onNavigate(AppScreen.Settings) },
                onCycleEngine = onCycleEngine
            )

            if (syncState.isSyncing || syncState.errorMessage != null) {
                SyncBannerStrip(syncState = syncState)
            }

            // Scrollable Smarters Dashboard Body
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Hero Atmospheric Broadcast Card + Primary Navigation Tiles
                item {
                    HeroNavigationDeck(
                        counts = counts,
                        isWide = isWide,
                        defaultEngine = settings.defaultEngine,
                        abiPrimary = abiReport.primaryAbi,
                        onNavigate = onNavigate
                    )
                }

                // Continue Watching Carousel
                if (continueWatching.isNotEmpty()) {
                    item {
                        SectionHeaderRow(
                            icon = Icons.Default.History,
                            title = "Continue Watching",
                            subtitle = "${continueWatching.size} active sessions"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(continueWatching, key = { it.streamKey }) { progress ->
                                ContinueWatchingCard(
                                    item = progress,
                                    onClick = { onResumeProgress(progress) }
                                )
                            }
                        }
                    }
                }

                // Favorites Quick Carousel
                if (favorites.isNotEmpty()) {
                    item {
                        SectionHeaderRow(
                            icon = Icons.Default.Favorite,
                            title = "Favorites & Pinned",
                            subtitle = "${favorites.size} saved items"
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(favorites.take(12), key = { it.streamKey }) { fav ->
                                QuickStreamTile(
                                    stream = fav,
                                    nowProgramTitle = epgMap[fav.tvgId]?.firstOrNull {
                                        nowMillis in it.startTimeMillis..it.endTimeMillis
                                    }?.title,
                                    onClick = {
                                        if (fav.streamType == StreamType.LIVE) onPlayStream(fav, null)
                                        else onOpenDetail(fav)
                                    },
                                    onPlayVlc = { onPlayStream(fav, PlayerEngine.VLC_EMBEDDED) },
                                    onPlayMpv = { onPlayStream(fav, PlayerEngine.MPV_EMBEDDED) }
                                )
                            }
                        }
                    }
                }

                // Direct Live TV Channel Listings Carousel
                item {
                    SectionHeaderRow(
                        icon = Icons.Default.LiveTv,
                        title = "Live TV Channel Listings",
                        subtitle = "${counts.liveCount} channels • ExoPlayer / VLC / MPV",
                        actionLabel = "Open 3-Pane Live TV",
                        onAction = { onNavigate(AppScreen.LiveTv(false)) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(liveStreams.filter { !it.isAdult }.take(12), key = { it.streamKey }) { ch ->
                            val nowProg = epgMap[ch.tvgId]?.firstOrNull {
                                nowMillis in it.startTimeMillis..it.endTimeMillis
                            }
                            QuickStreamTile(
                                stream = ch,
                                nowProgramTitle = nowProg?.title ?: "Live Broadcast Master",
                                onClick = { onPlayStream(ch, null) },
                                onPlayVlc = { onPlayStream(ch, PlayerEngine.VLC_EMBEDDED) },
                                onPlayMpv = { onPlayStream(ch, PlayerEngine.MPV_EMBEDDED) }
                            )
                        }
                    }
                }

                // Direct Movies (VOD) Listings Carousel
                item {
                    SectionHeaderRow(
                        icon = Icons.Default.Movie,
                        title = "Movies (VOD) Listings",
                        subtitle = "${counts.vodCount} feature films",
                        actionLabel = "Browse All Movies",
                        onAction = { onNavigate(AppScreen.Movies) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(vodStreams.take(10), key = { it.streamKey }) { movie ->
                            DashboardPosterCard(
                                stream = movie,
                                onClick = { onOpenDetail(movie) },
                                onQuickPlay = { onPlayStream(movie, null) }
                            )
                        }
                    }
                }

                // Direct TV Series Listings Carousel
                item {
                    SectionHeaderRow(
                        icon = Icons.Default.VideoLibrary,
                        title = "TV Series Listings",
                        subtitle = "${counts.seriesCount} multi-season series",
                        actionLabel = "Browse All Series",
                        onAction = { onNavigate(AppScreen.Series) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(seriesStreams.take(8), key = { it.streamKey }) { series ->
                            DashboardPosterCard(
                                stream = series,
                                onClick = { onOpenDetail(series) },
                                onQuickPlay = { onOpenDetail(series) }
                            )
                        }
                    }
                }
            }

            // Docked Dashboard Bottom Info & Control Bar
            DashboardBottomInfoBar(
                activePlaylist = activePlaylist,
                counts = counts,
                settings = settings,
                abiPrimary = abiReport.primaryAbi,
                isWide = isWide,
                onOpenSearch = onOpenSearch,
                onSyncEpg = onSyncNow,
                onCycleEngine = onCycleEngine,
                onOpenSettings = { onNavigate(AppScreen.Settings) }
            )
        }
    }
}

@Composable
private fun DashboardTopBar(
    activePlaylist: PlaylistEntity?,
    settings: PlayerSettings,
    isWide: Boolean,
    onOpenSearch: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onCycleEngine: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(listOf(SignalViolet, ElectricCyan))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF070B17))
                }
                Column {
                    Text(
                        text = "NovaStream IPTV",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1
                    )
                    Text(
                        text = activePlaylist?.name ?: "BYOP Multi-Engine Player",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick Tri-Engine Cycle Pill (EXO / VLC / MPV)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .testTag("dashboard_engine_pill")
                        .tvFocusableCard(RoundedCornerShape(10.dp), onClick = onCycleEngine)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = if (isWide) "Engine: ${settings.defaultEngine.shortLabel}" else settings.defaultEngine.badge,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .testTag("dashboard_search_button")
                        .tvFocusableCard(RoundedCornerShape(10.dp), onClick = onOpenSearch)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
                        if (isWide) Text("FTS Search", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .testTag("dashboard_profiles_button")
                        .tvFocusableCard(RoundedCornerShape(10.dp), onClick = onOpenProfiles)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Profiles", modifier = Modifier.size(18.dp))
                        if (isWide) Text("Playlists", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .testTag("dashboard_settings_button")
                        .tvFocusableCard(RoundedCornerShape(10.dp), onClick = onOpenSettings)
                ) {
                    Box(modifier = Modifier.padding(8.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroNavigationDeck(
    counts: QuadCounts,
    isWide: Boolean,
    defaultEngine: PlayerEngine,
    abiPrimary: String,
    onNavigate: (AppScreen) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Atmospheric Broadcast Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isWide) 138.dp else 124.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_hero_broadcast_1794446863372),
                contentDescription = "Broadcast Control Studio",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xEB070B17),
                                Color(0xBF0B132B),
                                Color(0x66070B17)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TelemetryBadgePill(
                        text = "TRI-ENGINE: EXO • LIBVLC • LIBMPV",
                        containerColor = ElectricCyan.copy(alpha = 0.2f),
                        contentColor = ElectricCyan
                    )
                    TelemetryBadgePill(
                        text = "SPLIT ABI: $abiPrimary",
                        containerColor = SignalViolet.copy(alpha = 0.28f),
                        contentColor = Color(0xFFE5DEFF)
                    )
                }
                Column {
                    Text(
                        text = "Multi-Screen Broadcast Control Deck",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White
                    )
                    Text(
                        text = "Xtream Codes • M3U/M3U8 • Stalker MAC • 2D XMLTV Guide • Active Default: ${defaultEngine.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD0DCFA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Responsive Smarters Primary Navigation Tiles (5-column wide vs 2-column portrait)
        val navItems = listOf(
            NavTileSpec("Live TV", "${counts.liveCount} Channels", Icons.Default.LiveTv, listOf(Color(0xFF0077B6), Color(0xFF00B4D8)), "tile_live_tv") {
                onNavigate(AppScreen.LiveTv(false))
            },
            NavTileSpec("Movies (VOD)", "${counts.vodCount} Films", Icons.Default.Movie, listOf(Color(0xFF5A189A), Color(0xFF9D4EDD)), "tile_movies") {
                onNavigate(AppScreen.Movies)
            },
            NavTileSpec("TV Series", "${counts.seriesCount} Series", Icons.Default.VideoLibrary, listOf(Color(0xFFC9184A), Color(0xFFFF4D6D)), "tile_series") {
                onNavigate(AppScreen.Series)
            },
            NavTileSpec("2D EPG & Replay", "${counts.epgCount} Slots", Icons.Default.CalendarViewDay, listOf(Color(0xFF007F5F), Color(0xFF2B9348)), "tile_epg") {
                onNavigate(AppScreen.EpgGrid)
            },
            NavTileSpec("Multi-Screen", "4-Way 2x2 Split", Icons.Default.GridView, listOf(Color(0xFFD97706), Color(0xFFF59E0B)), "tile_multiscreen") {
                onNavigate(AppScreen.MultiScreen)
            }
        )

        if (isWide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                navItems.forEach { spec ->
                    PrimarySmartersTile(spec = spec, modifier = Modifier.weight(1f))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PrimarySmartersTile(spec = navItems[0], modifier = Modifier.weight(1f))
                    PrimarySmartersTile(spec = navItems[1], modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PrimarySmartersTile(spec = navItems[2], modifier = Modifier.weight(1f))
                    PrimarySmartersTile(spec = navItems[3], modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PrimarySmartersTile(spec = navItems[4], modifier = Modifier.weight(1f))
                    PrimarySmartersTile(
                        spec = NavTileSpec(
                            "Catch-Up Archive",
                            "Timeshift TV",
                            Icons.Default.History,
                            listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6)),
                            "tile_catchup"
                        ) { onNavigate(AppScreen.LiveTv(catchUpOnly = true)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private data class NavTileSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val colors: List<Color>,
    val tag: String,
    val onClick: () -> Unit
)

@Composable
private fun PrimarySmartersTile(spec: NavTileSpec, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(92.dp)
            .testTag(spec.tag)
            .tvFocusableCard(shape = shape, onClick = spec.onClick)
            .background(Brush.linearGradient(spec.colors))
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = spec.title,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
            Column {
                Text(
                    text = spec.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = spec.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: PlaybackProgressEntity,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val progress = if (item.durationMs > 0) {
        (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0.04f, 0.98f)
    } else 0.35f

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = shape,
        modifier = Modifier
            .width(230.dp)
            .tvFocusableCard(shape = shape, onClick = onClick)
    ) {
        Column {
            Box(modifier = Modifier.height(110.dp).fillMaxWidth()) {
                StylizedMediaArtwork(
                    imageUrl = item.posterUrl,
                    title = item.title,
                    modifier = Modifier.fillMaxSize()
                )
                TelemetryBadgePill(
                    text = item.lastEngine.take(3),
                    containerColor = Color.Black.copy(alpha = 0.75f),
                    contentColor = ElectricCyan,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle.ifBlank { "Tap to resume" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun QuickStreamTile(
    stream: StreamEntity,
    nowProgramTitle: String?,
    onClick: () -> Unit,
    onPlayVlc: () -> Unit,
    onPlayMpv: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        tonalElevation = 2.dp,
        modifier = Modifier
            .width(246.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), shape)
            .tvFocusableCard(shape = shape, onClick = onClick)
    ) {
        Column {
            Box(modifier = Modifier.height(114.dp).fillMaxWidth()) {
                StylizedMediaArtwork(
                    imageUrl = stream.backdropUrl.ifBlank { stream.logoUrl },
                    title = stream.name,
                    isChannelLogo = stream.streamType == StreamType.LIVE,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC070B17)))
                        )
                )
                if (stream.streamType == StreamType.LIVE) {
                    TelemetryBadgePill(
                        text = "LIVE • CH ${stream.channelNumber}",
                        containerColor = LiveCrimson,
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    )
                }
                TelemetryBadgePill(
                    text = stream.qualityBadge,
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    contentColor = ElectricCyan,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stream.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = nowProgramTitle ?: stream.categoryName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Watch", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onPlayVlc,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("VLC", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onPlayMpv,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("MPV", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardPosterCard(
    stream: StreamEntity,
    onClick: () -> Unit,
    onQuickPlay: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        modifier = Modifier
            .width(152.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape)
            .tvFocusableCard(shape = shape, onClick = onClick)
    ) {
        Column {
            Box(modifier = Modifier.height(190.dp).fillMaxWidth()) {
                StylizedMediaArtwork(
                    imageUrl = stream.logoUrl,
                    title = stream.name,
                    modifier = Modifier.fillMaxSize()
                )
                RatingBadge(
                    rating = stream.rating,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                )
                TelemetryBadgePill(
                    text = stream.qualityBadge.substringBefore(" "),
                    containerColor = Color.Black.copy(alpha = 0.75f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                )
            }
            Column(modifier = Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stream.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${stream.releaseYear.ifBlank { "2024" }} • ${stream.categoryName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FilledTonalButton(
                    onClick = onQuickPlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        if (stream.streamType == StreamType.SERIES) "Episodes" else "Play Now",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncBannerStrip(syncState: SyncBannerState) {
    Surface(
        color = if (syncState.errorMessage != null) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
        } else {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (syncState.isSyncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            }
            Text(
                text = syncState.errorMessage ?: syncState.statusMessage,
                style = MaterialTheme.typography.labelMedium,
                color = if (syncState.errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun DashboardBottomInfoBar(
    activePlaylist: PlaylistEntity?,
    counts: QuadCounts,
    settings: PlayerSettings,
    abiPrimary: String,
    isWide: Boolean,
    onOpenSearch: () -> Unit,
    onSyncEpg: () -> Unit,
    onCycleEngine: () -> Unit,
    onOpenSettings: () -> Unit
) {
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
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(EmeraldOnline)
                    )
                    Text(
                        text = buildString {
                            append(activePlaylist?.accountStatus ?: "Active")
                            append(" • Exp: ${formatExpirationDate(activePlaylist?.expirationTimestampSec)}")
                            if (isWide) {
                                append(" • Cons: ${activePlaylist?.activeConnections ?: 1}/${activePlaylist?.maxConnections ?: 4}")
                                append(" • ${counts.liveCount} Live / ${counts.vodCount} VOD / ${counts.seriesCount} Series")
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TelemetryBadgePill(
                    text = "$abiPrimary • ${settings.defaultEngine.badge}",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onOpenSearch,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Search", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onSyncEpg,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Sync EPG", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = onCycleEngine,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = settings.defaultEngine.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
                FilledTonalButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Themes & Tools", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

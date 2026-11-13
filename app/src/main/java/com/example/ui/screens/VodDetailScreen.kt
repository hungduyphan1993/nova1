package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.EpisodeEntity
import com.example.data.local.StreamEntity
import com.example.data.local.StreamType
import com.example.data.preferences.ExternalPlayerTarget
import com.example.data.preferences.PlayerEngine
import com.example.data.preferences.PlayerSettings
import com.example.player.ExternalPlayerAndAbiManager
import com.example.ui.components.RatingBadge
import com.example.ui.components.StylizedMediaArtwork
import com.example.ui.components.TelemetryBadgePill
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.LiveCrimson

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VodDetailScreen(
    stream: StreamEntity?,
    episodes: List<EpisodeEntity>,
    settings: PlayerSettings,
    onBack: () -> Unit,
    onEnsureEpisodesLoaded: (StreamEntity) -> Unit,
    onPlayMovie: (StreamEntity, PlayerEngine?) -> Unit,
    onPlayEpisode: (StreamEntity, EpisodeEntity, PlayerEngine?) -> Unit,
    onToggleFavorite: (StreamEntity) -> Unit
) {
    val context = LocalContext.current

    if (stream == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text("Loading title details…", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    val isSeries = stream.streamType == StreamType.SERIES
    LaunchedEffect(stream.streamKey) {
        if (isSeries) {
            onEnsureEpisodesLoaded(stream)
        }
    }

    val seasons = remember(episodes) {
        episodes.map { it.seasonNumber }.distinct().sorted().ifEmpty { listOf(1) }
    }
    var selectedSeason by rememberSaveable(stream.streamKey) { mutableIntStateOf(1) }
    val seasonEpisodes = remember(episodes, selectedSeason) {
        episodes.filter { it.seasonNumber == selectedSeason }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Compact Top Navigation Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text(
                            text = stream.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${stream.streamType.name} • ${stream.categoryName} • ${stream.qualityBadge}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                IconButton(onClick = { onToggleFavorite(stream) }) {
                    Icon(
                        imageVector = if (stream.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (stream.isFavorite) LiveCrimson else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ABOVE THE FOLD FOR SERIES: Season Tabs & Episode Listings immediately visible without scrolling!
            if (isSeries) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Seasons & Episodes (${episodes.size} Total)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TelemetryBadgePill(
                                text = "Default: ${settings.defaultEngine.shortLabel}",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(seasons) { seasonNum ->
                                FilterChip(
                                    selected = selectedSeason == seasonNum,
                                    onClick = { selectedSeason = seasonNum },
                                    label = { Text("Season $seasonNum") },
                                    modifier = Modifier.testTag("season_tab_$seasonNum")
                                )
                            }
                        }
                    }
                }

                items(seasonEpisodes, key = { it.id }) { ep ->
                    EpisodeListingCard(
                        series = stream,
                        episode = ep,
                        userAgent = settings.userAgent,
                        onPlayDefault = { onPlayEpisode(stream, ep, null) },
                        onPlayVlc = { onPlayEpisode(stream, ep, PlayerEngine.VLC_EMBEDDED) },
                        onPlayMpv = { onPlayEpisode(stream, ep, PlayerEngine.MPV_EMBEDDED) }
                    )
                }
            }

            // Wide Cinema Hero Backdrop + Multi-Engine & External VLC/MPV Action Deck
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    if (stream.backdropUrl.isNotBlank() || stream.logoUrl.isNotBlank()) {
                        StylizedMediaArtwork(
                            imageUrl = stream.backdropUrl.ifBlank { stream.logoUrl },
                            title = stream.name,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.img_cinema_backdrop_1794446865410),
                            contentDescription = stream.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0x55070B17),
                                        Color(0xD9070B17),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RatingBadge(rating = stream.rating)
                            TelemetryBadgePill(text = stream.releaseYear.ifBlank { "2024" })
                            TelemetryBadgePill(
                                text = stream.qualityBadge,
                                containerColor = ElectricCyan.copy(alpha = 0.2f),
                                contentColor = ElectricCyan
                            )
                        }
                        Text(
                            text = stream.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                    }
                }
            }

            // Primary Playback Engine Buttons + External VLC / MPV / MX Handoff
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isSeries) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onPlayMovie(stream, PlayerEngine.EXOPLAYER) },
                                modifier = Modifier.testTag("play_movie_exo")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Play (ExoPlayer)")
                            }
                            FilledTonalButton(
                                onClick = { onPlayMovie(stream, PlayerEngine.VLC_EMBEDDED) },
                                modifier = Modifier.testTag("play_movie_vlc")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Play in LibVLC")
                            }
                            FilledTonalButton(
                                onClick = { onPlayMovie(stream, PlayerEngine.MPV_EMBEDDED) },
                                modifier = Modifier.testTag("play_movie_mpv")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Play in MPV")
                            }
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                ExternalPlayerAndAbiManager.launchYoutubeTrailer(
                                    context, stream.youtubeTrailerId, stream.name
                                )
                            }
                        ) {
                            Icon(Icons.Default.SmartDisplay, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("YouTube Trailer")
                        }
                        OutlinedButton(
                            onClick = {
                                val targetUrl = if (isSeries) seasonEpisodes.firstOrNull()?.streamUrl ?: stream.streamUrl else stream.streamUrl
                                ExternalPlayerAndAbiManager.launchExternalPlayer(
                                    context, ExternalPlayerTarget.VLC_EXTERNAL, targetUrl, stream.name, settings.userAgent
                                )
                            }
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("External VLC")
                        }
                        OutlinedButton(
                            onClick = {
                                val targetUrl = if (isSeries) seasonEpisodes.firstOrNull()?.streamUrl ?: stream.streamUrl else stream.streamUrl
                                ExternalPlayerAndAbiManager.launchExternalPlayer(
                                    context, ExternalPlayerTarget.MPV_EXTERNAL, targetUrl, stream.name, settings.userAgent
                                )
                            }
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("External MPV")
                        }
                        OutlinedButton(
                            onClick = {
                                val targetUrl = if (isSeries) seasonEpisodes.firstOrNull()?.streamUrl ?: stream.streamUrl else stream.streamUrl
                                ExternalPlayerAndAbiManager.launchExternalPlayer(
                                    context, ExternalPlayerTarget.MX_PLAYER, targetUrl, stream.name, settings.userAgent
                                )
                            }
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("MX Player")
                        }
                    }

                    // Synopsis, Director & Cast Credits
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Synopsis & Credits", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = stream.synopsis.ifBlank { "High-definition cinema presentation from ${stream.categoryName}." },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (stream.director.isNotBlank()) {
                                Text(
                                    text = "Director: ${stream.director}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (stream.cast.isNotBlank()) {
                                Text(
                                    text = "Cast: ${stream.cast}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun EpisodeListingCard(
    series: StreamEntity,
    episode: EpisodeEntity,
    userAgent: String,
    onPlayDefault: () -> Unit,
    onPlayVlc: () -> Unit,
    onPlayMpv: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StylizedMediaArtwork(
                imageUrl = episode.thumbnailUrl.ifBlank { series.logoUrl },
                title = episode.title,
                modifier = Modifier
                    .size(width = 96.dp, height = 62.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    TelemetryBadgePill(text = episode.durationLabel)
                }
                Text(
                    text = episode.synopsis.ifBlank { "Season ${episode.seasonNumber} • Episode ${episode.episodeNumber}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = onPlayDefault,
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Play", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onPlayVlc,
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("VLC", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onPlayMpv,
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("MPV", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = {
                            ExternalPlayerAndAbiManager.launchExternalPlayer(
                                context, ExternalPlayerTarget.VLC_EXTERNAL, episode.streamUrl, episode.title, userAgent
                            )
                        },
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("Ext", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CategoryEntity
import com.example.data.local.StreamEntity
import com.example.data.local.StreamType
import com.example.data.preferences.PlayerEngine
import com.example.player.ExternalPlayerAndAbiManager
import com.example.ui.components.RatingBadge
import com.example.ui.components.StylizedMediaArtwork
import com.example.ui.components.TelemetryBadgePill
import com.example.ui.components.tvFocusableCard
import com.example.ui.theme.LiveCrimson

private enum class CatalogSortMode(val label: String) {
    RECENT("Recently Added"),
    RATING("Top Rating"),
    ALPHA("A–Z"),
    YEAR("Release Year")
}

@Composable
fun VodSeriesScreen(
    isSeries: Boolean,
    categories: List<CategoryEntity>,
    items: List<StreamEntity>,
    defaultEngine: PlayerEngine,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onPlayStream: (StreamEntity, PlayerEngine?) -> Unit,
    onOpenDetail: (StreamEntity) -> Unit,
    onToggleFavorite: (StreamEntity) -> Unit
) {
    val context = LocalContext.current
    var selectedCategory by rememberSaveable { mutableStateOf("ALL") }
    var isGridMode by rememberSaveable { mutableStateOf(true) }
    var sortMode by rememberSaveable { mutableStateOf(CatalogSortMode.RECENT) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    val filteredAndSorted = remember(items, selectedCategory, sortMode) {
        val filtered = when (selectedCategory) {
            "ALL" -> items
            "FAV" -> items.filter { it.isFavorite }
            "TOP" -> items.filter { it.rating >= 8.8 }
            else -> items.filter { it.categoryId == selectedCategory }
        }
        when (sortMode) {
            CatalogSortMode.RECENT -> filtered.sortedByDescending { it.addedTimestamp }
            CatalogSortMode.RATING -> filtered.sortedByDescending { it.rating }
            CatalogSortMode.ALPHA -> filtered.sortedBy { it.name.lowercase() }
            CatalogSortMode.YEAR -> filtered.sortedByDescending { it.releaseYear }
        }
    }

    var selectedItemKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedItem = remember(filteredAndSorted, selectedItemKey) {
        filteredAndSorted.firstOrNull { it.streamKey == selectedItemKey } ?: filteredAndSorted.firstOrNull()
    }

    val countsByCat = remember(items) { items.groupingBy { it.categoryId }.eachCount() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWideTwoPane = maxWidth >= 720.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Compact Single-Line Top Bar (gives ~80% vertical height to listings)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(38.dp).testTag("vod_back_button")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            text = if (isSeries) "TV Series (${filteredAndSorted.size})" else "Movies (${filteredAndSorted.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(onClick = onOpenSearch, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(19.dp))
                        }

                        // 1-Tap List / Grid View Toggle
                        FilterChip(
                            selected = !isGridMode,
                            onClick = { isGridMode = !isGridMode },
                            leadingIcon = {
                                Icon(
                                    if (isGridMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            label = { Text(if (isGridMode) "List" else "Grid", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(32.dp).testTag("vod_view_toggle")
                        )

                        Box {
                            FilterChip(
                                selected = false,
                                onClick = { sortMenuOpen = true },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(15.dp))
                                },
                                label = { Text(sortMode.label.substringBefore(" "), style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(32.dp)
                            )
                            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                                CatalogSortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label) },
                                        onClick = {
                                            sortMode = mode
                                            sortMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isWideTwoPane) {
                // Smarters Pro 2-Pane Layout: Left Category Sidebar + Right Poster Grid / Row List
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .width(215.dp)
                            .fillMaxHeight()
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(10.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            item {
                                VodSidebarRow(
                                    title = if (isSeries) "All Series" else "All Movies",
                                    count = items.size,
                                    selected = selectedCategory == "ALL",
                                    onClick = { selectedCategory = "ALL" }
                                )
                            }
                            item {
                                VodSidebarRow(
                                    title = "★ Favorites",
                                    count = items.count { it.isFavorite },
                                    selected = selectedCategory == "FAV",
                                    onClick = { selectedCategory = "FAV" }
                                )
                            }
                            item {
                                VodSidebarRow(
                                    title = "Top Rated ★ 8.8+",
                                    count = items.count { it.rating >= 8.8 },
                                    selected = selectedCategory == "TOP",
                                    onClick = { selectedCategory = "TOP" }
                                )
                            }
                            items(categories, key = { it.id }) { cat ->
                                VodSidebarRow(
                                    title = cat.name,
                                    count = countsByCat[cat.categoryId] ?: 0,
                                    selected = selectedCategory == cat.categoryId,
                                    onClick = { selectedCategory = cat.categoryId }
                                )
                            }
                        }
                    }

                    VodListingsContent(
                        isSeries = isSeries,
                        isGridMode = isGridMode,
                        items = filteredAndSorted,
                        selectedKey = selectedItem?.streamKey,
                        gridColumns = GridCells.Adaptive(minSize = 165.dp),
                        onSelect = { selectedItemKey = it.streamKey },
                        onPrimaryAction = { item ->
                            if (isSeries) onOpenDetail(item) else onPlayStream(item, null)
                        },
                        onDetails = onOpenDetail,
                        onPlayVlc = { onPlayStream(it, PlayerEngine.VLC_EMBEDDED) },
                        onPlayMpv = { onPlayStream(it, PlayerEngine.MPV_EMBEDDED) },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            } else {
                // Portrait Compact Single-Line Category Strip + Full-Height Listings (~80% screen height)
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedCategory == "ALL",
                                onClick = { selectedCategory = "ALL" },
                                label = { Text("All (${items.size})", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(30.dp)
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedCategory == "FAV",
                                onClick = { selectedCategory = "FAV" },
                                label = { Text("Favorites (${items.count { it.isFavorite }})", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(30.dp)
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedCategory == "TOP",
                                onClick = { selectedCategory = "TOP" },
                                label = { Text("★ 8.8+ (${items.count { it.rating >= 8.8 }})", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(30.dp)
                            )
                        }
                        items(categories, key = { it.id }) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat.categoryId,
                                onClick = { selectedCategory = cat.categoryId },
                                label = { Text("${cat.name} (${countsByCat[cat.categoryId] ?: 0})", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }

                    VodListingsContent(
                        isSeries = isSeries,
                        isGridMode = isGridMode,
                        items = filteredAndSorted,
                        selectedKey = selectedItem?.streamKey,
                        gridColumns = GridCells.Fixed(2),
                        onSelect = { selectedItemKey = it.streamKey },
                        onPrimaryAction = { item ->
                            if (isSeries) onOpenDetail(item) else onPlayStream(item, null)
                        },
                        onDetails = onOpenDetail,
                        onPlayVlc = { onPlayStream(it, PlayerEngine.VLC_EMBEDDED) },
                        onPlayMpv = { onPlayStream(it, PlayerEngine.MPV_EMBEDDED) },
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }

            // Sleek Docked Bottom Movie/Series Info & Control Bar
            selectedItem?.let { item ->
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
                                imageUrl = item.logoUrl,
                                title = item.name,
                                modifier = Modifier
                                    .size(width = 42.dp, height = 54.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    RatingBadge(rating = item.rating)
                                    TelemetryBadgePill(text = item.releaseYear.ifBlank { "2024" })
                                }
                                Text(
                                    text = item.synopsis.ifBlank { "${item.categoryName} • ${item.qualityBadge} • Ready on ${defaultEngine.shortLabel}, VLC & MPV" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onToggleFavorite(item) }, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (item.isFavorite) LiveCrimson else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { if (isSeries) onOpenDetail(item) else onPlayStream(item, null) },
                                modifier = Modifier.weight(1.3f).height(34.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    if (isSeries) "Seasons & Play" else "Watch Movie",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                            OutlinedButton(
                                onClick = { onPlayStream(item, PlayerEngine.VLC_EMBEDDED) },
                                modifier = Modifier.weight(0.65f).height(34.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("VLC", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { onPlayStream(item, PlayerEngine.MPV_EMBEDDED) },
                                modifier = Modifier.weight(0.65f).height(34.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Text("MPV", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = { onOpenDetail(item) },
                                modifier = Modifier.weight(0.85f).height(34.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Info", style = MaterialTheme.typography.labelSmall)
                            }
                            FilledTonalButton(
                                onClick = {
                                    ExternalPlayerAndAbiManager.launchYoutubeTrailer(context, item.youtubeTrailerId, item.name)
                                },
                                modifier = Modifier.weight(0.9f).height(34.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.SmartDisplay, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("Trailer", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VodSidebarRow(
    title: String,
    count: Int,
    selected: Boolean,
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VodListingsContent(
    isSeries: Boolean,
    isGridMode: Boolean,
    items: List<StreamEntity>,
    selectedKey: String?,
    gridColumns: GridCells,
    onSelect: (StreamEntity) -> Unit,
    onPrimaryAction: (StreamEntity) -> Unit,
    onDetails: (StreamEntity) -> Unit,
    onPlayVlc: (StreamEntity) -> Unit,
    onPlayMpv: (StreamEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isGridMode) {
        LazyVerticalGrid(
            columns = gridColumns,
            modifier = modifier,
            contentPadding = PaddingValues(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.streamKey }) { item ->
                val selected = item.streamKey == selectedKey
                val shape = RoundedCornerShape(14.dp)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = shape,
                    modifier = Modifier
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            shape = shape
                        )
                        .tvFocusableCard(shape = shape, selected = selected, onClick = { onSelect(item) })
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(186.dp)
                                .clickable { onSelect(item) }
                        ) {
                            StylizedMediaArtwork(
                                imageUrl = item.logoUrl,
                                title = item.name,
                                modifier = Modifier.fillMaxSize()
                            )
                            RatingBadge(
                                rating = item.rating,
                                modifier = Modifier.align(Alignment.TopStart).padding(7.dp)
                            )
                            TelemetryBadgePill(
                                text = item.qualityBadge.substringBefore(" "),
                                containerColor = Color.Black.copy(alpha = 0.72f),
                                contentColor = Color.White,
                                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp)
                            )
                        }

                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.releaseYear.ifBlank { "2024" }} • ${item.categoryName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { onPrimaryAction(item) },
                                    modifier = Modifier.weight(1f).height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text(
                                        if (isSeries) "Episodes" else "Play",
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                                FilledTonalButton(
                                    onClick = { onDetails(item) },
                                    modifier = Modifier.weight(0.85f).height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Text("Details", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Detailed Row Listings View
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.streamKey }) { item ->
                val selected = item.streamKey == selectedKey
                val shape = RoundedCornerShape(12.dp)
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = shape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            shape = shape
                        )
                        .tvFocusableCard(shape = shape, selected = selected, onClick = { onSelect(item) })
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StylizedMediaArtwork(
                            imageUrl = item.logoUrl,
                            title = item.name,
                            modifier = Modifier
                                .size(width = 68.dp, height = 94.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                RatingBadge(rating = item.rating)
                            }
                            Text(
                                text = "${item.releaseYear.ifBlank { "2024" }} • ${item.categoryName} • ${item.qualityBadge}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = item.synopsis.ifBlank { "Playable in Media3 ExoPlayer, Embedded VLC, or Embedded MPV." },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { onPrimaryAction(item) },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Text(if (isSeries) "Episodes" else "Play", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { onPlayVlc(item) },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("VLC", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(
                                    onClick = { onPlayMpv(item) },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("MPV", style = MaterialTheme.typography.labelSmall)
                                }
                                FilledTonalButton(
                                    onClick = { onDetails(item) },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Text("Details", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

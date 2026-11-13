package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.local.StreamEntity
import com.example.data.local.StreamType
import com.example.data.preferences.ExternalPlayerTarget
import com.example.data.preferences.PlayerEngine
import com.example.player.ExternalPlayerAndAbiManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reliable Media Artwork loader with stylized cinema poster gradient fallback
 * so channel logos and movie/series cards never appear blank.
 */
@Composable
fun StylizedMediaArtwork(
    imageUrl: String,
    title: String,
    modifier: Modifier = Modifier,
    isChannelLogo: Boolean = false
) {
    val gradientColors = posterFallbackGradient(title)
    Box(
        modifier = modifier.background(Brush.linearGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = {
                    FallbackArtworkContent(title = title, isChannelLogo = isChannelLogo)
                }
            )
        } else {
            FallbackArtworkContent(title = title, isChannelLogo = isChannelLogo)
        }
    }
}

@Composable
private fun FallbackArtworkContent(title: String, isChannelLogo: Boolean) {
    Column(
        modifier = Modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isChannelLogo) Icons.Default.Tv else Icons.Default.Movie,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(if (isChannelLogo) 22.dp else 32.dp)
        )
        if (!isChannelLogo) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title.take(22),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TelemetryBadgePill(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            maxLines = 1
        )
    }
}

@Composable
fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    if (rating <= 0.0) return
    Surface(
        color = Color(0xCC1B1400),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.border(1.dp, Color(0xFFFFB300).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = String.format(Locale.US, "%.1f", rating),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFFD54F),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Reusable Tri-Engine + External VLC/MPV/MX Quick Selector Dropdown Menu
 * used throughout Live TV, Movies, Series, EPG, and Fullscreen Player.
 */
@Composable
fun EngineAndExternalLauncherButton(
    activeEngine: PlayerEngine,
    streamUrl: String,
    streamTitle: String,
    userAgent: String,
    positionMs: Long = 0L,
    onSelectEmbeddedEngine: (PlayerEngine) -> Unit,
    compactLabel: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .testTag("engine_selector_button")
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Select Player Engine",
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (compactLabel) activeEngine.badge else "Engine: ${activeEngine.shortLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            PlayerEngine.entries.forEach { engine ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (engine == activeEngine) "✓ ${engine.displayName}" else engine.displayName,
                            fontWeight = if (engine == activeEngine) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelectEmbeddedEngine(engine)
                    }
                )
            }
            if (streamUrl.isNotBlank()) {
                ExternalPlayerTarget.entries.forEach { ext ->
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        text = { Text("Launch in ${ext.label}") },
                        onClick = {
                            expanded = false
                            ExternalPlayerAndAbiManager.launchExternalPlayer(
                                context = context,
                                target = ext,
                                streamUrl = streamUrl,
                                title = streamTitle,
                                userAgent = userAgent,
                                positionMs = positionMs
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ParentalPinUnlockDialog(
    onDismiss: () -> Unit,
    onVerifyPin: (String) -> Boolean,
    onUnlocked: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Parental PIN Required") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter your 4-digit Parental Control PIN to unlock protected categories (Default PIN: 0000).",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                            pin = it
                            hasError = false
                        }
                    },
                    label = { Text("4-Digit PIN") },
                    singleLine = true,
                    isError = hasError,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("parental_pin_input")
                )
                if (hasError) {
                    Text(
                        "Incorrect PIN. Try again.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (onVerifyPin(pin)) {
                        onUnlocked()
                    } else {
                        hasError = true
                    }
                },
                modifier = Modifier.testTag("parental_pin_submit")
            ) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun InstantFtsSearchOverlay(
    query: String,
    results: List<StreamEntity>,
    onQueryChange: (String) -> Unit,
    onSelectStream: (StreamEntity, PlayerEngine?) -> Unit,
    onOpenDetail: (StreamEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Instant FTS5/FTS4 Search", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close search")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search Live TV, Movies, Series, Cast…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fts_search_input")
                )

                if (query.isBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Type any channel, movie title, genre, or actor for sub-millisecond SQLite FTS results.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (results.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No matching streams for \"$query\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(results.size, key = { results[it].streamKey }) { idx ->
                            val item = results[idx]
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        if (item.streamType == StreamType.LIVE) {
                                            onSelectStream(item, null)
                                        } else {
                                            onOpenDetail(item)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    StylizedMediaArtwork(
                                        imageUrl = item.logoUrl,
                                        title = item.name,
                                        isChannelLogo = item.streamType == StreamType.LIVE,
                                        modifier = Modifier
                                            .size(width = 52.dp, height = 40.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.streamType.name} • ${item.categoryName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                onDismiss()
                                                onSelectStream(item, PlayerEngine.VLC_EMBEDDED)
                                            },
                                            modifier = Modifier.height(34.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Text("VLC", style = MaterialTheme.typography.labelSmall)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                onDismiss()
                                                onSelectStream(item, PlayerEngine.MPV_EMBEDDED)
                                            },
                                            modifier = Modifier.height(34.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                                        ) {
                                            Text("MPV", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

fun formatTimeShort(millis: Long): String {
    if (millis <= 0L) return "--:--"
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}

fun formatDurationClock(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000L
    val hrs = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return if (hrs > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

fun formatExpirationDate(expSec: Long?): String {
    if (expSec == null || expSec <= 0L) return "Unlimited"
    return SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(expSec * 1000L))
}

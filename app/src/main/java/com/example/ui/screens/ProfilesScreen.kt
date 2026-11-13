package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.PlaylistEntity
import com.example.ui.components.TelemetryBadgePill
import com.example.ui.components.formatExpirationDate
import com.example.ui.theme.EmeraldOnline
import com.example.ui.viewmodel.SyncBannerState

@Composable
fun ProfilesScreen(
    playlists: List<PlaylistEntity>,
    syncState: SyncBannerState,
    onBack: () -> Unit,
    onSwitchPlaylist: (Long) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onAddXtream: (String, String, String, String) -> Unit,
    onAddM3uUrl: (String, String, String) -> Unit,
    onAddM3uLocal: (String, Uri, String) -> Unit,
    onAddStalker: (String, String, String) -> Unit,
    onGenerateMac: () -> String,
    onLoadShowcase: () -> Unit
) {
    var activeTab by rememberSaveable { mutableIntStateOf(0) }

    // Xtream inputs
    var xtName by rememberSaveable { mutableStateOf("") }
    var xtUrl by rememberSaveable { mutableStateOf("") }
    var xtUser by rememberSaveable { mutableStateOf("") }
    var xtPass by rememberSaveable { mutableStateOf("") }

    // M3U inputs
    var m3uName by rememberSaveable { mutableStateOf("") }
    var m3uUrl by rememberSaveable { mutableStateOf("") }
    var m3uEpgUrl by rememberSaveable { mutableStateOf("") }

    // Stalker inputs
    var stkName by rememberSaveable { mutableStateOf("") }
    var stkUrl by rememberSaveable { mutableStateOf("") }
    var stkMac by rememberSaveable { mutableStateOf(onGenerateMac()) }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onAddM3uLocal(m3uName.ifBlank { "Local M3U Playlist" }, uri, m3uEpgUrl)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("profiles_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text("Playlists & Multi-Profile Switcher", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Xtream Codes API • M3U/M3U8 Streaming • Stalker MAC Portal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (syncState.isSyncing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            syncState.errorMessage?.let { err ->
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // Saved Profiles Switcher
            item {
                Text("Saved Provider Profiles (1-Tap Switch)", style = MaterialTheme.typography.titleMedium)
            }

            items(playlists, key = { it.id }) { p ->
                Surface(
                    color = if (p.isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (p.isActive) 1.5.dp else 1.dp,
                            color = if (p.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onSwitchPlaylist(p.id) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (p.isActive) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldOnline, modifier = Modifier.size(16.dp))
                                }
                                Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                TelemetryBadgePill(text = p.type.label)
                            }
                            Text(
                                text = "${p.accountStatus} • Exp: ${formatExpirationDate(p.expirationTimestampSec)} • Cons: ${p.activeConnections}/${p.maxConnections}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (playlists.size > 1) {
                            IconButton(onClick = { onDeletePlaylist(p.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete profile", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // Ingestion Method Selector Tabs
            item {
                Spacer(Modifier.height(6.dp))
                Text("Add New BYOP Provider Playlist", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf("Xtream API", "M3U / File", "Stalker MAC", "Open Showcase")
                    tabs.forEachIndexed { idx, label ->
                        FilterChip(
                            selected = activeTab == idx,
                            onClick = { activeTab = idx },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // Active Tab Form Card
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (activeTab) {
                            0 -> {
                                Text("Xtream Codes API Login", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                OutlinedTextField(
                                    value = xtName,
                                    onValueChange = { xtName = it },
                                    label = { Text("Profile Name (e.g., Living Room IPTV)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = xtUrl,
                                    onValueChange = { xtUrl = it },
                                    label = { Text("Server URL (http://domain:port)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = xtUser,
                                    onValueChange = { xtUser = it },
                                    label = { Text("Username") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = xtPass,
                                    onValueChange = { xtPass = it },
                                    label = { Text("Password") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Button(
                                    onClick = { onAddXtream(xtName, xtUrl, xtUser, xtPass) },
                                    enabled = xtUrl.isNotBlank() && xtUser.isNotBlank() && !syncState.isSyncing,
                                    modifier = Modifier.fillMaxWidth().testTag("submit_xtream_button")
                                ) {
                                    Text("Verify /player_api.php & Sync Catalog")
                                }
                            }
                            1 -> {
                                Text("M3U / M3U8 Streaming Loader", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                OutlinedTextField(
                                    value = m3uName,
                                    onValueChange = { m3uName = it },
                                    label = { Text("Profile Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = m3uUrl,
                                    onValueChange = { m3uUrl = it },
                                    label = { Text("Remote M3U / M3U8 URL") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = m3uEpgUrl,
                                    onValueChange = { m3uEpgUrl = it },
                                    label = { Text("Optional Custom XMLTV (.xml / .xml.gz) URL") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = { onAddM3uUrl(m3uName, m3uUrl, m3uEpgUrl) },
                                        enabled = m3uUrl.isNotBlank() && !syncState.isSyncing,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Import M3U URL")
                                    }
                                    FilledTonalButton(
                                        onClick = { documentPicker.launch(arrayOf("*/*")) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Pick Local File")
                                    }
                                }
                            }
                            2 -> {
                                Text("Stalker / Ministra MAC Portal", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                OutlinedTextField(
                                    value = stkName,
                                    onValueChange = { stkName = it },
                                    label = { Text("Profile Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = stkUrl,
                                    onValueChange = { stkUrl = it },
                                    label = { Text("Portal URL (e.g., http://portal.domain.com/c)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = stkMac,
                                        onValueChange = { stkMac = it },
                                        label = { Text("Virtual STB MAC (00:1A:79:XX:XX:XX)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(onClick = { stkMac = onGenerateMac() }) {
                                        Icon(Icons.Default.Casino, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("New")
                                    }
                                }
                                Button(
                                    onClick = { onAddStalker(stkName, stkUrl, stkMac) },
                                    enabled = stkUrl.isNotBlank() && stkMac.length >= 17 && !syncState.isSyncing,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("STB Handshake & Sync Channels")
                                }
                            }
                            else -> {
                                Text("1-Tap Open-Source Broadcast Showcase", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    "Loads legally licensed Blender Foundation open-source movies (Big Buck Bunny, Sintel, Tears of Steel, Elephants Dream) and public HLS test streams across 12 Live TV channels, 24h XMLTV EPG, 10 VOD Movies, and 6 Multi-Season Series.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = onLoadShowcase,
                                    modifier = Modifier.fillMaxWidth().testTag("reload_showcase_button")
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Reload Open-Source Broadcast Showcase")
                                }
                            }
                        }
                    }
                }
            }

            // BYOP Legal Safeguard Card
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = stringResource(id = R.string.disclaimer_byop),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.CategoryEntity
import com.example.data.preferences.DecoderMode
import com.example.data.preferences.PlayerEngine
import com.example.data.preferences.PlayerSettings
import com.example.data.preferences.ThemePreset
import com.example.player.AbiAndEngineReport
import com.example.ui.components.TelemetryBadgePill
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.viewmodel.SpeedTestUiState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsUtilitiesScreen(
    settings: PlayerSettings,
    liveCategories: List<CategoryEntity>,
    abiReport: AbiAndEngineReport,
    speedTestState: SpeedTestUiState,
    onBack: () -> Unit,
    onSelectEngine: (PlayerEngine) -> Unit,
    onToggleAutoFallback: (Boolean) -> Unit,
    onSelectDecoder: (DecoderMode) -> Unit,
    onUpdateBufferSeconds: (Int) -> Unit,
    onUpdateUserAgent: (String) -> Unit,
    onSelectTheme: (ThemePreset) -> Unit,
    onToggleParentalEnabled: (Boolean) -> Unit,
    onUpdateParentalPin: (String) -> Unit,
    onToggleCategoryLock: (CategoryEntity) -> Unit,
    onRunSpeedTest: () -> Unit
) {
    var bufferDraft by remember(settings.bufferSeconds) { mutableFloatStateOf(settings.bufferSeconds.toFloat()) }
    var userAgentDraft by remember(settings.userAgent) { mutableStateOf(settings.userAgent) }
    var newPinDraft by remember { mutableStateOf("") }

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
                IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text("Player Engines, Split ABIs & Utilities", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "ExoPlayer • LibVLC • MPV • Network Speed Test • Buffer & Parental Controls",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Tri-Engine Playback Selector (Media3 ExoPlayer + Embedded VLC + Embedded MPV)
            item {
                SettingsSectionCard(
                    title = "Tri-Engine Playback Stack (ExoPlayer • VLC • MPV)",
                    icon = Icons.Default.PlayCircle
                ) {
                    Text(
                        "Select your global default playback engine. You can also switch between ExoPlayer, Embedded LibVLC, Embedded MPV, or External VLC/MPV inside any Live TV, Movie, Series, or Multi-Screen view.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerEngine.entries.forEach { engine ->
                            FilterChip(
                                selected = settings.defaultEngine == engine,
                                onClick = { onSelectEngine(engine) },
                                label = { Text(engine.displayName) },
                                modifier = Modifier.testTag("select_engine_${engine.name.lowercase()}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Fallback Across Engines", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Automatically switch ExoPlayer → LibVLC → MPV on raw .ts / AC3 / codec failure",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = settings.autoFallbackEngine, onCheckedChange = onToggleAutoFallback)
                    }

                    Text("Hardware vs. Software Decoder", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecoderMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.decoderMode == mode,
                                onClick = { onSelectDecoder(mode) },
                                label = { Text("${mode.label} (${mode.name})") }
                            )
                        }
                    }
                }
            }

            // 2. Per-Architecture Split Build & Native JNI Telemetry
            item {
                SettingsSectionCard(
                    title = "Architecture Split Builds & Native Engines",
                    icon = Icons.Default.Memory
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TelemetryBadgePill(
                            text = "ACTIVE ABI: ${abiReport.primaryAbi}",
                            containerColor = EmeraldOnline.copy(alpha = 0.2f),
                            contentColor = EmeraldOnline
                        )
                        TelemetryBadgePill(
                            text = "JNI READY",
                            containerColor = ElectricCyan.copy(alpha = 0.2f),
                            contentColor = ElectricCyan
                        )
                    }
                    Text(
                        text = "Configured Gradle ABI Split Outputs: ${abiReport.splitTargets.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Bundled Engines: ${abiReport.exoPlayerVersion} • ${abiReport.libVlcVersion} • ${abiReport.libMpvVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Detected External Apps: VLC (${if (abiReport.externalVlcInstalled) "Installed" else "Via Chooser"}) • MPV (${if (abiReport.externalMpvInstalled) "Installed" else "Via Chooser"}) • MX (${if (abiReport.externalMxInstalled) "Installed" else "Via Chooser"})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // 3. Built-in Network Throughput & Latency Speed Tester
            item {
                SettingsSectionCard(
                    title = "Built-in Stream Throughput & Latency Speed Test",
                    icon = Icons.Default.Speed
                ) {
                    if (speedTestState.isRunning) {
                        LinearProgressIndicator(
                            progress = { speedTestState.progress },
                            modifier = Modifier.fillMaxWidth().height(6.dp)
                        )
                        Text(
                            text = String.format(Locale.US, "Measuring live stream throughput: %.1f Mbps…", speedTestState.liveMbps),
                            style = MaterialTheme.typography.titleSmall,
                            color = ElectricCyan
                        )
                    }

                    speedTestState.result?.let { res ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SpeedMetricColumn("Download", String.format(Locale.US, "%.1f Mbps", res.downloadMbps))
                            SpeedMetricColumn("Latency", "${res.latencyMs} ms")
                            SpeedMetricColumn("Jitter", "${res.jitterMs} ms")
                        }
                        TelemetryBadgePill(
                            text = res.qualityVerdict,
                            containerColor = EmeraldOnline.copy(alpha = 0.18f),
                            contentColor = EmeraldOnline
                        )
                    }

                    speedTestState.error?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = onRunSpeedTest,
                        enabled = !speedTestState.isRunning,
                        modifier = Modifier.fillMaxWidth().testTag("run_speed_test_button")
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (speedTestState.isRunning) "Testing Bandwidth…" else "Run Broadcast Speed Test")
                    }
                }
            }

            // 4. Stream Buffer Size (1s - 60s) & Custom HTTP User-Agent
            item {
                SettingsSectionCard(
                    title = "Stream Buffer & Custom HTTP User-Agent",
                    icon = Icons.Default.Memory
                ) {
                    Text(
                        text = "Buffer Duration: ${bufferDraft.toInt()} seconds (Applied to ExoPlayer, LibVLC & MPV)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Slider(
                        value = bufferDraft,
                        onValueChange = { bufferDraft = it },
                        onValueChangeFinished = { onUpdateBufferSeconds(bufferDraft.toInt()) },
                        valueRange = 1f..60f
                    )
                    OutlinedTextField(
                        value = userAgentDraft,
                        onValueChange = { userAgentDraft = it },
                        label = { Text("Custom HTTP User-Agent Header") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { onUpdateUserAgent(userAgentDraft) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Save User-Agent")
                    }
                }
            }

            // 5. Broadcast Themes
            item {
                SettingsSectionCard(
                    title = "Broadcast UI Theme Presets",
                    icon = Icons.Default.Palette
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreset.entries.forEach { preset ->
                            FilterChip(
                                selected = settings.themePreset == preset,
                                onClick = { onSelectTheme(preset) },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                }
            }

            // 6. Parental Controls (4-Digit PIN & Category Locks)
            item {
                SettingsSectionCard(
                    title = "Parental Controls & Category PIN Lock",
                    icon = Icons.Default.Lock
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Require 4-Digit PIN for Locked Groups", style = MaterialTheme.typography.titleSmall)
                        Switch(checked = settings.parentalEnabled, onCheckedChange = onToggleParentalEnabled)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newPinDraft,
                            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) newPinDraft = it },
                            label = { Text("New 4-Digit PIN (Current: ${settings.parentalPin})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                onUpdateParentalPin(newPinDraft)
                                newPinDraft = ""
                            },
                            enabled = newPinDraft.length == 4
                        ) {
                            Text("Update PIN")
                        }
                    }

                    if (liveCategories.isNotEmpty()) {
                        Text("Lock / Unlock Live TV Categories:", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            liveCategories.forEach { cat ->
                                FilterChip(
                                    selected = cat.isAdultLocked,
                                    onClick = { onToggleCategoryLock(cat) },
                                    leadingIcon = {
                                        Icon(
                                            if (cat.isAdultLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    label = { Text(cat.name) }
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
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun SpeedMetricColumn(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ElectricCyan)
    }
}

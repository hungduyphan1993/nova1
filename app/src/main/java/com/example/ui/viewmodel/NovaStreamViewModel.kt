package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.CategoryEntity
import com.example.data.local.EpgProgramEntity
import com.example.data.local.EpisodeEntity
import com.example.data.local.PlaybackProgressEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistType
import com.example.data.local.StreamEntity
import com.example.data.local.StreamType
import com.example.data.network.SpeedTestResult
import com.example.data.preferences.AspectRatioMode
import com.example.data.preferences.DecoderMode
import com.example.data.preferences.PlayerEngine
import com.example.data.preferences.PlayerPreferencesRepository
import com.example.data.preferences.PlayerSettings
import com.example.data.preferences.ThemePreset
import com.example.data.repository.IptvRepository
import com.example.player.AbiAndEngineReport
import com.example.player.ExternalPlayerAndAbiManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaybackRequest(
    val streamKey: String,
    val playlistId: Long,
    val streamType: StreamType,
    val title: String,
    val subtitle: String = "",
    val streamUrl: String,
    val logoUrl: String = "",
    val channelNumber: Int = 0,
    val isLive: Boolean = true,
    val isCatchUp: Boolean = false,
    val qualityBadge: String = "1080p FHD",
    val tvgId: String = "",
    val resumePositionMs: Long = 0L,
    val overrideEngine: PlayerEngine? = null
)

sealed interface AppScreen {
    data object Dashboard : AppScreen
    data class LiveTv(val catchUpOnly: Boolean = false) : AppScreen
    data object EpgGrid : AppScreen
    data object Movies : AppScreen
    data object Series : AppScreen
    data class CinemaDetail(val streamKey: String) : AppScreen
    data class FullscreenPlayer(val request: PlaybackRequest) : AppScreen
    data object MultiScreen : AppScreen
    data object Profiles : AppScreen
    data object Settings : AppScreen
}

data class SyncBannerState(
    val isSyncing: Boolean = false,
    val statusMessage: String = "",
    val errorMessage: String? = null
)

data class SpeedTestUiState(
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val liveMbps: Double = 0.0,
    val result: SpeedTestResult? = null,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NovaStreamViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    val repository = IptvRepository(application, database.iptvDao())
    private val prefsRepo = PlayerPreferencesRepository(application)

    val abiReport: AbiAndEngineReport = ExternalPlayerAndAbiManager.inspectDeviceAbiAndPlayers(application)

    private val navBackStack = MutableStateFlow<List<AppScreen>>(listOf(AppScreen.Dashboard))
    val currentScreen: StateFlow<AppScreen> = navBackStack
        .combine(MutableStateFlow(Unit)) { stack, _ -> stack.lastOrNull() ?: AppScreen.Dashboard }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppScreen.Dashboard)

    val playerSettings: StateFlow<PlayerSettings> = prefsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerSettings())

    val playlists: StateFlow<List<PlaylistEntity>> = repository.playlistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePlaylist: StateFlow<PlaylistEntity?> = repository.activePlaylistFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _syncState = MutableStateFlow(SyncBannerState())
    val syncState: StateFlow<SyncBannerState> = _syncState.asStateFlow()

    private val _adultUnlockedInSession = MutableStateFlow(false)
    val adultUnlockedInSession: StateFlow<Boolean> = _adultUnlockedInSession.asStateFlow()

    private val _globalSearchOpen = MutableStateFlow(false)
    val globalSearchOpen: StateFlow<Boolean> = _globalSearchOpen.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _speedTestState = MutableStateFlow(SpeedTestUiState())
    val speedTestState: StateFlow<SpeedTestUiState> = _speedTestState.asStateFlow()

    // Multi-Screen 4-quadrant state (channel keys + per-slot engines + active audio quadrant 0..3)
    private val _multiScreenChannelKeys = MutableStateFlow<List<String?>>(listOf(null, null, null, null))
    val multiScreenChannelKeys: StateFlow<List<String?>> = _multiScreenChannelKeys.asStateFlow()

    private val _multiScreenEngines = MutableStateFlow<List<PlayerEngine>>(
        listOf(PlayerEngine.EXOPLAYER, PlayerEngine.EXOPLAYER, PlayerEngine.VLC_EMBEDDED, PlayerEngine.EXOPLAYER)
    )
    val multiScreenEngines: StateFlow<List<PlayerEngine>> = _multiScreenEngines.asStateFlow()

    private val _activeAudioQuadrant = MutableStateFlow(0)
    val activeAudioQuadrant: StateFlow<Int> = _activeAudioQuadrant.asStateFlow()

    // Reactive active catalog flows
    val liveCategories: StateFlow<List<CategoryEntity>> = activePlaylist
        .flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repository.observeCategories(p.id, StreamType.LIVE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vodCategories: StateFlow<List<CategoryEntity>> = activePlaylist
        .flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repository.observeCategories(p.id, StreamType.VOD) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val seriesCategories: StateFlow<List<CategoryEntity>> = activePlaylist
        .flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repository.observeCategories(p.id, StreamType.SERIES) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val liveStreams: StateFlow<List<StreamEntity>> = activePlaylist
        .flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repository.observeStreams(p.id, StreamType.LIVE) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vodStreams: StateFlow<List<StreamEntity>> = activePlaylist
        .flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repository.observeStreams(p.id, StreamType.VOD) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val seriesStreams: StateFlow<List<StreamEntity>> = activePlaylist
        .flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repository.observeStreams(p.id, StreamType.SERIES) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<StreamEntity>> = activePlaylist
        .flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repository.observeFavorites(p.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val continueWatching: StateFlow<List<PlaybackProgressEntity>> = activePlaylist
        .flatMapLatest { p -> if (p == null) flowOf(emptyList()) else repository.observeContinueWatching(p.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val epgProgramsMap: StateFlow<Map<String, List<EpgProgramEntity>>> = activePlaylist
        .flatMapLatest { p ->
            if (p == null) {
                flowOf(emptyMap())
            } else {
                val now = System.currentTimeMillis()
                repository.observeEpgWindow(p.id, now - 6 * 3600_000L, now + 18 * 3600_000L)
                    .combine(flowOf(Unit)) { list, _ -> list.groupBy { it.tvgId } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val catalogCounts: StateFlow<QuadCounts> = activePlaylist
        .flatMapLatest { p ->
            if (p == null) {
                flowOf(QuadCounts(0, 0, 0, 0))
            } else {
                combine(
                    repository.observeCountByType(p.id, StreamType.LIVE),
                    repository.observeCountByType(p.id, StreamType.VOD),
                    repository.observeCountByType(p.id, StreamType.SERIES),
                    repository.observeTotalEpgCount(p.id)
                ) { l, v, s, e -> QuadCounts(l, v, s, e) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QuadCounts(0, 0, 0, 0))

    val ftsSearchResults: StateFlow<List<StreamEntity>> = combine(
        activePlaylist,
        _searchQuery.debounce(120L)
    ) { p, q -> p to q }
        .flatMapLatest { (p, q) ->
            if (p == null || q.isBlank()) flowOf(emptyList())
            else repository.searchInstantFts(p.id, q)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _syncState.value = SyncBannerState(isSyncing = true, statusMessage = "Verifying broadcast catalog & 24h EPG…")
            runCatching {
                repository.ensureSelfHealingCatalog()
            }.onFailure { err ->
                _syncState.value = SyncBannerState(isSyncing = false, errorMessage = err.message)
            }.onSuccess {
                _syncState.value = SyncBannerState(isSyncing = false)
            }
        }

        // Auto-populate default 4-way Multi-Screen quadrants from active Live TV channels
        viewModelScope.launch {
            liveStreams.collect { channels ->
                val nonAdult = channels.filter { !it.isAdult }
                val current = _multiScreenChannelKeys.value
                if (nonAdult.isNotEmpty() && current.all { it == null }) {
                    _multiScreenChannelKeys.value = List(4) { idx ->
                        nonAdult.getOrNull(idx % nonAdult.size)?.streamKey
                    }
                }
            }
        }
    }

    // Navigation
    fun navigateTo(screen: AppScreen) {
        val current = navBackStack.value
        if (current.lastOrNull() == screen) return
        navBackStack.value = current + screen
    }

    fun navigateBack(): Boolean {
        val current = navBackStack.value
        if (current.size <= 1) return false
        navBackStack.value = current.dropLast(1)
        return true
    }

    fun popToDashboard() {
        navBackStack.value = listOf(AppScreen.Dashboard)
    }

    // Search
    fun setGlobalSearchOpen(open: Boolean) {
        _globalSearchOpen.value = open
        if (!open) _searchQuery.value = ""
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Stream Playback Launches
    fun playStream(
        stream: StreamEntity,
        overrideEngine: PlayerEngine? = null,
        resumeMs: Long = 0L,
        customUrl: String? = null,
        isCatchUp: Boolean = false,
        customSubtitle: String? = null
    ) {
        val request = PlaybackRequest(
            streamKey = stream.streamKey,
            playlistId = stream.playlistId,
            streamType = stream.streamType,
            title = stream.name,
            subtitle = customSubtitle ?: stream.categoryName,
            streamUrl = customUrl ?: stream.streamUrl,
            logoUrl = stream.logoUrl,
            channelNumber = stream.channelNumber,
            isLive = stream.streamType == StreamType.LIVE && !isCatchUp,
            isCatchUp = isCatchUp,
            qualityBadge = stream.qualityBadge,
            tvgId = stream.tvgId,
            resumePositionMs = resumeMs,
            overrideEngine = overrideEngine
        )
        navigateTo(AppScreen.FullscreenPlayer(request))
    }

    fun playEpisode(
        series: StreamEntity,
        episode: EpisodeEntity,
        overrideEngine: PlayerEngine? = null
    ) {
        val request = PlaybackRequest(
            streamKey = episode.id,
            playlistId = series.playlistId,
            streamType = StreamType.SERIES,
            title = "${series.name} • S${episode.seasonNumber}E${episode.episodeNumber}",
            subtitle = episode.title,
            streamUrl = episode.streamUrl,
            logoUrl = episode.thumbnailUrl.ifBlank { series.logoUrl },
            channelNumber = 0,
            isLive = false,
            isCatchUp = false,
            qualityBadge = series.qualityBadge,
            resumePositionMs = 0L,
            overrideEngine = overrideEngine
        )
        navigateTo(AppScreen.FullscreenPlayer(request))
    }

    fun playContinueWatching(item: PlaybackProgressEntity, overrideEngine: PlayerEngine? = null) {
        val engine = overrideEngine ?: runCatching { PlayerEngine.valueOf(item.lastEngine) }.getOrNull()
        val request = PlaybackRequest(
            streamKey = item.streamKey,
            playlistId = item.playlistId,
            streamType = item.streamType,
            title = item.title,
            subtitle = item.subtitle,
            streamUrl = item.streamUrl,
            logoUrl = item.posterUrl,
            isLive = item.streamType == StreamType.LIVE,
            resumePositionMs = if (item.streamType == StreamType.LIVE) 0L else item.positionMs,
            overrideEngine = engine
        )
        navigateTo(AppScreen.FullscreenPlayer(request))
    }

    fun playCatchUpProgram(channel: StreamEntity, program: EpgProgramEntity, overrideEngine: PlayerEngine? = null) {
        val active = activePlaylist.value
        val now = System.currentTimeMillis()
        val isPast = program.endTimeMillis <= now
        val targetUrl = if (isPast && active?.type == PlaylistType.XTREAM) {
            val durMin = ((program.endTimeMillis - program.startTimeMillis) / 60_000L).toInt().coerceAtLeast(15)
            repository.networkClients.buildXtreamTimeshiftUrl(
                serverUrl = active.serverUrl,
                username = active.username,
                password = active.password,
                providerStreamId = channel.providerStreamId,
                startMillis = program.startTimeMillis,
                durationMinutes = durMin
            )
        } else {
            channel.streamUrl
        }
        playStream(
            stream = channel,
            overrideEngine = overrideEngine,
            customUrl = targetUrl,
            isCatchUp = isPast,
            customSubtitle = program.title
        )
    }

    fun recordPlaybackProgress(request: PlaybackRequest, positionMs: Long, durationMs: Long, engine: PlayerEngine) {
        viewModelScope.launch {
            repository.savePlaybackProgress(
                streamKey = request.streamKey,
                playlistId = request.playlistId,
                streamType = request.streamType,
                title = request.title,
                subtitle = request.subtitle,
                posterUrl = request.logoUrl,
                streamUrl = request.streamUrl,
                positionMs = positionMs,
                durationMs = durationMs,
                engineName = engine.name
            )
        }
    }

    fun observeStream(streamKey: String): Flow<StreamEntity?> = repository.observeStreamByKey(streamKey)

    fun observeSeriesEpisodes(seriesStreamKey: String): Flow<List<EpisodeEntity>> =
        repository.observeEpisodes(seriesStreamKey)

    fun ensureSeriesLoaded(series: StreamEntity) {
        viewModelScope.launch {
            repository.ensureSeriesEpisodesLoaded(series, playerSettings.value.userAgent)
        }
    }

    fun refreshShortEpgForChannel(channel: StreamEntity) {
        viewModelScope.launch {
            repository.refreshShortEpgIfNeeded(channel, playerSettings.value.userAgent)
        }
    }

    fun toggleFavorite(stream: StreamEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(stream.streamKey, stream.isFavorite)
        }
    }

    fun toggleFavoriteByKey(streamKey: String) {
        viewModelScope.launch {
            val current = repository.getStreamByKeyOnce(streamKey) ?: return@launch
            repository.toggleFavorite(current.streamKey, current.isFavorite)
        }
    }

    // Multi-Screen Controls
    fun setActiveAudioQuadrant(quadrant: Int) {
        _activeAudioQuadrant.value = quadrant.coerceIn(0, 3)
    }

    fun assignMultiScreenChannel(quadrant: Int, streamKey: String?) {
        val updated = _multiScreenChannelKeys.value.toMutableList()
        if (quadrant in 0..3) {
            updated[quadrant] = streamKey
            _multiScreenChannelKeys.value = updated
        }
    }

    fun cycleMultiScreenEngine(quadrant: Int) {
        val updated = _multiScreenEngines.value.toMutableList()
        if (quadrant in 0..3) {
            updated[quadrant] = updated[quadrant].next()
            _multiScreenEngines.value = updated
        }
    }

    // Preferences & Engine Actions
    fun setDefaultEngine(engine: PlayerEngine) {
        viewModelScope.launch { prefsRepo.setDefaultEngine(engine) }
    }

    fun cycleDefaultEngine() {
        viewModelScope.launch { prefsRepo.setDefaultEngine(playerSettings.value.defaultEngine.next()) }
    }

    fun setAutoFallback(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoFallback(enabled) }
    }

    fun setDecoderMode(mode: DecoderMode) {
        viewModelScope.launch { prefsRepo.setDecoderMode(mode) }
    }

    fun setAspectRatioMode(mode: AspectRatioMode) {
        viewModelScope.launch { prefsRepo.setAspectRatioMode(mode) }
    }

    fun setBufferSeconds(seconds: Int) {
        viewModelScope.launch { prefsRepo.setBufferSeconds(seconds) }
    }

    fun setUserAgent(userAgent: String) {
        viewModelScope.launch { prefsRepo.setUserAgent(userAgent) }
    }

    fun setThemePreset(preset: ThemePreset) {
        viewModelScope.launch { prefsRepo.setThemePreset(preset) }
    }

    // Parental Controls
    fun verifyParentalPin(enteredPin: String): Boolean {
        val valid = enteredPin == playerSettings.value.parentalPin
        if (valid) _adultUnlockedInSession.value = true
        return valid
    }

    fun lockAdultSessionAgain() {
        _adultUnlockedInSession.value = false
    }

    fun updateParentalPin(newPin: String) {
        viewModelScope.launch { prefsRepo.setParentalPin(newPin) }
    }

    fun setParentalEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setParentalEnabled(enabled) }
    }

    fun toggleCategoryLock(category: CategoryEntity) {
        viewModelScope.launch { repository.setCategoryAdultLock(category.id, !category.isAdultLocked) }
    }

    // Playlist Ingestion & Multi-Profile Switching
    fun syncActivePlaylist() {
        val active = activePlaylist.value ?: return
        viewModelScope.launch {
            _syncState.value = SyncBannerState(isSyncing = true, statusMessage = "Syncing ${active.name}…")
            runCatching {
                repository.syncPlaylistContent(active.id, playerSettings.value.userAgent) { msg ->
                    _syncState.value = SyncBannerState(isSyncing = true, statusMessage = msg)
                }
            }.onSuccess {
                _syncState.value = SyncBannerState(isSyncing = false, statusMessage = "Synced ${active.name}")
            }.onFailure { err ->
                _syncState.value = SyncBannerState(isSyncing = false, errorMessage = err.message ?: "Sync failed")
            }
        }
    }

    fun loadShowcaseCatalog() {
        viewModelScope.launch {
            _syncState.value = SyncBannerState(isSyncing = true, statusMessage = "Loading Open-Source Broadcast Showcase…")
            runCatching {
                repository.seedOrRefreshShowcasePlaylist(makeActive = true)
            }.onSuccess {
                _syncState.value = SyncBannerState(isSyncing = false)
                popToDashboard()
            }.onFailure { err ->
                _syncState.value = SyncBannerState(isSyncing = false, errorMessage = err.message)
            }
        }
    }

    fun addXtreamPlaylist(name: String, serverUrl: String, username: String, password: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _syncState.value = SyncBannerState(isSyncing = true, statusMessage = "Connecting to Xtream server…")
            runCatching {
                repository.addAndSyncXtreamPlaylist(
                    name, serverUrl, username, password, playerSettings.value.userAgent
                ) { msg -> _syncState.value = SyncBannerState(isSyncing = true, statusMessage = msg) }
            }.onSuccess {
                _syncState.value = SyncBannerState(isSyncing = false)
                onDone()
            }.onFailure { err ->
                _syncState.value = SyncBannerState(isSyncing = false, errorMessage = err.message ?: "Xtream login failed")
            }
        }
    }

    fun addM3uUrlPlaylist(name: String, m3uUrl: String, epgUrl: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _syncState.value = SyncBannerState(isSyncing = true, statusMessage = "Downloading M3U playlist…")
            runCatching {
                repository.addAndSyncM3uUrlPlaylist(
                    name, m3uUrl, epgUrl, playerSettings.value.userAgent
                ) { msg -> _syncState.value = SyncBannerState(isSyncing = true, statusMessage = msg) }
            }.onSuccess {
                _syncState.value = SyncBannerState(isSyncing = false)
                onDone()
            }.onFailure { err ->
                _syncState.value = SyncBannerState(isSyncing = false, errorMessage = err.message ?: "M3U import failed")
            }
        }
    }

    fun addLocalM3uPlaylist(name: String, uri: Uri, epgUrl: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _syncState.value = SyncBannerState(isSyncing = true, statusMessage = "Parsing local M3U file…")
            runCatching {
                repository.addAndSyncLocalM3uFile(
                    name, uri, epgUrl, playerSettings.value.userAgent
                ) { msg -> _syncState.value = SyncBannerState(isSyncing = true, statusMessage = msg) }
            }.onSuccess {
                _syncState.value = SyncBannerState(isSyncing = false)
                onDone()
            }.onFailure { err ->
                _syncState.value = SyncBannerState(isSyncing = false, errorMessage = err.message ?: "Local M3U error")
            }
        }
    }

    fun addStalkerPlaylist(name: String, portalUrl: String, macAddress: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _syncState.value = SyncBannerState(isSyncing = true, statusMessage = "Performing Stalker STB Handshake…")
            runCatching {
                repository.addAndSyncStalkerPlaylist(
                    name, portalUrl, macAddress, playerSettings.value.userAgent
                ) { msg -> _syncState.value = SyncBannerState(isSyncing = true, statusMessage = msg) }
            }.onSuccess {
                _syncState.value = SyncBannerState(isSyncing = false)
                onDone()
            }.onFailure { err ->
                _syncState.value = SyncBannerState(isSyncing = false, errorMessage = err.message ?: "Stalker portal handshake failed")
            }
        }
    }

    fun generateRandomStalkerMac(): String = repository.networkClients.generateVirtualMac()

    fun switchPlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.switchActivePlaylist(playlistId)
            _multiScreenChannelKeys.value = listOf(null, null, null, null)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { repository.deletePlaylist(playlistId) }
    }

    fun dismissSyncError() {
        _syncState.value = _syncState.value.copy(errorMessage = null)
    }

    // Built-in Network Speed Test
    fun startNetworkSpeedTest() {
        if (_speedTestState.value.isRunning) return
        viewModelScope.launch {
            _speedTestState.value = SpeedTestUiState(isRunning = true, progress = 0.05f)
            runCatching {
                repository.runSpeedTest(playerSettings.value.userAgent) { progress, mbps ->
                    _speedTestState.value = SpeedTestUiState(
                        isRunning = true,
                        progress = progress,
                        liveMbps = mbps
                    )
                }
            }.onSuccess { res ->
                _speedTestState.value = SpeedTestUiState(
                    isRunning = false,
                    progress = 1f,
                    liveMbps = res.downloadMbps,
                    result = res
                )
            }.onFailure { err ->
                _speedTestState.value = SpeedTestUiState(
                    isRunning = false,
                    error = err.message ?: "Speed test failed"
                )
            }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NovaStreamViewModel(application) as T
        }
    }
}

data class QuadCounts(
    val liveCount: Int,
    val vodCount: Int,
    val seriesCount: Int,
    val epgCount: Int
)

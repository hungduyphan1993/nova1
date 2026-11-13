package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.InstantFtsSearchOverlay
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EpgGridScreen
import com.example.ui.screens.LiveTvScreen
import com.example.ui.screens.MultiScreenScreen
import com.example.ui.screens.ProfilesScreen
import com.example.ui.screens.SettingsUtilitiesScreen
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.screens.VodDetailScreen
import com.example.ui.screens.VodSeriesScreen
import com.example.ui.theme.NovaStreamTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.NovaStreamViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: NovaStreamViewModel by viewModels {
        NovaStreamViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.playerSettings.collectAsStateWithLifecycle()
            NovaStreamTheme(themePreset = settings.themePreset) {
                NovaStreamAppRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun NovaStreamAppRoot(viewModel: NovaStreamViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val settings by viewModel.playerSettings.collectAsStateWithLifecycle()
    val activePlaylist by viewModel.activePlaylist.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val counts by viewModel.catalogCounts.collectAsStateWithLifecycle()
    val liveCategories by viewModel.liveCategories.collectAsStateWithLifecycle()
    val vodCategories by viewModel.vodCategories.collectAsStateWithLifecycle()
    val seriesCategories by viewModel.seriesCategories.collectAsStateWithLifecycle()
    val liveStreams by viewModel.liveStreams.collectAsStateWithLifecycle()
    val vodStreams by viewModel.vodStreams.collectAsStateWithLifecycle()
    val seriesStreams by viewModel.seriesStreams.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val epgMap by viewModel.epgProgramsMap.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val adultUnlocked by viewModel.adultUnlockedInSession.collectAsStateWithLifecycle()
    val globalSearchOpen by viewModel.globalSearchOpen.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val ftsResults by viewModel.ftsSearchResults.collectAsStateWithLifecycle()
    val speedTestState by viewModel.speedTestState.collectAsStateWithLifecycle()
    val multiScreenKeys by viewModel.multiScreenChannelKeys.collectAsStateWithLifecycle()
    val multiScreenEngines by viewModel.multiScreenEngines.collectAsStateWithLifecycle()
    val activeAudioQuad by viewModel.activeAudioQuadrant.collectAsStateWithLifecycle()

    if (currentScreen !is AppScreen.Dashboard) {
        BackHandler {
            viewModel.navigateBack()
        }
    }

    when (val screen = currentScreen) {
        AppScreen.Dashboard -> {
            DashboardScreen(
                activePlaylist = activePlaylist,
                counts = counts,
                liveStreams = liveStreams,
                vodStreams = vodStreams,
                seriesStreams = seriesStreams,
                favorites = favorites,
                continueWatching = continueWatching,
                epgMap = epgMap,
                settings = settings,
                syncState = syncState,
                abiReport = viewModel.abiReport,
                onNavigate = { viewModel.navigateTo(it) },
                onPlayStream = { stream, engine -> viewModel.playStream(stream, overrideEngine = engine) },
                onOpenDetail = { stream -> viewModel.navigateTo(AppScreen.CinemaDetail(stream.streamKey)) },
                onResumeProgress = { progress -> viewModel.playContinueWatching(progress) },
                onOpenSearch = { viewModel.setGlobalSearchOpen(true) },
                onSyncNow = { viewModel.syncActivePlaylist() },
                onCycleEngine = { viewModel.cycleDefaultEngine() }
            )
        }

        is AppScreen.LiveTv -> {
            LiveTvScreen(
                categories = liveCategories,
                channels = liveStreams,
                epgMap = epgMap,
                settings = settings,
                adultUnlocked = adultUnlocked,
                catchUpOnly = screen.catchUpOnly,
                onBack = { viewModel.navigateBack() },
                onOpenEpgGrid = { viewModel.navigateTo(AppScreen.EpgGrid) },
                onPlayFullscreen = { ch, engine -> viewModel.playStream(ch, overrideEngine = engine) },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onVerifyParentalPin = { viewModel.verifyParentalPin(it) },
                onRefreshShortEpg = { viewModel.refreshShortEpgForChannel(it) }
            )
        }

        AppScreen.EpgGrid -> {
            EpgGridScreen(
                channels = liveStreams,
                epgMap = epgMap,
                adultUnlocked = adultUnlocked,
                onBack = { viewModel.navigateBack() },
                onPlayProgram = { ch, prog, engine -> viewModel.playCatchUpProgram(ch, prog, engine) }
            )
        }

        AppScreen.Movies -> {
            VodSeriesScreen(
                isSeries = false,
                categories = vodCategories,
                items = vodStreams,
                defaultEngine = settings.defaultEngine,
                onBack = { viewModel.navigateBack() },
                onOpenSearch = { viewModel.setGlobalSearchOpen(true) },
                onPlayStream = { movie, engine -> viewModel.playStream(movie, overrideEngine = engine) },
                onOpenDetail = { movie -> viewModel.navigateTo(AppScreen.CinemaDetail(movie.streamKey)) },
                onToggleFavorite = { viewModel.toggleFavorite(it) }
            )
        }

        AppScreen.Series -> {
            VodSeriesScreen(
                isSeries = true,
                categories = seriesCategories,
                items = seriesStreams,
                defaultEngine = settings.defaultEngine,
                onBack = { viewModel.navigateBack() },
                onOpenSearch = { viewModel.setGlobalSearchOpen(true) },
                onPlayStream = { series, engine -> viewModel.playStream(series, overrideEngine = engine) },
                onOpenDetail = { series -> viewModel.navigateTo(AppScreen.CinemaDetail(series.streamKey)) },
                onToggleFavorite = { viewModel.toggleFavorite(it) }
            )
        }

        is AppScreen.CinemaDetail -> {
            val detailStream by viewModel.observeStream(screen.streamKey).collectAsStateWithLifecycle(initialValue = null)
            val episodes by viewModel.observeSeriesEpisodes(screen.streamKey).collectAsStateWithLifecycle(initialValue = emptyList())
            VodDetailScreen(
                stream = detailStream,
                episodes = episodes,
                settings = settings,
                onBack = { viewModel.navigateBack() },
                onEnsureEpisodesLoaded = { viewModel.ensureSeriesLoaded(it) },
                onPlayMovie = { movie, engine -> viewModel.playStream(movie, overrideEngine = engine) },
                onPlayEpisode = { series, ep, engine -> viewModel.playEpisode(series, ep, overrideEngine = engine) },
                onToggleFavorite = { viewModel.toggleFavorite(it) }
            )
        }

        is AppScreen.FullscreenPlayer -> {
            val isFav = favorites.any { it.streamKey == screen.request.streamKey }
            VideoPlayerScreen(
                request = screen.request,
                liveChannels = liveStreams.filter { adultUnlocked || !it.isAdult },
                epgMap = epgMap,
                isFavorite = isFav,
                settings = settings,
                abiPrimary = viewModel.abiReport.primaryAbi,
                onBack = { viewModel.navigateBack() },
                onSwitchChannel = { newCh, currentEngine ->
                    viewModel.navigateBack()
                    viewModel.playStream(newCh, overrideEngine = currentEngine)
                },
                onToggleFavorite = { viewModel.toggleFavoriteByKey(screen.request.streamKey) },
                onUpdateDecoder = { viewModel.setDecoderMode(it) },
                onUpdateAspectRatio = { viewModel.setAspectRatioMode(it) },
                onSaveProgress = { posMs, durMs, engine ->
                    viewModel.recordPlaybackProgress(screen.request, posMs, durMs, engine)
                }
            )
        }

        AppScreen.MultiScreen -> {
            MultiScreenScreen(
                channels = liveStreams,
                quadrantChannelKeys = multiScreenKeys,
                quadrantEngines = multiScreenEngines,
                activeAudioQuadrant = activeAudioQuad,
                settings = settings,
                onBack = { viewModel.navigateBack() },
                onSelectAudioQuadrant = { viewModel.setActiveAudioQuadrant(it) },
                onAssignChannel = { slot, key -> viewModel.assignMultiScreenChannel(slot, key) },
                onCycleQuadrantEngine = { slot -> viewModel.cycleMultiScreenEngine(slot) },
                onExpandQuadrant = { ch, engine -> viewModel.playStream(ch, overrideEngine = engine) }
            )
        }

        AppScreen.Profiles -> {
            ProfilesScreen(
                playlists = playlists,
                syncState = syncState,
                onBack = { viewModel.navigateBack() },
                onSwitchPlaylist = { viewModel.switchPlaylist(it) },
                onDeletePlaylist = { viewModel.deletePlaylist(it) },
                onAddXtream = { n, u, usr, p -> viewModel.addXtreamPlaylist(n, u, usr, p) { viewModel.popToDashboard() } },
                onAddM3uUrl = { n, u, e -> viewModel.addM3uUrlPlaylist(n, u, e) { viewModel.popToDashboard() } },
                onAddM3uLocal = { n, uri, e -> viewModel.addLocalM3uPlaylist(n, uri, e) { viewModel.popToDashboard() } },
                onAddStalker = { n, u, m -> viewModel.addStalkerPlaylist(n, u, m) { viewModel.popToDashboard() } },
                onGenerateMac = { viewModel.generateRandomStalkerMac() },
                onLoadShowcase = { viewModel.loadShowcaseCatalog() }
            )
        }

        AppScreen.Settings -> {
            SettingsUtilitiesScreen(
                settings = settings,
                liveCategories = liveCategories,
                abiReport = viewModel.abiReport,
                speedTestState = speedTestState,
                onBack = { viewModel.navigateBack() },
                onSelectEngine = { viewModel.setDefaultEngine(it) },
                onToggleAutoFallback = { viewModel.setAutoFallback(it) },
                onSelectDecoder = { viewModel.setDecoderMode(it) },
                onUpdateBufferSeconds = { viewModel.setBufferSeconds(it) },
                onUpdateUserAgent = { viewModel.setUserAgent(it) },
                onSelectTheme = { viewModel.setThemePreset(it) },
                onToggleParentalEnabled = { viewModel.setParentalEnabled(it) },
                onUpdateParentalPin = { viewModel.updateParentalPin(it) },
                onToggleCategoryLock = { viewModel.toggleCategoryLock(it) },
                onRunSpeedTest = { viewModel.startNetworkSpeedTest() }
            )
        }
    }

    if (globalSearchOpen) {
        InstantFtsSearchOverlay(
            query = searchQuery,
            results = ftsResults,
            onQueryChange = { viewModel.updateSearchQuery(it) },
            onSelectStream = { stream, engine -> viewModel.playStream(stream, overrideEngine = engine) },
            onOpenDetail = { stream -> viewModel.navigateTo(AppScreen.CinemaDetail(stream.streamKey)) },
            onDismiss = { viewModel.setGlobalSearchOpen(false) }
        )
    }
}

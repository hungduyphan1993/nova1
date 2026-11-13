package com.example.data.repository

import com.example.data.local.CategoryEntity
import com.example.data.local.EpgProgramEntity
import com.example.data.local.EpisodeEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistType
import com.example.data.local.StreamEntity
import com.example.data.local.StreamType
import java.util.Calendar

/**
 * Legally licensed Open-Source Broadcast Showcase catalog using Blender Foundation open movies
 * (Big Buck Bunny, Sintel, Tears of Steel, Elephants Dream) and Google Cloud Storage sample streams
 * with reliable commondatastorage.googleapis.com artwork, 12 Live TV channels, 24h XMLTV EPG,
 * 10 4K/FHD VOD Movies, and 6 Multi-Season TV Series (Seasons 1-3).
 */
object ShowcaseCatalogSeeder {

    private const val GCS_VIDEO = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample"
    private const val GCS_IMG = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/images"
    private const val MUX_HLS = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    private const val APPLE_HLS_FMP4 = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8"

    fun createShowcasePlaylist(existingId: Long = 0L): PlaylistEntity {
        val expSec = (System.currentTimeMillis() / 1000L) + (365L * 24L * 3600L)
        return PlaylistEntity(
            id = existingId,
            name = "Open-Source Broadcast Showcase",
            type = PlaylistType.SHOWCASE,
            serverUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket",
            username = "open_broadcast",
            accountStatus = "Active • Licensed CC-BY",
            expirationTimestampSec = expSec,
            activeConnections = 1,
            maxConnections = 4,
            isActive = true,
            lastSyncedAt = System.currentTimeMillis()
        )
    }

    fun buildCategories(playlistId: Long): List<CategoryEntity> {
        val liveCats = listOf(
            "cinema_live" to "Open Cinema 24/7",
            "scifi_tech" to "Sci-Fi & Studio Lab",
            "motorsport_action" to "Action & Motorsport",
            "documentary_world" to "World & Nature UHD",
            "late_vault" to "Parental PIN Vault (18+ Demo)"
        ).mapIndexed { idx, (cid, name) ->
            CategoryEntity(
                id = "$playlistId:LIVE:$cid",
                playlistId = playlistId,
                categoryId = cid,
                name = name,
                streamType = StreamType.LIVE,
                isAdultLocked = cid == "late_vault",
                sortOrder = idx
            )
        }

        val vodCats = listOf(
            "blender_premieres" to "Blender Open Premieres",
            "scifi_cinema" to "Sci-Fi & VFX Spectacles",
            "adventure_auto" to "Adventure & Grand Tour",
            "studio_shorts" to "4K Studio Showcases"
        ).mapIndexed { idx, (cid, name) ->
            CategoryEntity(
                id = "$playlistId:VOD:$cid",
                playlistId = playlistId,
                categoryId = cid,
                name = name,
                streamType = StreamType.VOD,
                sortOrder = idx
            )
        }

        val seriesCats = listOf(
            "open_originals" to "Open Studio Originals",
            "cyber_chronicles" to "Sci-Fi & Cyber Anthologies",
            "expedition_docs" to "Global Expedition Series"
        ).mapIndexed { idx, (cid, name) ->
            CategoryEntity(
                id = "$playlistId:SERIES:$cid",
                playlistId = playlistId,
                categoryId = cid,
                name = name,
                streamType = StreamType.SERIES,
                sortOrder = idx
            )
        }

        return liveCats + vodCats + seriesCats
    }

    fun buildLiveChannels(playlistId: Long, favoriteKeys: Set<String>): List<StreamEntity> {
        data class ChSpec(
            val id: String,
            val num: Int,
            val name: String,
            val catId: String,
            val catName: String,
            val url: String,
            val img: String,
            val tvgId: String,
            val badge: String,
            val catchupDays: Int = 3
        )

        val specs = listOf(
            ChSpec("ch_bbb_hls", 101, "Blender Premiere HLS 1", "cinema_live", "Open Cinema 24/7", MUX_HLS, "$GCS_IMG/BigBuckBunny.jpg", "blender.hls.1", "1080p FHD", 7),
            ChSpec("ch_sintel_uhd", 102, "Sintel Fantasy 4K", "cinema_live", "Open Cinema 24/7", "$GCS_VIDEO/Sintel.mp4", "$GCS_IMG/Sintel.jpg", "sintel.uhd", "4K UHD", 7),
            ChSpec("ch_tos_scifi", 103, "Tears of Steel Sci-Fi", "scifi_tech", "Sci-Fi & Studio Lab", "$GCS_VIDEO/TearsOfSteel.mp4", "$GCS_IMG/TearsOfSteel.jpg", "tos.scifi", "4K UHD", 5),
            ChSpec("ch_elephants", 104, "Elephants Dream Art", "scifi_tech", "Sci-Fi & Studio Lab", "$GCS_VIDEO/ElephantsDream.mp4", "$GCS_IMG/ElephantsDream.jpg", "elephants.art", "1080p FHD", 5),
            ChSpec("ch_apple_hls", 105, "Telemetry Broadcast HLS", "scifi_tech", "Sci-Fi & Studio Lab", APPLE_HLS_FMP4, "$GCS_IMG/ForBiggerMeltdowns.jpg", "telemetry.hls", "1080p FHD", 3),
            ChSpec("ch_subaru", 106, "Rally & Outback Live", "motorsport_action", "Action & Motorsport", "$GCS_VIDEO/SubaruOutbackOnStreetAndDirt.mp4", "$GCS_IMG/SubaruOutbackOnStreetAndDirt.jpg", "rally.live", "1080p FHD", 3),
            ChSpec("ch_bullrun", 107, "Grand Bullrun GT", "motorsport_action", "Action & Motorsport", "$GCS_VIDEO/WeAreGoingOnBullrun.mp4", "$GCS_IMG/WeAreGoingOnBullrun.jpg", "bullrun.gt", "1080p FHD", 3),
            ChSpec("ch_blazes", 108, "Blaze Action Network", "motorsport_action", "Action & Motorsport", "$GCS_VIDEO/ForBiggerBlazes.mp4", "$GCS_IMG/ForBiggerBlazes.jpg", "blaze.action", "4K UHD", 3),
            ChSpec("ch_escapes", 109, "Horizon Escapes TV", "documentary_world", "World & Nature UHD", "$GCS_VIDEO/ForBiggerEscapes.mp4", "$GCS_IMG/ForBiggerEscapes.jpg", "horizon.escapes", "4K UHD", 5),
            ChSpec("ch_joyrides", 110, "Coastal Joyrides HD", "documentary_world", "World & Nature UHD", "$GCS_VIDEO/ForBiggerJoyrides.mp4", "$GCS_IMG/ForBiggerJoyrides.jpg", "coastal.joyrides", "1080p FHD", 3),
            ChSpec("ch_fun_net", 111, "Open Family Showcase", "cinema_live", "Open Cinema 24/7", "$GCS_VIDEO/ForBiggerFun.mp4", "$GCS_IMG/ForBiggerFun.jpg", "family.open", "1080p FHD", 3),
            ChSpec("ch_vault_pin", 112, "Midnight Vault (PIN Demo)", "late_vault", "Parental PIN Vault (18+ Demo)", "$GCS_VIDEO/ForBiggerMeltdowns.mp4", "$GCS_IMG/ForBiggerMeltdowns.jpg", "vault.pin", "1080p FHD", 1)
        )

        return specs.map { s ->
            val key = "$playlistId:LIVE:${s.id}"
            StreamEntity(
                streamKey = key,
                playlistId = playlistId,
                providerStreamId = s.id,
                channelNumber = s.num,
                name = s.name,
                streamType = StreamType.LIVE,
                categoryId = s.catId,
                categoryName = s.catName,
                streamUrl = s.url,
                logoUrl = s.img,
                backdropUrl = s.img,
                tvgId = s.tvgId,
                catchupDays = s.catchupDays,
                qualityBadge = s.badge,
                isAdult = s.catId == "late_vault",
                isFavorite = favoriteKeys.contains(key) || s.num in listOf(101, 102, 103)
            )
        }
    }

    fun buildVodMovies(playlistId: Long, favoriteKeys: Set<String>): List<StreamEntity> {
        data class MovieSpec(
            val id: String,
            val title: String,
            val catId: String,
            val catName: String,
            val url: String,
            val img: String,
            val rating: Double,
            val year: String,
            val badge: String,
            val director: String,
            val cast: String,
            val trailerId: String,
            val synopsis: String
        )

        val movies = listOf(
            MovieSpec(
                "vod_sintel", "Sintel: The Dragon's Oath", "blender_premieres", "Blender Open Premieres",
                "$GCS_VIDEO/Sintel.mp4", "$GCS_IMG/Sintel.jpg", 9.1, "2010", "4K UHD",
                "Colin Levy", "Halina Reijn, Thom Hoffman", "eRsGyueVLvQ",
                "A lonely young warrior named Sintel searches perilous frozen peaks and ancient desert cities to rescue Scales, a fledgling dragon befriended in youth."
            ),
            MovieSpec(
                "vod_tos", "Tears of Steel (Mango Project)", "scifi_cinema", "Sci-Fi & VFX Spectacles",
                "$GCS_VIDEO/TearsOfSteel.mp4", "$GCS_IMG/TearsOfSteel.jpg", 8.9, "2012", "4K UHD",
                "Ian Hubert", "Derek de Lint, Sergio Hasselbaink, Rogier Schippers", "R6MlUcmOul8",
                "In a cybernetic future Amsterdam, a resistance team of scientists and soldiers stage a desperate temporal mission inside the Oude Kerk dome to avert a machine uprising."
            ),
            MovieSpec(
                "vod_bbb", "Big Buck Bunny Remastered", "blender_premieres", "Blender Open Premieres",
                "$GCS_VIDEO/BigBuckBunny.mp4", "$GCS_IMG/BigBuckBunny.jpg", 8.8, "2008", "4K UHD",
                "Sacha Goedegebure", "Blender Institute Ensemble", "YE7VzlLtp-4",
                "A gentle giant rabbit's peaceful sunny meadow morning turns into clever woodland slapstick strategy when three mischievous rodents push his patience too far."
            ),
            MovieSpec(
                "vod_ed", "Elephants Dream", "scifi_cinema", "Sci-Fi & VFX Spectacles",
                "$GCS_VIDEO/ElephantsDream.mp4", "$GCS_IMG/ElephantsDream.jpg", 8.4, "2006", "1080p FHD",
                "Bassam Kurdali", "Tygo Gernandt, Cas Jansen", "TLkA0RELQ1g",
                "Proog and Emo journey deep inside a surreal, endlessly shifting clockwork megastructure that challenges perception, memory, and shared reality."
            ),
            MovieSpec(
                "vod_blazes", "For Bigger Blazes", "studio_shorts", "4K Studio Showcases",
                "$GCS_VIDEO/ForBiggerBlazes.mp4", "$GCS_IMG/ForBiggerBlazes.jpg", 8.6, "2022", "4K UHD",
                "Open Broadcast Unit", "Studio Action Cast", "YE7VzlLtp-4",
                "High-dynamic-range cinema benchmark showcasing vibrant contrast, pyrotechnic highlights, and crisp motion pacing for home theater calibration."
            ),
            MovieSpec(
                "vod_escapes", "Horizon: For Bigger Escapes", "adventure_auto", "Adventure & Grand Tour",
                "$GCS_VIDEO/ForBiggerEscapes.mp4", "$GCS_IMG/ForBiggerEscapes.jpg", 8.9, "2023", "4K UHD",
                "Elena Vance", "Alpine Expedition Crew", "eRsGyueVLvQ",
                "Sweeping aerial cinematography across untouched mountain ridges, coastal cliffs, and open wilderness designed for widescreen big-screen immersion."
            ),
            MovieSpec(
                "vod_fun", "Studio Pulse: For Bigger Fun", "studio_shorts", "4K Studio Showcases",
                "$GCS_VIDEO/ForBiggerFun.mp4", "$GCS_IMG/ForBiggerFun.jpg", 8.3, "2021", "1080p FHD",
                "Marcus Chen", "Ensemble Showcase", "YE7VzlLtp-4",
                "Fast-paced colorful showcase celebrating theatrical soundstage design and responsive multi-device streaming."
            ),
            MovieSpec(
                "vod_joyrides", "Pacific Coast Joyrides", "adventure_auto", "Adventure & Grand Tour",
                "$GCS_VIDEO/ForBiggerJoyrides.mp4", "$GCS_IMG/ForBiggerJoyrides.jpg", 8.7, "2023", "1080p FHD",
                "Liam Sterling", "West Coast Drivers", "R6MlUcmOul8",
                "Golden-hour highway runs and coastal switchbacks captured with high-frame-rate gimbal stabilized lenses."
            ),
            MovieSpec(
                "vod_meltdowns", "Thermal Limit: Meltdowns", "scifi_cinema", "Sci-Fi & VFX Spectacles",
                "$GCS_VIDEO/ForBiggerMeltdowns.mp4", "$GCS_IMG/ForBiggerMeltdowns.jpg", 8.5, "2022", "4K UHD",
                "Kira Lindqvist", "VFX Dynamics Lab", "R6MlUcmOul8",
                "Explosive studio VFX reel testing hardware decoding throughput, shadow detail, and rapid scene transitions."
            ),
            MovieSpec(
                "vod_outback", "All-Terrain Odyssey", "adventure_auto", "Adventure & Grand Tour",
                "$GCS_VIDEO/SubaruOutbackOnStreetAndDirt.mp4", "$GCS_IMG/SubaruOutbackOnStreetAndDirt.jpg", 8.8, "2024", "1080p FHD",
                "Devon Brooks", "Backcountry Trail Team", "eRsGyueVLvQ",
                "From slick urban asphalt to rugged gravel mountain passes, an overland documentary testing endurance and precision driving."
            )
        )

        return movies.mapIndexed { idx, m ->
            val key = "$playlistId:VOD:${m.id}"
            StreamEntity(
                streamKey = key,
                playlistId = playlistId,
                providerStreamId = m.id,
                name = m.title,
                streamType = StreamType.VOD,
                categoryId = m.catId,
                categoryName = m.catName,
                streamUrl = m.url,
                logoUrl = m.img,
                backdropUrl = m.img,
                rating = m.rating,
                releaseYear = m.year,
                qualityBadge = m.badge,
                synopsis = m.synopsis,
                director = m.director,
                cast = m.cast,
                youtubeTrailerId = m.trailerId,
                isFavorite = favoriteKeys.contains(key) || idx < 2,
                addedTimestamp = System.currentTimeMillis() - (idx * 3600_000L)
            )
        }
    }

    fun buildSeriesAndEpisodes(
        playlistId: Long,
        favoriteKeys: Set<String>
    ): Pair<List<StreamEntity>, List<EpisodeEntity>> {
        data class SeriesSpec(
            val id: String,
            val title: String,
            val catId: String,
            val catName: String,
            val img: String,
            val rating: Double,
            val year: String,
            val badge: String,
            val director: String,
            val cast: String,
            val trailerId: String,
            val synopsis: String
        )

        val seriesList = listOf(
            SeriesSpec(
                "ser_blender_chronicles", "Chronicles of Open Cinema", "open_originals", "Open Studio Originals",
                "$GCS_IMG/Sintel.jpg", 9.3, "2024", "4K UHD • 3 Seasons",
                "Colin Levy & Ian Hubert", "Halina Reijn, Derek de Lint", "eRsGyueVLvQ",
                "Anthology series chronicling the world-building, visual effects breakthroughs, and complete open-source short films from the Blender Institute."
            ),
            SeriesSpec(
                "ser_amsterdam_2084", "Dome 2084: Tears of Steel", "cyber_chronicles", "Sci-Fi & Cyber Anthologies",
                "$GCS_IMG/TearsOfSteel.jpg", 9.0, "2023", "4K UHD • 3 Seasons",
                "Ian Hubert", "Sergio Hasselbaink, Denise Rebergen", "R6MlUcmOul8",
                "Multi-season sci-fi saga following Thom and Celia's cybernetic memory reconstructions across futuristic European city domes."
            ),
            SeriesSpec(
                "ser_meadow_tales", "Big Buck Meadow Tales", "open_originals", "Open Studio Originals",
                "$GCS_IMG/BigBuckBunny.jpg", 8.8, "2022", "1080p FHD • 3 Seasons",
                "Sacha Goedegebure", "Forest Animation Collective", "YE7VzlLtp-4",
                "Family-friendly animated adventures in the sunlit Blender Valley featuring inventive contraptions and vibrant 3D character animation."
            ),
            SeriesSpec(
                "ser_clockwork_mind", "The Proog Protocol", "cyber_chronicles", "Sci-Fi & Cyber Anthologies",
                "$GCS_IMG/ElephantsDream.jpg", 8.7, "2023", "1080p FHD • 3 Seasons",
                "Bassam Kurdali", "Tygo Gernandt, Cas Jansen", "TLkA0RELQ1g",
                "Surreal architectural sci-fi mystery exploring the infinite rooms, telegraph corridors, and sentient machinery of The Machine."
            ),
            SeriesSpec(
                "ser_overland_gt", "Apex Overland & Bullrun", "expedition_docs", "Global Expedition Series",
                "$GCS_IMG/WeAreGoingOnBullrun.jpg", 8.9, "2024", "4K UHD • 3 Seasons",
                "Devon Brooks", "Continental Rally Crew", "YE7VzlLtp-4",
                "Three-season motorsport and cross-country rally docuseries tracking endurance builds, desert stages, and mountain tarmac."
            ),
            SeriesSpec(
                "ser_horizon_earth", "Frontiers in 4K", "expedition_docs", "Global Expedition Series",
                "$GCS_IMG/ForBiggerEscapes.jpg", 9.1, "2025", "4K UHD • 3 Seasons",
                "Elena Vance", "Global Cinematography Unit", "eRsGyueVLvQ",
                "Reference-grade nature and expedition anthology filmed for HDR displays across glaciers, volcanic ridges, and coastal highways."
            )
        )

        val sampleVideoPool = listOf(
            "$GCS_VIDEO/Sintel.mp4" to "$GCS_IMG/Sintel.jpg",
            "$GCS_VIDEO/TearsOfSteel.mp4" to "$GCS_IMG/TearsOfSteel.jpg",
            "$GCS_VIDEO/BigBuckBunny.mp4" to "$GCS_IMG/BigBuckBunny.jpg",
            "$GCS_VIDEO/ElephantsDream.mp4" to "$GCS_IMG/ElephantsDream.jpg",
            "$GCS_VIDEO/ForBiggerEscapes.mp4" to "$GCS_IMG/ForBiggerEscapes.jpg",
            "$GCS_VIDEO/SubaruOutbackOnStreetAndDirt.mp4" to "$GCS_IMG/SubaruOutbackOnStreetAndDirt.jpg",
            MUX_HLS to "$GCS_IMG/ForBiggerBlazes.jpg"
        )

        val streams = ArrayList<StreamEntity>()
        val episodes = ArrayList<EpisodeEntity>()

        seriesList.forEachIndexed { sIdx, spec ->
            val streamKey = "$playlistId:SERIES:${spec.id}"
            streams.add(
                StreamEntity(
                    streamKey = streamKey,
                    playlistId = playlistId,
                    providerStreamId = spec.id,
                    name = spec.title,
                    streamType = StreamType.SERIES,
                    categoryId = spec.catId,
                    categoryName = spec.catName,
                    streamUrl = sampleVideoPool[sIdx % sampleVideoPool.size].first,
                    logoUrl = spec.img,
                    backdropUrl = spec.img,
                    rating = spec.rating,
                    releaseYear = spec.year,
                    qualityBadge = spec.badge,
                    synopsis = spec.synopsis,
                    director = spec.director,
                    cast = spec.cast,
                    youtubeTrailerId = spec.trailerId,
                    isFavorite = favoriteKeys.contains(streamKey) || sIdx == 0,
                    addedTimestamp = System.currentTimeMillis() - (sIdx * 1800_000L)
                )
            )

            for (season in 1..3) {
                for (ep in 1..4) {
                    val (epUrl, epThumb) = sampleVideoPool[(sIdx + season + ep) % sampleVideoPool.size]
                    episodes.add(
                        EpisodeEntity(
                            id = "$streamKey:S$season:E$ep",
                            playlistId = playlistId,
                            seriesStreamKey = streamKey,
                            providerSeriesId = spec.id,
                            seasonNumber = season,
                            episodeNumber = ep,
                            title = "S${season}E${ep} • Chapter $ep: ${episodeSubtitle(season, ep)}",
                            streamUrl = epUrl,
                            thumbnailUrl = epThumb,
                            durationLabel = "${24 + (ep * 6)}m",
                            synopsis = "Season $season Episode $ep of ${spec.title} — mastered in high-bitrate broadcast quality for ExoPlayer, LibVLC, and MPV engines.",
                            rating = (spec.rating - 0.2 + (ep * 0.1)).coerceIn(8.0, 9.8)
                        )
                    )
                }
            }
        }
        return streams to episodes
    }

    fun build24HourEpg(playlistId: Long, channels: List<StreamEntity>): List<EpgProgramEntity> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, -6) // Include 6 hours of Catch-Up archive + 18 hours ahead
        }
        val baseStart = cal.timeInMillis
        val slotMs = 30L * 60L * 1000L // 30-minute broadcast slots

        val showTemplates = listOf(
            "Open Cinema Feature Presentation" to "Uncut theatrical open-source broadcast master with multi-engine telemetry.",
            "Behind the Render Farm" to "Deep-dive into open-source 3D lighting, geometry nodes, and compositing pipelines.",
            "4K HDR Calibration Hour" to "Reference color grading, dynamic range, and motion cadence showcase.",
            "Director's Commentary Cut" to "Frame-by-frame breakdown of storyboard sequences and orchestral score.",
            "Sci-Fi Worldbuilding Live" to "Concept art, VFX breakdowns, and real-time virtual production stage tour.",
            "Global Overland Stage" to "High-octane gravel rally telemetry, onboard cameras, and route analysis."
        )

        val programs = ArrayList<EpgProgramEntity>(channels.size * 48)
        val now = System.currentTimeMillis()

        channels.forEachIndexed { chIdx, ch ->
            var cursor = baseStart
            for (slot in 0 until 48) {
                val durationMultiplier = if ((chIdx + slot) % 3 == 0) 2 else 1
                val end = cursor + (slotMs * durationMultiplier)
                val (baseTitle, desc) = showTemplates[(chIdx * 3 + slot) % showTemplates.size]
                programs.add(
                    EpgProgramEntity(
                        playlistId = playlistId,
                        tvgId = ch.tvgId,
                        title = "${ch.name.substringBefore(" ")} • $baseTitle",
                        description = desc,
                        category = ch.categoryName,
                        startTimeMillis = cursor,
                        endTimeMillis = end,
                        hasArchive = end <= now && ch.catchupDays > 0
                    )
                )
                cursor = end
                if (cursor >= baseStart + 24L * 3600_000L) break
            }
        }
        return programs
    }

    private fun episodeSubtitle(season: Int, ep: Int): String {
        val titles = listOf(
            "Signal Horizon", "Glass Observatory", "The Dragon's Crest", "Memory Vault",
            "Amsterdam Protocol", "Meadow Uprising", "Clockwork Relay", "Apex Switchback",
            "Zero Latency", "Northern Ridge", "Chromatic Shift", "Final Transmission"
        )
        return titles[((season - 1) * 4 + (ep - 1)) % titles.size]
    }
}

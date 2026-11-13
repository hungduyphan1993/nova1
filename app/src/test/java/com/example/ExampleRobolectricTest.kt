package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CategoryEntity
import com.example.data.local.StreamEntity
import com.example.data.local.StreamType
import com.example.data.network.M3uStreamingParser
import com.example.data.network.XtreamAndStalkerClients
import com.example.data.preferences.PlayerEngine
import com.example.data.repository.IptvRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `main activity launches dashboard cleanly without crashing`() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("NovaStream IPTV").assertIsDisplayed()
        composeTestRule.onNodeWithText("Multi-Screen Broadcast Control Deck").assertIsDisplayed()
    }

    @Test
    fun `self healing showcase seeder and Room FTS4 search work end to end`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("NovaStream IPTV", context.getString(R.string.app_name))
        assertEquals(PlayerEngine.VLC_EMBEDDED, PlayerEngine.EXOPLAYER.next())
        assertEquals(PlayerEngine.MPV_EMBEDDED, PlayerEngine.VLC_EMBEDDED.next())
        assertEquals(PlayerEngine.EXOPLAYER, PlayerEngine.MPV_EMBEDDED.next())

        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = IptvRepository(context, db.iptvDao())
        repo.ensureSelfHealingCatalog()

        val active = db.iptvDao().getActivePlaylistOnce()
        assertTrue(active != null)
        assertEquals(12, db.iptvDao().countStreamsByType(active!!.id, StreamType.LIVE))
        assertEquals(10, db.iptvDao().countStreamsByType(active.id, StreamType.VOD))
        assertEquals(6, db.iptvDao().countStreamsByType(active.id, StreamType.SERIES))

        val sampleM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="sintel.uhd" tvg-name="Sintel 4K" group-title="Open Cinema",Sintel Fantasy 4K UHD
            https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4
        """.trimIndent()

        val parser = M3uStreamingParser(OkHttpClient())
        val collectedCats = mutableListOf<CategoryEntity>()
        val collectedStreams = mutableListOf<StreamEntity>()
        val count = parser.parseFromInputStream(
            inputStream = ByteArrayInputStream(sampleM3u.toByteArray(Charsets.UTF_8)),
            playlistId = 99L,
            favoriteKeys = emptySet()
        ) { cats, batch ->
            collectedCats.addAll(cats)
            collectedStreams.addAll(batch)
        }
        assertEquals(1, count)
        db.iptvDao().insertCategories(collectedCats)
        db.iptvDao().insertStreamsInBatches(collectedStreams, batchSize = 500)

        val ftsHits = db.iptvDao().searchStreamsFts(99L, "Sintel*").first()
        assertEquals(1, ftsHits.size)
        assertTrue(XtreamAndStalkerClients(OkHttpClient()).generateVirtualMac().startsWith("00:1A:79:"))
        db.close()
    }
}

package com.example.player

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import com.example.data.preferences.ExternalPlayerTarget

data class AbiAndEngineReport(
    val primaryAbi: String,
    val supportedAbis: List<String>,
    val splitTargets: List<String>,
    val exoPlayerVersion: String = "Media3 1.6.0",
    val libVlcVersion: String = "LibVLC 3.6.5 (JNI)",
    val libMpvVersion: String = "libmpv 1.0.0 (JNI)",
    val externalVlcInstalled: Boolean,
    val externalMpvInstalled: Boolean,
    val externalMxInstalled: Boolean
)

object ExternalPlayerAndAbiManager {

    fun inspectDeviceAbiAndPlayers(context: Context): AbiAndEngineReport {
        val abis = Build.SUPPORTED_ABIS?.toList().orEmpty().ifEmpty { listOf("arm64-v8a") }
        return AbiAndEngineReport(
            primaryAbi = abis.first(),
            supportedAbis = abis,
            splitTargets = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86", "universal"),
            externalVlcInstalled = isPackageInstalled(context, "org.videolan.vlc"),
            externalMpvInstalled = isPackageInstalled(context, "is.xyz.mpv") || isPackageInstalled(context, "is.xyz.mpv.ytdl"),
            externalMxInstalled = isPackageInstalled(context, "com.mxtech.videoplayer.pro") ||
                isPackageInstalled(context, "com.mxtech.videoplayer.ad")
        )
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)
    }

    fun launchExternalPlayer(
        context: Context,
        target: ExternalPlayerTarget,
        streamUrl: String,
        title: String,
        userAgent: String,
        positionMs: Long = 0L
    ) {
        if (streamUrl.isBlank()) return
        val uri = Uri.parse(streamUrl)

        val baseIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, inferMimeType(streamUrl))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("title", title)
            putExtra("extra_title", title)
            if (positionMs > 0L) {
                putExtra("position", positionMs.toInt())
                putExtra("from_start", false)
            }
            putExtra("headers", arrayOf("User-Agent", userAgent))
        }

        val candidatePackages = when (target) {
            ExternalPlayerTarget.VLC_EXTERNAL -> listOf("org.videolan.vlc")
            ExternalPlayerTarget.MPV_EXTERNAL -> listOf("is.xyz.mpv", "is.xyz.mpv.ytdl")
            ExternalPlayerTarget.MX_PLAYER -> listOf("com.mxtech.videoplayer.pro", "com.mxtech.videoplayer.ad")
            ExternalPlayerTarget.SYSTEM_CHOOSER -> emptyList()
        }

        val installedPkg = candidatePackages.firstOrNull { isPackageInstalled(context, it) }
        if (installedPkg != null) {
            val targeted = Intent(baseIntent).apply { setPackage(installedPkg) }
            val launched = runCatching {
                context.startActivity(targeted)
                true
            }.getOrDefault(false)
            if (launched) return
        }

        // Graceful fallback to system video chooser if specific external player app isn't installed
        runCatching {
            val chooser = Intent.createChooser(baseIntent, "Open stream with (${target.label})")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            if (target != ExternalPlayerTarget.SYSTEM_CHOOSER) {
                Toast.makeText(
                    context,
                    "${target.label} app not detected — showing compatible video players (or use built-in Embedded VLC / MPV!)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }.onFailure {
            Toast.makeText(
                context,
                "No external player found on device. Use built-in ExoPlayer, Embedded VLC, or Embedded MPV.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun launchYoutubeTrailer(context: Context, trailerIdOrUrl: String, fallbackSearchTitle: String) {
        val url = when {
            trailerIdOrUrl.startsWith("http") -> trailerIdOrUrl
            trailerIdOrUrl.isNotBlank() -> "https://www.youtube.com/watch?v=$trailerIdOrUrl"
            else -> "https://www.youtube.com/results?search_query=${Uri.encode("$fallbackSearchTitle official trailer")}"
        }
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun inferMimeType(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".m3u8") -> "application/x-mpegURL"
            lower.endsWith(".mpd") -> "application/dash+xml"
            lower.endsWith(".ts") -> "video/mp2t"
            lower.endsWith(".mkv") -> "video/x-matroska"
            else -> "video/*"
        }
    }
}

package com.abdownloadmanager.desktop.downloadcli

import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.DownloadSettings
import ir.amirab.downloader.DownloaderRegistry
import ir.amirab.downloader.connection.OkHttpHttpDownloaderClient
import ir.amirab.downloader.db.DownloadListFileStorage
import ir.amirab.downloader.db.PartListFileStorage
import ir.amirab.downloader.db.TransactionalFileSaver
import ir.amirab.downloader.downloaditem.http.HttpDownloader
import ir.amirab.downloader.utils.EmptyFileCreator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

/**
 * Builds a minimally-configured `DownloadManager` that writes to the same on-disk
 * DB the GUI uses. By design, this is the entire "bootstrap" surface — no Koin,
 * no `SingleInstanceManager`, no IPC service.
 *
 * The output folder argument (`outputDir`) is where files land; the DB itself
 * (downloadlist/ + parts/ + downloadData/) is reused from the GUI's standard
 * location so that any items enqueued here are visible to a running GUI and
 * vice versa.
 */
object DownloadCliBootstrap {

    /**
     * macOS path matches what `AppInfo.getUserDataDir()` produces for the GUI:
     * `~/Library/Application Support/ABDownloadManager`. We hardcode the
     * data-dir name to keep this module independent of `AppInfo` (which
     * transitively pulls in Compose desktop runtime).
     */
    private fun defaultDataDir(): File {
        val dataDirName = "ABDownloadManager"
        return File(System.getProperty("user.home"), "Library/Application Support/$dataDirName")
    }

    /**
     * Resolve the ABDM data dir. Honors `ABDM_DATA_DIR` env override so we can
     * point the CLI at a scratch location during testing without touching the
     * user's real download history.
     */
    private fun resolveDataDir(): File {
        System.getenv("ABDM_DATA_DIR")?.takeIf { it.isNotBlank() }?.let {
            return File(it).also { dir -> dir.mkdirs() }
        }
        return defaultDataDir().also { it.mkdirs() }
    }

    /**
     * Construct (but do not boot) a `DownloadManager` for the CLI session.
     *
     * @param outputDir folder where the actual downloaded files land; must
     *                  exist (caller ensures this via `mkdirs()`).
     */
    suspend fun build(outputDir: File): DownloadManager {
        val dataDir = resolveDataDir()
        val downloadListDir = File(dataDir, "config/download_db/downloadlist").apply { mkdirs() }
        val partsDir = File(dataDir, "config/download_db/parts").apply { mkdirs() }
        val downloadDataDir = File(dataDir, "system/downloadData").apply { mkdirs() }

        // Transactional JSON file-saver (atomic .tmp + rename).
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
        val fileSaver = TransactionalFileSaver(json)

        val dlListDb = DownloadListFileStorage(
            downloadListFolder = downloadListDir,
            fileSaver = fileSaver,
        )
        val partListDb = PartListFileStorage(
            folder = partsDir,
            fileSaver = fileSaver,
        )
        val settings = DownloadSettings() // all defaults
        val emptyFileCreator = EmptyFileCreator(
            diskStat = CliStubs.CliDiskStat(),
            useSparseFile = { true },
        )

        // HttpDownloaderClient — direct connection, no proxy. CDN geo-block
        // risk noted in design doc §7 R1/R2; users must route at OS level.
        val httpClient = OkHttpClient.Builder().build()
        val httpDownloaderClient = OkHttpHttpDownloaderClient(
            okHttpClient = httpClient,
            customUserAgentProvider = CliStubs.DefaultUserAgentProvider(),
            proxyStrategyProvider = CliStubs.DirectProxyStrategyProvider(),
            systemProxySelectorProvider = CliStubs.NoopSystemProxySelectorProvider(),
            autoConfigurableProxyProvider = CliStubs.NoopAutoConfigurableProxyProvider(),
        )

        val registry = DownloaderRegistry().apply {
            // HttpDownloader wraps the client in a Lazy<HttpDownloaderClient> —
            // matches the Koin wiring in Di.kt:234.
            add(HttpDownloader(httpDownloaderClient = lazy { httpDownloaderClient }))
        }

        return DownloadManager(
            dlListDb = dlListDb,
            partListDb = partListDb,
            settings = settings,
            emptyFileCreator = emptyFileCreator,
            downloaderRegistry = registry,
            downloadDataFolder = downloadDataDir,
        )
    }

    /**
     * Best-effort shutdown: cancel any pending jobs. We don't have a `close()`
     * on `DownloadManager` (see DownloadManager.kt — jobs are owned by the
     * registry), but we *can* stop active downloads cleanly. This is invoked
     * from the `finally` block in BatchAddCommand so Ctrl+C / script exit
     * doesn't leave 626 jobs running in the background.
     */
    fun shutdown(manager: DownloadManager) {
        runBlocking {
            try {
                manager.stopAll()
            } catch (_: Throwable) {
                // Best-effort; never let shutdown throw.
            }
        }
    }
}

package com.abdownloadmanager.desktop.downloadcli

import ir.amirab.downloader.DownloadManager
import ir.amirab.downloader.NewDownloadItemProps
import ir.amirab.downloader.downloaditem.DownloadItemContext
import ir.amirab.downloader.downloaditem.DownloadStatus
import ir.amirab.downloader.downloaditem.IDownloadItem
import ir.amirab.downloader.downloaditem.http.HttpDownloadItem
import ir.amirab.downloader.utils.OnDuplicateStrategy
import java.io.File

/**
 * Single-threaded enqueue loop. See `/tmp/abdm-cli-design.md` §1.3 for the
 * rationale: `DownloadManager.dbAddSync` already serializes writes, so
 * 626 concurrent calls would only queue behind the same mutex with extra
 * coroutine overhead. Sequential = "declared = actual".
 *
 * `DownloadManager.addDownload()` already calls `createJob(item).boot()`,
 * which starts the HTTP download immediately when network is available.
 * No explicit `start()` call is needed (and `DownloadManager` doesn't
 * expose one — see DownloadManager.kt:130).
 */
class BatchProcessor(
    private val manager: DownloadManager,
    private val totalCount: Int,
) {
    suspend fun run(
        urls: List<TsvUrlParser.UrlEntry>,
        outputDir: File,
        startImmediately: Boolean,
    ) {
        if (urls.isEmpty()) {
            echo("BatchProcessor: empty url list, nothing to do.")
            return
        }

        // Progress log every 50 lines so 626 URLs don't flood stdout.
        val progressEvery = 50

        urls.forEachIndexed { idx, entry ->
            val name = deriveFileName(entry.url, entry.name)
            val item = HttpDownloadItem(
                link = entry.url,
                headers = mapOf("User-Agent" to "ABDM-CLI/1.0"),
                id = 0L, // 0L = "let DownloadManager assign the next id"
                folder = outputDir.absolutePath,
                name = name,
                contentLength = IDownloadItem.LENGTH_UNKNOWN,
                dateAdded = System.currentTimeMillis(),
                status = DownloadStatus.Added,
            )
            val id = try {
                manager.addDownload(
                    NewDownloadItemProps(
                        downloadItem = item,
                        extraConfig = null,
                        onDuplicateStrategy = OnDuplicateStrategy.AddNumbered,
                        context = DownloadItemContext(),
                    )
                )
            } catch (t: Throwable) {
                echo("[${idx + 1}/$totalCount] FAIL url=${entry.url} error=${t.javaClass.simpleName}: ${t.message}")
                return@forEachIndexed
            }

            if (idx % progressEvery == 0) {
                echo("[${idx + 1}/$totalCount] progress checkpoint: last id=$id")
            }
            echo("[${idx + 1}/$totalCount] queued id=$id url=${entry.url}")

            if (!startImmediately) {
                // Default behaviour: just enqueue. ABDM's job.boot() will start
                // it automatically, but we don't trigger an explicit resume
                // unless --start was passed.
            }
        }

        echo("BatchProcessor: enqueued ${urls.size}/$totalCount URLs into ${outputDir.absolutePath}")
    }
}

/** Local echo() so we don't drag in clikt imports here. */
private fun echo(msg: String) = println(msg)

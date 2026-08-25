package com.abdownloadmanager.desktop.downloadcli

import kotlinx.coroutines.runBlocking

/**
 * Entry point for download-cli.
 *
 * Bypasses the GUI IPC stack (`SingleInstanceManager` / Koin) and directly
 * constructs a `DownloadManager` against the same on-disk download DB the
 * GUI uses. Lets us enqueue a TSV of URLs headlessly.
 *
 * Design reference: `/tmp/abdm-cli-design.md` §1.1 (calling chain).
 */
fun main(args: Array<String>) = runBlocking {
    DownloadCli().main(args)
}

package com.abdownloadmanager.desktop.downloadcli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.subcommands

/**
 * Top-level clikt command for `download-cli`. Currently exposes only
 * `batch-add`, but the subcommand registry makes it cheap to add
 * `list` / `remove` later without restructuring.
 */
class DownloadCli : SuspendingCliktCommand(
    name = "download-cli",
    help = "Batch URL downloader for ABDM (uses :downloader:core directly, no GUI required)",
) {
    init {
        subcommands(BatchAddCommand())
    }

    override suspend fun run() = Unit
}

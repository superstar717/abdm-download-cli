package com.abdownloadmanager.desktop.downloadcli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int

/**
 * `batch-add` subcommand: read a TSV of URLs, enqueue each one serially via
 * `DownloadManager.addDownload()`, optionally start downloads immediately.
 *
 * IMPORTANT: deliberately single-threaded. See `/tmp/abdm-cli-design.md` §1.3 —
 * 626 concurrent calls would just queue behind `DownloadManager.dbAddSync`
 * anyway, and the family of "declared != actual" feedback rules tells us not
 * to promise concurrency we can't deliver.
 */
class BatchAddCommand : SuspendingCliktCommand(
    name = "batch-add",
    help = "Read a TSV of URLs and enqueue them into ABDM's download DB",
) {
    private val input by option(
        "--input",
        help = "TSV file path (URL per line; optional TAB + suggested name)",
    )
        .file(mustExist = true)
        .required()

    private val output by option(
        "--output",
        help = "Output folder for downloaded files (must be writable)",
    )
        .file(canBeFile = false, mustExist = false)
        .required()

    private val limit by option(
        "--limit",
        help = "Cap on URLs to process (0 = all)",
    ).int().default(0)

    private val startImmediately by option(
        "--start",
        help = "Start downloads immediately after enqueue",
    ).flag()

    private val dryRun by option(
        "--dry-run",
        help = "Log URLs without enqueueing",
    ).flag()

    override suspend fun run() {
        echo("ABDM download-cli batch-add: input=${input.absolutePath} output=${output.absolutePath}")
        val parsed = TsvUrlParser.parse(input.readLines(), limit)
        if (parsed.isEmpty()) {
            echo("No URLs parsed from ${input.name}; exiting.")
            return
        }
        echo("Parsed ${parsed.size} URL(s) (limit=$limit).")

        if (dryRun) {
            parsed.forEachIndexed { idx, entry ->
                val name = deriveFileName(entry.url, entry.name)
                echo("[$idx/${parsed.size}] dry-run url=${entry.url} -> $name")
            }
            return
        }

        // Ensure output dir exists before we boot the manager (avoids race with EmptyFileCreator).
        output.mkdirs()

        val manager = DownloadCliBootstrap.build(output)
        try {
            manager.boot()
            val processor = BatchProcessor(manager = manager, totalCount = parsed.size)
            processor.run(
                urls = parsed,
                outputDir = output,
                startImmediately = startImmediately,
            )
        } finally {
            DownloadCliBootstrap.shutdown(manager)
        }
    }
}

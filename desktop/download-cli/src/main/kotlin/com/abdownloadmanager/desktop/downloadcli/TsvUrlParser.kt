package com.abdownloadmanager.desktop.downloadcli

/**
 * TSV parser for the input file format documented in
 * `/tmp/abdm-cli-design.md` §1.2:
 *
 *     URL<TAB>SUGGESTED_NAME    (SUGGESTED_NAME is optional)
 *     # comments start with '#'
 *
 * Lines starting with `#` are treated as comments and skipped.
 * Empty lines and lines whose URL fails `HttpUrlUtils.isValidUrl` are dropped.
 */
object TsvUrlParser {

    data class UrlEntry(val url: String, val name: String?)

    /**
     * @param lines raw lines from the TSV file
     * @param limit  max entries to keep (0 = no cap)
     */
    fun parse(lines: List<String>, limit: Int): List<UrlEntry> {
        val valid: List<UrlEntry> = lines.asSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                val cols = line.split('\t', limit = 2)
                UrlEntry(
                    url = cols[0].trim(),
                    name = cols.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
            .filter { entry -> isLikelyHttpUrl(entry.url) }
            .toList()
        return if (limit > 0) valid.take(limit) else valid
    }

    /**
     * Lightweight URL validator — we deliberately avoid pulling in
     * `ir.amirab.util.HttpUrlUtils` from `:shared:utils` because that module
     * transitively drags Compose/desktop runtime. Schema check only:
     * starts with http(s):// and has a non-empty host.
     */
    private fun isLikelyHttpUrl(s: String): Boolean {
        if (s.length < 8) return false
        val prefix = s.substring(0, 7).lowercase()
        if (prefix != "http://" && !s.lowercase().startsWith("https://")) return false
        val rest = s.substring(s.indexOf("://") + 3)
        // host part up to first '/' or '?'
        val hostEnd = rest.indexOfAny(charArrayOf('/', '?', '#'))
        val host = if (hostEnd == -1) rest else rest.substring(0, hostEnd)
        return host.isNotEmpty()
    }
}

/**
 * Derive a sensible filename for a URL: prefer the user-supplied `suggested`,
 * else take the last path segment, stripping query/fragment. Fallback to a
 * hash-derived name so we never produce an empty string (which would fail
 * ABDM's `FileNameValidator`).
 */
fun deriveFileName(url: String, suggested: String?): String =
    suggested?.takeIf { it.isNotEmpty() }
        ?: url.substringAfterLast('/')
            .substringBefore('?')
            .substringBefore('#')
            .ifEmpty { "download_${url.hashCode()}" }

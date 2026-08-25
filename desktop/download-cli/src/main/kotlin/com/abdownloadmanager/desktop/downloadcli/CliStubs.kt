package com.abdownloadmanager.desktop.downloadcli

import ir.amirab.downloader.connection.UserAgentProvider
import ir.amirab.downloader.connection.proxy.AutoConfigurableProxyProvider
import ir.amirab.downloader.connection.proxy.ProxyStrategy
import ir.amirab.downloader.connection.proxy.ProxyStrategyProvider
import ir.amirab.downloader.connection.proxy.SystemProxySelectorProvider
import ir.amirab.downloader.utils.IDiskStat
import java.io.File
import java.net.ProxySelector

/**
 * Stub implementations of the small interfaces that `OkHttpHttpDownloaderClient`
 * depends on. The full ABDM desktop app wires these up via Koin with sophisticated
 * proxy/UA logic (ProxyManager, UserAgentProviderFromSettings, etc.). For a
 * headless CLI we want the simplest valid behaviour:
 *
 * - Direct connection (no proxy) — user must run with a system-level proxy if
 *   their CDN is geo-blocked (R1/R2 risk in design doc §7).
 * - Default OkHttp User-Agent — fine for non-browser-validating CDNs.
 * - `path.freeSpace` for disk-stat — matches `DesktopDiskStat` exactly.
 *
 * Stubs are kept in one file because they're trivial and have no behavior of
 * their own — collectively they form the "no extra desktop-app plumbing"
 * boundary this module is designed around.
 */
internal object CliStubs {

    /** Returns "Direct" for every URL — i.e. no proxy. */
    class DirectProxyStrategyProvider : ProxyStrategyProvider {
        override fun getProxyStrategyFor(url: String): ProxyStrategy = ProxyStrategy.Direct
    }

    /** Returns null (no system proxy selector). */
    class NoopSystemProxySelectorProvider : SystemProxySelectorProvider {
        override fun getSystemProxySelector(): ProxySelector? = null
    }

    /** Auto-config script: none. */
    class NoopAutoConfigurableProxyProvider : AutoConfigurableProxyProvider {
        override fun getAutoConfigurableProxy(uri: String): ProxySelector? = null
    }

    /** Returns null → `OkHttpHttpDownloaderClient` falls back to its default UA. */
    class DefaultUserAgentProvider : UserAgentProvider {
        override fun getUserAgent(): String? = null
    }

    /** Mirrors `DesktopDiskStat` exactly — no need to pull in `shared:app` for this. */
    class CliDiskStat : IDiskStat {
        override fun getRemainingSpace(path: File): Long = path.freeSpace
    }
}

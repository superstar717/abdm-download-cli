plugins {
    id(MyPlugins.kotlin)
    id(Plugins.Kotlin.serialization)
}

dependencies {
    // Real ABDM downloader core — what we actually want to drive.
    implementation(project(":downloader:core"))
    implementation(project(":shared:utils"))

    // CLI plumbing (clikt is the same dependency the existing ABDM Cli.kt uses).
    implementation(libs.clikit)

    // OkHttp — pulled in transitively by :downloader:core anyway, but
    // declared explicitly so the CLI module compiles standalone.
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.okhttp.okhttp)
    implementation(libs.kotlin.serialization.json)
}

application {
    mainClass = "com.abdownloadmanager.desktop.downloadcli.DownloadCliMainKt"
}

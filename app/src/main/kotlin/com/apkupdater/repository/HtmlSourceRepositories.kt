package com.apkupdater.repository

import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.apkupdater.data.ui.AppInstalled
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.data.ui.ApkComboSource
import com.apkupdater.data.ui.Link
import com.apkupdater.data.ui.Source
import com.apkupdater.data.ui.UptodownSource
import io.github.g00fy2.versioncompare.Version
import com.apkupdater.util.retryTransiently
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder

private const val APK_COMBO_BASE_URL = "https://apkcombo.com"
private const val UPTODOWN_BASE_URL = "https://en.uptodown.com"
private const val USER_AGENT = "Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/121.0 Mobile Safari/537.36"
private const val SCRAPE_CONCURRENCY = 8
private val INSTALLABLE_EXTENSIONS = listOf(".apk", ".apkm", ".apks", ".xapk")

internal data class ScrapedApp(
    val name: String,
    val packageName: String,
    val version: String,
    val sourceUrl: String,
    val iconUrl: String = "",
    val downloadUrl: String? = null
)

internal fun isNewerScrapedVersion(version: String, currentVersion: String): Boolean =
    currentVersion.isBlank() || runCatching { Version(version) > Version(currentVersion) }
        .getOrDefault(version != currentVersion)

private fun ScrapedApp.toAppUpdate(current: AppInstalled?, source: Source) = AppUpdate(
    name = name,
    packageName = packageName,
    version = version,
    oldVersion = current?.version ?: "?",
    versionCode = 0L,
    oldVersionCode = current?.versionCode ?: 0L,
    source = source,
    iconUri = if (current == null) iconUrl.takeIf(String::isNotBlank)?.toUri() ?: Uri.EMPTY else Uri.EMPTY,
    link = downloadUrl?.let(Link::Url) ?: Link.Empty,
    sourceUrl = sourceUrl
)

class ApkComboRepository {

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val updates = mutableListOf<AppUpdate>()
        var firstFailure: Throwable? = null
        if (apps.isEmpty()) emit(emptyList())
        apps.chunked(SCRAPE_CONCURRENCY).forEach { chunk ->
            val attempts = coroutineScope {
                chunk.map { app ->
                    async(Dispatchers.IO) { app to runCatching { checkApp(app) } }
                }.awaitAll()
            }
            attempts.forEach { (app, result) ->
                result.onSuccess { it?.let(updates::add) }
                    .onFailure {
                        firstFailure = firstFailure ?: it
                        Log.e("ApkComboRepository", "Error checking ${app.packageName}.", it)
                    }
            }
            emit(updates.toList())
        }
        firstFailure?.let { if (updates.isEmpty()) throw it }
    }.retryTransiently().catch {
        Log.e("ApkComboRepository", "Error looking for updates.", it)
        throw it
    }

    suspend fun search(text: String) = flow {
        val result = searchUrls(text).mapNotNull { url ->
            runCatching {
                parseApkComboDetails(fetch(url), url)?.let { details ->
                    details.copy(downloadUrl = parseApkComboDownloadUrl(fetch("${details.sourceUrl.trimEnd('/')}/download/apk")))
                        .toAppUpdate(null, ApkComboSource)
                }
            }.onFailure { Log.e("ApkComboRepository", "Error parsing $url.", it) }.getOrNull()
        }
        emit(Result.success(result))
    }.catch {
        Log.e("ApkComboRepository", "Error searching.", it)
        emit(Result.failure(it))
    }

    private fun checkApp(app: AppInstalled): AppUpdate? {
        val sourceUrl = findDetailUrl(app.packageName) ?: return null
        val details = parseApkComboDetails(fetch(sourceUrl), sourceUrl) ?: return null
        if (details.packageName != app.packageName || !isNewerScrapedVersion(details.version, app.version)) return null
        val downloadUrl = parseApkComboDownloadUrl(fetch("${details.sourceUrl.trimEnd('/')}/download/apk"))
        return details.copy(downloadUrl = downloadUrl).toAppUpdate(app, ApkComboSource)
    }

    private fun findDetailUrl(packageName: String): String? = searchUrls(packageName)
        .firstOrNull { Uri.parse(it).pathSegments.lastOrNull().equals(packageName, ignoreCase = true) }

    private fun searchUrls(text: String): List<String> = fetch("$APK_COMBO_BASE_URL/search/${Uri.encode(text)}/")
        .select("a[href]")
        .mapNotNull { absoluteUrl(it.attr("href"), APK_COMBO_BASE_URL) }
        .filter { url ->
            val uri = Uri.parse(url)
            uri.host.equals("apkcombo.com", ignoreCase = true) &&
                uri.pathSegments.size >= 2 &&
                uri.pathSegments.last().contains('.')
        }
        .distinct()
        .take(20)

    private fun fetch(url: String): Document = Jsoup.connect(url)
        .userAgent(USER_AGENT)
        .timeout(30_000)
        .followRedirects(true)
        .get()
}

internal fun parseApkComboDetails(document: Document, fallbackUrl: String): ScrapedApp? {
    val sourceUrl = document.selectFirst("link[rel=canonical]")?.attr("href")
        ?.takeIf(String::isNotBlank)
        ?: document.selectFirst("meta[property=og:url]")?.attr("content")
            ?.takeIf(String::isNotBlank)
        ?: fallbackUrl
    val packageName = sourceUrl.substringBefore('?').trimEnd('/').substringAfterLast('/')
    val version = document.selectFirst("div.version")?.text()?.trim().orEmpty()
    if (packageName.isBlank() || version.isBlank()) return null
    return ScrapedApp(
        name = document.selectFirst("div.app_name")?.text()?.trim().orEmpty().ifBlank { packageName },
        packageName = packageName,
        version = version,
        sourceUrl = sourceUrl,
        iconUrl = document.selectFirst("meta[name=thumbnail]")?.attr("content")
            .orEmpty()
            .ifBlank { document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty() }
    )
}

internal fun parseApkComboDownloadUrl(document: Document, supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList()): String? {
    val variants = document.select("#variants-tab li").flatMap { item ->
        val architecture = item.selectFirst("code")?.text().orEmpty()
        item.select("a[href]").mapNotNull { link ->
            val url = absoluteUrl(link.attr("href"), APK_COMBO_BASE_URL)
            url?.takeIf { isInstallableUrl(it) }?.let { architecture to it }
        }
    }
    if (variants.isEmpty()) return null
    return variants.firstOrNull { it.first.contains("universal", true) || it.first.contains("noarch", true) }?.second
        ?: supportedAbis.asSequence().mapNotNull { abi -> variants.firstOrNull { it.first.contains(abi, true) }?.second }.firstOrNull()
        ?: variants.first().second
}

class UptodownRepository {

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val updates = mutableListOf<AppUpdate>()
        var firstFailure: Throwable? = null
        if (apps.isEmpty()) emit(emptyList())
        apps.chunked(SCRAPE_CONCURRENCY).forEach { chunk ->
            val attempts = coroutineScope {
                chunk.map { app ->
                    async(Dispatchers.IO) { app to runCatching { checkApp(app) } }
                }.awaitAll()
            }
            attempts.forEach { (app, result) ->
                result.onSuccess { it?.let(updates::add) }
                    .onFailure {
                        firstFailure = firstFailure ?: it
                        Log.e("UptodownRepository", "Error checking ${app.packageName}.", it)
                    }
            }
            emit(updates.toList())
        }
        firstFailure?.let { if (updates.isEmpty()) throw it }
    }.retryTransiently().catch {
        Log.e("UptodownRepository", "Error looking for updates.", it)
        throw it
    }

    suspend fun search(text: String) = flow {
        val result = searchUrls(text).mapNotNull { url ->
            runCatching { parseUptodownDetails(fetch("${url.trimEnd('/')}/download"), url) }
                .onFailure { Log.e("UptodownRepository", "Error parsing $url.", it) }
                .getOrNull()
                ?.toAppUpdate(null, UptodownSource)
        }
        emit(Result.success(result))
    }.catch {
        Log.e("UptodownRepository", "Error searching.", it)
        emit(Result.failure(it))
    }

    private fun checkApp(app: AppInstalled): AppUpdate? {
        val sourceUrl = findAppUrl(app.packageName) ?: return null
        val details = parseUptodownDetails(fetch("${sourceUrl.trimEnd('/')}/download"), sourceUrl) ?: return null
        if (details.packageName != app.packageName || !isNewerScrapedVersion(details.version, app.version)) return null
        return details.toAppUpdate(app, UptodownSource)
    }

    private fun findAppUrl(packageName: String): String? = searchUrls(packageName).firstOrNull { url ->
        runCatching { parseUptodownDetails(fetch("${url.trimEnd('/')}/download"), url)?.packageName == packageName }
            .getOrDefault(false)
    }

    private fun searchUrls(text: String): List<String> = fetch(
        "$UPTODOWN_BASE_URL/android/search?query=${Uri.encode(text)}"
    ).select("a[href]")
        .mapNotNull { absoluteUrl(it.attr("href"), UPTODOWN_BASE_URL) }
        .filter { url ->
            val uri = Uri.parse(url)
            uri.host.orEmpty().endsWith(".uptodown.com", true) && uri.pathSegments == listOf("android")
        }
        .distinct()
        .take(20)

    private fun fetch(url: String): Document = Jsoup.connect(url)
        .userAgent(USER_AGENT)
        .timeout(30_000)
        .followRedirects(true)
        .get()
}

internal fun parseUptodownDetails(document: Document, fallbackUrl: String): ScrapedApp? {
    val packageName = document.select("#technical-information tr").firstOrNull { row ->
        row.selectFirst("th")?.text()?.equals("Package Name", ignoreCase = true) == true
    }?.select("td")?.lastOrNull()?.text()?.trim().orEmpty()
    val version = document.selectFirst("meta[itemprop=softwareVersion]")?.attr("content")?.trim()
        .orEmpty()
        .ifBlank { document.select("div.version").firstOrNull()?.text()?.trim().orEmpty() }
    if (packageName.isBlank() || version.isBlank()) return null
    val button = document.selectFirst("#detail-download-button")
    val token = button?.attr("data-url-ext").orEmpty().ifBlank { button?.attr("data-url").orEmpty() }
    return ScrapedApp(
        name = document.selectFirst("#detail-app-name")?.text()?.trim().orEmpty().ifBlank { packageName },
        packageName = packageName,
        version = version,
        sourceUrl = fallbackUrl,
        iconUrl = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty(),
        downloadUrl = token.takeIf(String::isNotBlank)?.let {
            if (it.startsWith("http", true)) it else "https://dw.uptodown.com/dwn/${it.trim('/')}"
        }
    )
}

private fun isInstallableUrl(url: String): Boolean {
    val normalized = URLDecoder.decode(url, Charsets.UTF_8.name()).lowercase()
    return INSTALLABLE_EXTENSIONS.any { normalized.contains(it) }
}

private fun absoluteUrl(value: String, baseUrl: String): String? = value
    .takeIf(String::isNotBlank)
    ?.let {
        when {
            it.startsWith("//") -> "https:$it"
            it.startsWith("/") -> "$baseUrl$it"
            else -> it
        }
    }

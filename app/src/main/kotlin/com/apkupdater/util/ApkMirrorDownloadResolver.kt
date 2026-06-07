package com.apkupdater.util

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

internal data class ResolvedDownloadUrl(
    val url: String,
    val referer: String? = null
)

internal object ApkMirrorDownloadResolver {
    private const val HOST = "www.apkmirror.com"
    private const val BASE_URL = "https://www.apkmirror.com"
    private const val DOWNLOAD_PATH = "/wp-content/themes/APKMirror/download.php"

    fun isApkMirrorUrl(url: String) = runCatching {
        url.toHttpUrl().host == HOST
    }.getOrDefault(false)

    fun resolve(client: OkHttpClient, url: String): ResolvedDownloadUrl {
        if (!isApkMirrorUrl(url) || url.contains(DOWNLOAD_PATH)) {
            return ResolvedDownloadUrl(url)
        }

        val firstPage = fetchDocument(client, url)
        val downloadPageUrl = when {
            url.contains("/download/") -> url
            else -> firstPage.findDownloadPageUrl()
        } ?: return ResolvedDownloadUrl(url)

        val downloadPage = if (downloadPageUrl == url) {
            firstPage
        } else {
            fetchDocument(client, downloadPageUrl, referer = url)
        }

        return downloadPage.findDirectDownloadUrl()
            ?.let { ResolvedDownloadUrl(it, downloadPageUrl) }
            ?: ResolvedDownloadUrl(downloadPageUrl, url)
    }

    fun Document.findDownloadPageUrl(): String? =
        select("a[href]")
            .asSequence()
            .mapNotNull { it.absoluteHref() }
            .firstOrNull { it.contains("/download/?") || it.endsWith("/download/") }

    fun Document.findDirectDownloadUrl(): String? {
        select("a[href]")
            .asSequence()
            .mapNotNull { it.absoluteHref() }
            .firstOrNull { it.contains(DOWNLOAD_PATH) }
            ?.let { return it }

        val id = selectFirst("input[name=id]")?.attr("value")?.takeIf { it.isNotBlank() } ?: return null
        val key = selectFirst("input[name=key]")?.attr("value")?.takeIf { it.isNotBlank() } ?: return null

        return "$BASE_URL$DOWNLOAD_PATH".toHttpUrl().newBuilder()
            .addQueryParameter("id", id)
            .addQueryParameter("key", key)
            .build()
            .toString()
    }

    private fun fetchDocument(client: OkHttpClient, url: String, referer: String? = null): Document {
        client.newCall(request(url, referer)).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("APKMirror page request failed: ${response.code}")
            }
            return Jsoup.parse(response.body.string(), url)
        }
    }

    private fun request(url: String, referer: String? = null) = Request.Builder()
        .url(url)
        .apply { referer?.let { header("Referer", it) } }
        .build()

    private fun org.jsoup.nodes.Element.absoluteHref(): String? =
        absUrl("href").ifBlank {
            val href = attr("href")
            when {
                href.startsWith("/") -> "$BASE_URL$href"
                href.startsWith("http://") || href.startsWith("https://") -> href
                else -> ""
            }
        }.takeIf { it.isNotBlank() }
}

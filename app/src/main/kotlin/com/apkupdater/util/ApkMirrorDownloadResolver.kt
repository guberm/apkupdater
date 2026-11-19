package com.apkupdater.util

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

internal data class ResolvedDownloadUrl(
    val url: String,
    val referer: String? = null
)

/** APKMirror served its Cloudflare bot-protection challenge; downloads cannot proceed until it clears. */
internal class ApkMirrorChallengeException(message: String) : IOException(message)

internal object ApkMirrorDownloadResolver {
    private const val HOST = "www.apkmirror.com"
    internal const val BASE_URL = "https://www.apkmirror.com"
    internal const val DOWNLOAD_PATH = "/wp-content/themes/APKMirror/download.php"

    fun isApkMirrorUrl(url: String) = runCatching {
        url.toHttpUrl().host == HOST
    }.getOrDefault(false)

    fun isChallengeResponse(code: Int, header: (String) -> String?, body: String?): Boolean =
        header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true ||
            ((code == 403 || code == 503) && body != null && isChallengeHtml(body))

    fun isChallengeHtml(html: String): Boolean =
        html.contains("Just a moment", ignoreCase = true) ||
            html.contains("challenge-platform", ignoreCase = true)

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
            val body = response.body.string()
            if (!response.isSuccessful || isChallengeResponse(response.code, response.headers::get, body)) {
                throw ApkMirrorChallengeException("APKMirror page request blocked: ${response.code}")
            }
            return Jsoup.parse(body, url)
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

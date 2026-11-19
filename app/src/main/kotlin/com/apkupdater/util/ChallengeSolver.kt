package com.apkupdater.util

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * On-device Cloudflare challenge solver: a WebView solves the interstitial, its cookies (cf_clearance)
 * and user agent are replayed on the app's HTTP client for the same site. Same device and IP as the
 * download, so clearance is accepted without an external FlareSolverr server.
 */
object ChallengeSolver {

	private val cookieStore = ConcurrentHashMap<String, String>()

	@Volatile
	var solvedUserAgent: String? = null
		private set

	private var pending: CompletableDeferred<Boolean>? = null

	val cookieJar = object : CookieJar {
		override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
			if (isApkMirrorHost(url.host)) cookies.forEach { cookieStore[it.name] = it.value }
		}

		override fun loadForRequest(url: HttpUrl): List<Cookie> {
			if (!isApkMirrorHost(url.host) || cookieStore.isEmpty()) return emptyList()
			return cookieStore.map { (name, value) ->
				Cookie.Builder().name(name).value(value).domain("apkmirror.com").path("/").build()
			}
		}

		private fun isApkMirrorHost(host: String) = host == "apkmirror.com" || host.endsWith(".apkmirror.com")
	}

	fun userAgent(fallback: String): String = solvedUserAgent ?: fallback

	/** Launches the WebView solver for [url] and returns true once clearance was captured. Blocking: runs on the IO download workers. */
	fun solve(context: Context, url: String): Boolean = runBlocking {
		val deferred = pending ?: CompletableDeferred<Boolean>().also { pending = it }
		try {
			if (pending === deferred) {
				context.startActivity(Intent(context, ChallengeSolverActivity::class.java).apply {
					addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
					putExtra(ChallengeSolverActivity.EXTRA_URL, solveUrl(url))
				})
			}
			withTimeout(90_000) { deferred.await() }
		} catch (e: Exception) {
			false
		} finally {
			if (pending === deferred) pending = null
		}
	}

	fun onSolved(userAgent: String, cookieHeader: String) {
		solvedUserAgent = userAgent
		cookieHeader.split(";").forEach { part ->
			val pair = part.trim().split("=", limit = 2)
			if (pair.size == 2 && pair[0].isNotBlank()) cookieStore[pair[0]] = pair[1]
		}
		pending?.complete(true)
		pending = null
	}

	fun onFailed() {
		pending?.complete(false)
		pending = null
	}

	private fun solveUrl(url: String): String =
		if (url.contains(ApkMirrorDownloadResolver.DOWNLOAD_PATH)) ApkMirrorDownloadResolver.BASE_URL else url
}

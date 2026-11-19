package com.apkupdater.util

import com.apkupdater.prefs.Prefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Optional FlareSolverr backend (https://github.com/Flaresolverr/Flaresolverr): when the user runs a
 * FlareSolverr server (same public IP as the device) and configures its endpoint, APKMirror challenges
 * are solved through it and the resulting cookies + user agent feed the same replay path as the
 * on-device WebView solver.
 */
object FlareSolverrClient : KoinComponent {

	private val prefs: Prefs by inject()
	private val client = OkHttpClient.Builder()
		.connectTimeout(15, TimeUnit.SECONDS)
		.readTimeout(90, TimeUnit.SECONDS)
		.callTimeout(90, TimeUnit.SECONDS)
		.build()

	private fun endpoint() = prefs.flaresolverrUrl.get().trim().trimEnd('/')

	fun isConfigured(): Boolean = endpoint().isNotBlank()

	/** Solves [url] through FlareSolverr and applies its cookies + UA. Blocking: IO download workers only. */
	fun solve(url: String): Boolean = runCatching {
		val body = JSONObject().put("cmd", "request.get").put("url", url).put("maxTimeout", 60_000)
			.toString().toRequestBody("application/json".toMediaType())
		val request = Request.Builder().url("${endpoint()}/v1").post(body).build()
		client.newCall(request).execute().use { response ->
			val json = JSONObject(response.body.string())
			if (json.optString("status") != "ok") return false
			val solution = json.optJSONObject("solution") ?: return false
			val ua = solution.optString("userAgent").takeIf { it.isNotBlank() } ?: return false
			val cookies = solution.optJSONArray("cookies")?.let { array ->
				(0 until array.length()).mapNotNull { i ->
					val cookie = array.optJSONObject(i) ?: return@mapNotNull null
					cookie.optString("name").takeIf { it.isNotBlank() }?.let { it to cookie.optString("value") }
				}
			} ?: return false
			ChallengeSolver.applySolution(ua, cookies)
			true
		}
	}.getOrElse {
		it.printStackTrace()
		false
	}
}

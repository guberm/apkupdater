package com.apkupdater.util

import android.os.Bundle
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import com.apkupdater.R

/** Shows APKMirror in a WebView so its Cloudflare challenge can be solved; captures cookies + UA. */
class ChallengeSolverActivity : ComponentActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val url = intent.getStringExtra(EXTRA_URL) ?: return finish()

		val webView = WebView(this).apply {
			settings.javaScriptEnabled = true
			settings.domStorageEnabled = true
		}
		val root = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(48, 48, 48, 48)
			addView(TextView(this@ChallengeSolverActivity).apply {
				text = getString(R.string.apkmirror_verify_hint)
			})
			addView(webView, LinearLayout.LayoutParams(0, 0, 1f).apply { width = LinearLayout.LayoutParams.MATCH_PARENT; height = 0 })
		}
		setContentView(root)

		CookieManager.getInstance().setAcceptCookie(true)
		CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
		val started = SystemClock.elapsedRealtime()
		val check = object : Runnable {
			override fun run() {
				val cookie = CookieManager.getInstance().getCookie("https://www.apkmirror.com")
				if (cookie != null && cookie.contains("cf_clearance=")) {
					CookieManager.getInstance().flush()
					ChallengeSolver.onSolved(webView.settings.userAgentString, cookie)
					finish()
					return
				}
				if (SystemClock.elapsedRealtime() - started > TIMEOUT_MS) {
					ChallengeSolver.onFailed()
					finish()
					return
				}
				webView.postDelayed(this, POLL_INTERVAL_MS)
			}
		}
		webView.webViewClient = object : WebViewClient() {
			override fun onPageFinished(view: WebView, finishedUrl: String) = check.run()
		}
		webView.loadUrl(url)
		webView.postDelayed(check, POLL_INTERVAL_MS)

		onBackPressedDispatcher.addCallback(this) {
			ChallengeSolver.onFailed()
			finish()
		}
	}

	companion object {
		const val EXTRA_URL = "url"
		private const val TIMEOUT_MS = 60_000L
		private const val POLL_INTERVAL_MS = 1_000L
	}
}

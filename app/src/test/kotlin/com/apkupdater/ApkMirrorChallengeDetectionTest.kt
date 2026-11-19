package com.apkupdater

import com.apkupdater.util.ApkMirrorDownloadResolver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkMirrorChallengeDetectionTest {

	@Test
	fun `challenge pages are detected`() {
		assertTrue(ApkMirrorDownloadResolver.isChallengeHtml("<html><title>Just a moment...</title></html>"))
		assertTrue(ApkMirrorDownloadResolver.isChallengeHtml("<script src=\"/cdn-cgi/challenge-platform/h/b/orchestrate\">"))
		assertFalse(ApkMirrorDownloadResolver.isChallengeHtml("<html><body><a href='/wp-content/themes/APKMirror/download.php?id=1&key=2'>dl</a></body></html>"))
		assertTrue(ApkMirrorDownloadResolver.isChallengeResponse(403, { "challenge" }, null))
		assertTrue(ApkMirrorDownloadResolver.isChallengeResponse(200, { null }, null) == false)
	}

	@Test
	fun `challenge page yields no direct download url`() {
		val challenge = org.jsoup.Jsoup.parse("<html><body>Just a moment...</body></html>", "https://www.apkmirror.com")
		with(ApkMirrorDownloadResolver) {
			assertNull(challenge.findDirectDownloadUrl())
			assertNull(challenge.findDownloadPageUrl())
		}
	}
}

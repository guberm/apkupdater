package com.apkupdater

import com.apkupdater.repository.isNewerScrapedVersion
import com.apkupdater.repository.parseApkComboDetails
import com.apkupdater.repository.parseApkComboDownloadUrl
import com.apkupdater.repository.parseUptodownDetails
import com.apkupdater.util.retryTransiently
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlSourceRepositoriesTest {

    @Test
    fun apkComboVersionExcludesDateAndCategory() {
        val app = parseApkComboDetails(
            Jsoup.parse("""<div class="version">3.0.0 · Jul 31, 2026 · <a>Tools</a></div>"""),
            "https://apkcombo.com/dns/com.appplanex.dnschanger/"
        )
        assertEquals("3.0.0", app?.version)
    }

    @Test
    fun apkComboRejectsWrongArchitectureAndHtmlDisguisedAsApk() {
        val document = Jsoup.parse("""
            <div id="variants-tab">
              <li><code>x86</code><a href="https://example.com/app.apk">x86</a></li>
              <li><code>arm64-v8a</code><a href="https://example.com/page?file=app.apk">HTML</a></li>
            </div>
        """)
        assertNull(parseApkComboDownloadUrl(document, listOf("arm64-v8a")))
    }

    @Test
    fun parsesApkComboDetailsAndSelectsDeviceArchitecture() {
        val app = parseApkComboDetails(
            Jsoup.parse(
                """
                <link rel="canonical" href="https://apkcombo.com/instagram/com.instagram.android/">
                <meta name="thumbnail" content="https://img.example/icon.png">
                <div class="app_name">Instagram</div>
                <div class="version">448.0.0.48.84</div>
                """.trimIndent()
            ),
            "https://apkcombo.com/instagram/com.instagram.android/"
        )
        val url = parseApkComboDownloadUrl(
            Jsoup.parse(
                """
                <div id="variants-tab">
                  <li><code>armeabi-v7a</code><a href="/arm.apk">arm</a></li>
                  <li><code>arm64-v8a</code><a href="/arm64.apks">arm64</a></li>
                </div>
                """.trimIndent()
            ),
            listOf("arm64-v8a")
        )

        assertEquals("Instagram", app?.name)
        assertEquals("com.instagram.android", app?.packageName)
        assertEquals("448.0.0.48.84", app?.version)
        assertEquals("https://img.example/icon.png", app?.iconUrl)
        assertEquals("https://apkcombo.com/arm64.apks", url)
    }

    @Test
    fun parsesUptodownDetailsAndSupportsLegacyDownloadAttribute() {
        val app = parseUptodownDetails(
            Jsoup.parse(
                """
                <meta property="og:image" content="https://img.example/icon.png">
                <meta itemprop="softwareVersion" content="448.0.0.48.84">
                <div id="detail-app-name">Instagram</div>
                <div id="technical-information"><table><tr><th>Package Name</th><td>com.instagram.android</td></tr></table></div>
                <button id="detail-download-button" data-url="token/file.apk"></button>
                """.trimIndent()
            ),
            "https://instagram.en.uptodown.com/android"
        )

        assertEquals("Instagram", app?.name)
        assertEquals("com.instagram.android", app?.packageName)
        assertEquals("448.0.0.48.84", app?.version)
        assertEquals("https://dw.uptodown.com/dwn/token/file.apk", app?.downloadUrl)
    }

    @Test
    fun doesNotInventUptodownDownloadUrlWhenSiteHidesIt() {
        val app = parseUptodownDetails(
            Jsoup.parse(
                """
                <meta itemprop="softwareVersion" content="1.2.3">
                <div id="detail-app-name">Example</div>
                <div id="technical-information"><table><tr><th>Package Name</th><td>com.example</td></tr></table></div>
                <button id="detail-download-button" data-file-id="123"></button>
                """.trimIndent()
            ),
            "https://example.en.uptodown.com/android"
        )

        assertNull(app?.downloadUrl)
    }

    @Test
    fun comparesScrapedVersions() {
        assertTrue(isNewerScrapedVersion("2.0.0", "1.9.9"))
    }

    @Test
    fun retriesTransientRefreshFailure() = runBlocking {
        var attempts = 0
        flow {
            attempts++
            if (attempts < 3) error("temporary")
            emit("ok")
        }.retryTransiently().collect { assertEquals("ok", it) }

        assertEquals(3, attempts)
    }
}

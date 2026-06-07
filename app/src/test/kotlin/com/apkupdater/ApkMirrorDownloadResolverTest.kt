package com.apkupdater

import com.apkupdater.util.ApkMirrorDownloadResolver
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class ApkMirrorDownloadResolverTest {

    @Test
    fun findsDownloadPageUrl() {
        val doc = Jsoup.parse(
            """
            <html>
              <body>
                <a href="/apk/example/app/app-release/app-android-apk-download/download/?key=abc">Download APK</a>
              </body>
            </html>
            """.trimIndent(),
            "https://www.apkmirror.com/apk/example/app/app-release/app-android-apk-download/"
        )

        assertEquals(
            "https://www.apkmirror.com/apk/example/app/app-release/app-android-apk-download/download/?key=abc",
            ApkMirrorDownloadResolver.run { doc.findDownloadPageUrl() }
        )
    }

    @Test
    fun buildsDirectDownloadUrlFromHiddenInputs() {
        val doc = Jsoup.parse(
            """
            <html>
              <body>
                <input type="hidden" name="id" value="12345">
                <input type="hidden" name="key" value="abcdef">
              </body>
            </html>
            """.trimIndent(),
            "https://www.apkmirror.com/apk/example/app/app-release/app-android-apk-download/download/?key=abcdef"
        )

        assertEquals(
            "https://www.apkmirror.com/wp-content/themes/APKMirror/download.php?id=12345&key=abcdef",
            ApkMirrorDownloadResolver.run { doc.findDirectDownloadUrl() }
        )
    }

    @Test
    fun prefersExistingDirectDownloadLink() {
        val doc = Jsoup.parse(
            """
            <html>
              <body>
                <a href="/wp-content/themes/APKMirror/download.php?id=12345&key=abcdef&forcebaseapk=true">Download</a>
              </body>
            </html>
            """.trimIndent(),
            "https://www.apkmirror.com/apk/example/app/app-release/app-android-apk-download/download/?key=abcdef"
        )

        assertEquals(
            "https://www.apkmirror.com/wp-content/themes/APKMirror/download.php?id=12345&key=abcdef&forcebaseapk=true",
            ApkMirrorDownloadResolver.run { doc.findDirectDownloadUrl() }
        )
    }
}

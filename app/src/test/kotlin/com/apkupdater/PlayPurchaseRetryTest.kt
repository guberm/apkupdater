package com.apkupdater

import com.apkupdater.repository.invalidPlayFileUrls
import com.apkupdater.repository.purchasePlayFiles
import com.aurora.gplayapi.data.models.PlayFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayPurchaseRetryTest {

    @Test
    fun detectsEmptyPlayDelivery() {
        assertTrue(invalidPlayFileUrls(emptyList()))
        assertTrue(invalidPlayFileUrls(listOf("")))
        assertFalse(invalidPlayFileUrls(listOf("https://example.com/base.apk")))
    }

    @Test
    fun refreshesAuthOnceWhenGoogleRateLimitsDelivery() {
        var purchaseAttempts = 0
        var authRefreshes = 0

        val files = purchasePlayFiles(
            purchase = {
                purchaseAttempts++
                listOf(
                    PlayFile(
                        "base",
                        "base.apk",
                        if (purchaseAttempts == 1) "" else "https://example.com/base.apk",
                        1L,
                        PlayFile.Type.BASE,
                        "",
                        ""
                    )
                )
            },
            refreshAuth = { authRefreshes++ },
            responseCode = { 429 }
        )

        assertEquals("https://example.com/base.apk", files.single().url)
        assertEquals(2, purchaseAttempts)
        assertEquals(1, authRefreshes)
    }

}

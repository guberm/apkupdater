package com.apkupdater

import com.apkupdater.repository.invalidPlayFileUrls
import com.apkupdater.repository.retryAfterRefresh
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
    fun rotatesAuthTwiceWhenGoogleRateLimitsDelivery() {
        var purchaseAttempts = 0
        var authRefreshes = 0

        val url = retryAfterRefresh(
            action = {
                purchaseAttempts++
                if (purchaseAttempts < 3) "" else "https://example.com/base.apk"
            },
            refresh = { authRefreshes++ },
            shouldRetry = String::isBlank,
            maxRefreshes = { 2 }
        )

        assertEquals("https://example.com/base.apk", url)
        assertEquals(3, purchaseAttempts)
        assertEquals(2, authRefreshes)
    }

}

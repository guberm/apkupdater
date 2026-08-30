package com.apkupdater

import com.apkupdater.repository.invalidPlayFileUrls
import com.apkupdater.repository.retryOnceAfterRefresh
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

        val url = retryOnceAfterRefresh(
            action = {
                purchaseAttempts++
                if (purchaseAttempts == 1) "" else "https://example.com/base.apk"
            },
            refresh = { authRefreshes++ },
            shouldRetry = String::isBlank
        )

        assertEquals("https://example.com/base.apk", url)
        assertEquals(2, purchaseAttempts)
        assertEquals(1, authRefreshes)
    }

}

package com.apkupdater

import com.apkupdater.repository.invalidPlayFileUrls
import com.apkupdater.repository.shouldRefreshPlayAuth
import com.apkupdater.util.play.executeWithPlayRateLimitRetry
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
    fun doesNotRefreshAuthWhenGoogleRateLimitsDelivery() {
        assertFalse(shouldRefreshPlayAuth(429))
        assertTrue(shouldRefreshPlayAuth(200))
    }

    @Test
    fun retriesRateLimitedPlayDeliveryWithBackoff() {
        val responseCodes = ArrayDeque(listOf(429, 429, 200))
        val delays = mutableListOf<Long>()

        val response = executeWithPlayRateLimitRetry(
            method = "GET",
            encodedPath = "/fdfe/delivery",
            responseCode = { it },
            sleep = { delays += it }
        ) {
            responseCodes.removeFirst()
        }

        assertEquals(200, response)
        assertEquals(listOf(1_000L, 2_000L), delays)
    }

    @Test
    fun doesNotRetryRateLimitedPlayPurchase() {
        var attempts = 0

        val response = executeWithPlayRateLimitRetry("POST", "/fdfe/purchase", { it }) {
            attempts++
            429
        }

        assertEquals(429, response)
        assertEquals(1, attempts)
    }
}

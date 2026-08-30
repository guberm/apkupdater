package com.apkupdater

import com.apkupdater.repository.invalidPlayFileUrls
import com.apkupdater.repository.playAuthRefreshes
import com.apkupdater.repository.retryAfterRefresh
import com.apkupdater.repository.retryTransientPlayAuth
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
    fun rotatesAcrossRateLimitedPlayAccounts() {
        var purchaseAttempts = 0
        var authRefreshes = 0

        val url = retryAfterRefresh(
            action = {
                purchaseAttempts++
                if (purchaseAttempts < 5) "" else "https://example.com/base.apk"
            },
            refresh = { authRefreshes++ },
            shouldRetry = String::isBlank,
            maxRefreshes = { playAuthRefreshes(429) }
        )

        assertEquals("https://example.com/base.apk", url)
        assertEquals(5, purchaseAttempts)
        assertEquals(4, authRefreshes)
        assertEquals(1, playAuthRefreshes(200))
    }

    @Test
    fun retriesTransientPlayAuthServerErrors() {
        val codes = ArrayDeque(listOf(502, 503, 200))
        val delays = mutableListOf<Long>()

        val response = retryTransientPlayAuth(
            responseCode = { it },
            sleep = { delays += it },
            request = { codes.removeFirst() }
        )

        assertEquals(200, response)
        assertEquals(listOf(1_000L, 2_000L), delays)
        assertEquals(
            429,
            retryTransientPlayAuth<Int>(responseCode = { it }, sleep = {}, request = { 429 })
        )
    }

}

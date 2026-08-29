package com.apkupdater

import com.apkupdater.repository.purchasePlayFiles
import com.aurora.gplayapi.data.models.PlayFile
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayPurchaseRetryTest {

    @Test
    fun refreshesAuthAndRetriesEmptyDelivery() {
        var attempts = 0
        var refreshes = 0

        val files = purchasePlayFiles(
            purchase = {
                attempts++
                listOf(PlayFile(url = if (attempts == 1) "" else "https://example.com/base.apk"))
            },
            refreshAuth = { refreshes++ }
        )

        assertEquals("https://example.com/base.apk", files.single().url)
        assertEquals(2, attempts)
        assertEquals(1, refreshes)
    }
}

package com.apkupdater

import com.apkupdater.worker.refreshIntervalMinutes
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatesWorkerTest {

    @Test
    fun mapsRefreshOptionsToWorkManagerIntervals() {
        assertEquals(listOf(15L, 30L, 60L, 120L, 180L, 360L, 720L, 1_440L),
            (0..7).map(::refreshIntervalMinutes))
    }

    @Test
    fun fallsBackToDailyForInvalidSavedValue() {
        assertEquals(1_440L, refreshIntervalMinutes(-1))
        assertEquals(1_440L, refreshIntervalMinutes(8))
    }
}

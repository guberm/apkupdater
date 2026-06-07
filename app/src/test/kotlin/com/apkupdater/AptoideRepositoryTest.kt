package com.apkupdater

import com.apkupdater.repository.buildFilterList
import org.junit.Assert.assertEquals
import org.junit.Test

class AptoideRepositoryTest {

    @Test
    fun buildsNoPreReleaseFilterWhenBothSwitchesAreOff() {
        assertEquals("", buildFilterList(ignoreAlpha = false, ignoreBeta = false))
    }

    @Test
    fun buildsOnlyBetaFilterWhenAlphaIsAllowed() {
        assertEquals("beta", buildFilterList(ignoreAlpha = false, ignoreBeta = true))
    }

    @Test
    fun buildsOnlyAlphaFilterWhenBetaIsAllowed() {
        assertEquals("alpha", buildFilterList(ignoreAlpha = true, ignoreBeta = false))
    }

    @Test
    fun buildsBothFiltersWhenBothSwitchesAreOn() {
        assertEquals("alpha,beta", buildFilterList(ignoreAlpha = true, ignoreBeta = true))
    }
}

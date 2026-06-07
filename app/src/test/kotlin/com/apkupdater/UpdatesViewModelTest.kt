package com.apkupdater

import com.apkupdater.viewmodel.shouldKeepUpdateForIgnoredReleaseLabels
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatesViewModelTest {

    @Test
    fun filtersAlphaVersionWhenIgnoreAlphaIsEnabled() {
        assertFalse(shouldKeepUpdateForIgnoredReleaseLabels("1.2.0-alpha1", ignoreAlpha = true, ignoreBeta = false))
    }

    @Test
    fun filtersBetaVersionWhenIgnoreBetaIsEnabled() {
        assertFalse(shouldKeepUpdateForIgnoredReleaseLabels("1.2.0-beta1", ignoreAlpha = false, ignoreBeta = true))
    }

    @Test
    fun keepsBetaVersionWhenIgnoreBetaIsDisabled() {
        assertTrue(shouldKeepUpdateForIgnoredReleaseLabels("1.2.0-beta1", ignoreAlpha = true, ignoreBeta = false))
    }
}

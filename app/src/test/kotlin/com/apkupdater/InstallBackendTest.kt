package com.apkupdater

import com.apkupdater.viewmodel.useRootInstaller
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallBackendTest {

    @Test
    fun `Shizuku is optional and takes precedence only when enabled`() {
        assertFalse(useRootInstaller(root = false, shizuku = false))
        assertTrue(useRootInstaller(root = true, shizuku = false))
        assertFalse(useRootInstaller(root = false, shizuku = true))
        assertFalse(useRootInstaller(root = true, shizuku = true))
    }
}

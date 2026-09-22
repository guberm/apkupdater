package com.apkupdater

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apkupdater.prefs.Prefs
import com.apkupdater.util.SessionInstaller
import com.apkupdater.util.ShizukuAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class InstallerPermissionTest {
    @Test fun unavailableShizukuFromDownloadWorkerReturnsFalseWithoutCrashing() = runBlocking {
        val prefs = GlobalContext.get().get<Prefs>()
        val installer = GlobalContext.get().get<SessionInstaller>()
        val previous = prefs.shizukuInstall.get()
        try {
            assertFalse("Test app must not have a usable Shizuku connection", ShizukuAccess.isReady())
            prefs.shizukuInstall.put(true)
            withContext(Dispatchers.IO) {
                assertNull("Reproduce the downloader's non-Looper worker", Looper.myLooper())
                assertFalse(installer.checkPermission())
                assertNull("Permission check must return to the caller's dispatcher", Looper.myLooper())
            }
            withContext(Dispatchers.Main.immediate) {
                assertFalse(installer.checkPermission())
            }
        } finally {
            prefs.shizukuInstall.put(previous)
        }
    }
}

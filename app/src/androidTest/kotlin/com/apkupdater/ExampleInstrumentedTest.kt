package com.apkupdater

import android.net.Uri
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.data.ui.GitHubSource
import com.apkupdater.data.ui.PlaySource
import com.apkupdater.viewmodel.prepareUpdates
import com.apkupdater.viewmodel.shouldKeepUpdateForIgnoredUpdates
import com.apkupdater.viewmodel.ignoreAppSourceKey
import com.apkupdater.viewmodel.ignoreVersionKey
import com.apkupdater.viewmodel.ignoreVersionSourceKey
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.apkupdater", appContext.packageName)
    }

    @Test
    fun removesUpdatesWithoutAPackageName() {
        val updates = prepareUpdates(listOf(update("", 10), update("com.example.app", 11)))

        assertEquals(listOf("com.example.app"), updates.map { it.packageName })
    }

    @Test
    fun groupedUpdatesKeepOnlyTheNewestVersionPerPackage() {
        val updates = prepareUpdates(
            listOf(
                update("com.example.app", 10),
                update("com.example.other", 20),
                update("com.example.app", 30)
            ),
            groupByPackage = true
        )

        assertEquals(listOf(30L, 20L), updates.map { it.versionCode }.sortedDescending())
    }

    @Test
    fun sourceIgnoresOnlyHideTheSelectedSource() {
        val play = update("com.example.app", 10)
        val github = play.copy(source = GitHubSource)

        assertFalse(shouldKeepUpdateForIgnoredUpdates(play, setOf(ignoreAppSourceKey(play))))
        assertTrue(shouldKeepUpdateForIgnoredUpdates(github, setOf(ignoreAppSourceKey(play))))
        assertFalse(shouldKeepUpdateForIgnoredUpdates(play, setOf(ignoreVersionSourceKey(play))))
        assertTrue(shouldKeepUpdateForIgnoredUpdates(github, setOf(ignoreVersionSourceKey(play))))
    }

    @Test
    fun globalVersionIgnoreHidesTheVersionFromEverySource() {
        val play = update("com.example.app", 10)
        val github = play.copy(source = GitHubSource)
        val ignored = setOf(ignoreVersionKey(play))

        assertFalse(shouldKeepUpdateForIgnoredUpdates(play, ignored))
        assertFalse(shouldKeepUpdateForIgnoredUpdates(github, ignored))
        assertTrue(shouldKeepUpdateForIgnoredUpdates(play.copy(versionCode = 11), ignored))
    }

    private fun update(packageName: String, versionCode: Long) = AppUpdate(
        name = packageName,
        packageName = packageName,
        version = versionCode.toString(),
        oldVersion = "1",
        versionCode = versionCode,
        oldVersionCode = 1,
        source = PlaySource,
        iconUri = Uri.EMPTY
    )
}

package com.apkupdater

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.data.ui.ApkComboSource
import com.apkupdater.data.ui.Link
import com.apkupdater.ui.component.TvUpdateItem
import com.apkupdater.ui.component.TvSearchItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DownloadActionTest {
    @get:Rule val compose = createComposeRule()

    private val update = AppUpdate(
        "Example", "com.example", "2", "1", 2, 1, ApkComboSource,
        sourceUrl = "https://apkcombo.com/example/com.example/"
    )

    @Test fun sourceOnlyUpdateHasVisibleWorkingDownloadAction() {
        var opened: AppUpdate? = null
        compose.setContent {
            MaterialTheme { TvUpdateItem(update, onOpenSource = { opened = it }) }
        }
        compose.onNodeWithText("Download").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(null, opened) }
        compose.onNodeWithText("APKCombo · 2 · Website").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(update, opened) }
    }

    @Test fun sourceOnlySearchHasVisibleWorkingDownloadAction() {
        var opened: AppUpdate? = null
        compose.setContent {
            MaterialTheme { TvSearchItem(update, onOpenSource = { opened = it }) }
        }
        compose.onNodeWithText("Download").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(null, opened) }
        compose.onNodeWithText("APKCombo · 2 · Website").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(update, opened) }
    }

    @Test fun directDownloadDispatchesInstall() {
        val downloadable = update.copy(link = Link.Url("https://example.com/app.apk"))
        var installed: AppUpdate? = null
        compose.setContent {
            MaterialTheme { TvUpdateItem(downloadable, onInstall = { installed = it }) }
        }
        compose.onNodeWithText("Download").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(null, installed) }
        compose.onNodeWithText("APKCombo · 2 · Download").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(downloadable, installed) }
    }

    @Test fun cardActionsStayOnOneRowAndIgnoreIsInOverflow() {
        var ignored: AppUpdate? = null
        compose.setContent {
            MaterialTheme { TvUpdateItem(update, onIgnoreVersion = { ignored = it }) }
        }
        val website = compose.onNodeWithText("Download").fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithContentDescription("More actions").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Actions must share one row", website.center.y in menu.top..menu.bottom)
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Ignore this version from all sources").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(update, ignored) }
    }

    @Test fun downloadMenuIncludesEverySourceWithoutOpeningBrowserOnFirstTap() {
        val direct = update.copy(source = com.apkupdater.data.ui.GitHubSource, link = Link.Url("https://example.com/app.apk"))
        var opened: AppUpdate? = null
        var installed: AppUpdate? = null
        compose.setContent {
            MaterialTheme {
                TvUpdateItem(update, listOf(update, direct), onInstall = { installed = it }, onOpenSource = { opened = it })
            }
        }
        compose.onNodeWithText("Download").performClick()
        compose.onNodeWithText("APKCombo · 2 · Website").assertIsDisplayed()
        compose.onNodeWithText("GitHub · 2 · Download").assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, opened); assertEquals(null, installed) }
        compose.onNodeWithText("GitHub · 2 · Download").performClick()
        compose.runOnIdle { assertEquals(direct, installed); assertEquals(null, opened) }
    }

    @Test fun overflowHasIndependentAppAndVersionIgnoreForEverySource() {
        val github = update.copy(source = com.apkupdater.data.ui.GitHubSource, version = "3", versionCode = 3)
        var ignoredApp: AppUpdate? = null
        var ignoredVersion: AppUpdate? = null
        var globalCalls = 0
        compose.setContent {
            MaterialTheme {
                TvUpdateItem(update, listOf(update, github),
                    onIgnoreApp = { globalCalls++ }, onIgnoreVersion = { globalCalls++ },
                    onIgnoreAppFromSource = { ignoredApp = it },
                    onIgnoreVersionFromSource = { ignoredVersion = it })
            }
        }
        compose.onNodeWithContentDescription("More actions").performClick()
        val orderedLabels = listOf("Ignore app from all sources", "Ignore app from APKCombo", "Ignore app from GitHub",
            "Ignore this version from all sources", "Ignore version 2 from APKCombo", "Ignore version 3 from GitHub")
        val tops = orderedLabels.map {
            compose.onNodeWithText(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        }
        org.junit.Assert.assertTrue("App actions must precede version actions", tops.zipWithNext().all { (a, b) -> a < b })
        compose.onNodeWithText("Ignore app from APKCombo").performClick()
        compose.runOnIdle { assertEquals(update, ignoredApp); assertEquals(0, globalCalls) }
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.onNodeWithText("Ignore version 3 from GitHub").performClick()
        compose.runOnIdle { assertEquals(github, ignoredVersion); assertEquals(0, globalCalls) }
    }
}

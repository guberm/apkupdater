package com.apkupdater

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.apkupdater.data.github.GitHubAuthor
import com.apkupdater.data.github.GitHubRelease
import com.apkupdater.data.github.GitHubReleaseAsset
import com.apkupdater.data.ui.AppInstalled
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.prefs.Prefs
import com.apkupdater.repository.GitHubRepository
import com.apkupdater.service.GitHubService
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class GitHubRegressionTest {
    private val prefs get() = org.koin.core.context.GlobalContext.get().get<Prefs>()
    private val apps = listOf(
        AppInstalled("AdAway", "org.adaway", "1.0", 1),
        AppInstalled("Aegis", "com.beemdevelopment.aegis", "1.0", 1)
    )

    @Test fun missingRepositoryDoesNotDiscardHealthyUpdatesOrRepeatRequests() = runBlocking {
        val calls = ConcurrentHashMap<String, Int>()
        val service = service { repo ->
            calls.merge(repo, 1, Int::plus)
            if (repo == "AdAway") throw HttpException(Response.error<String>(404, "missing".toResponseBody()))
            releases()
        }
        var latest = emptyList<AppUpdate>()
        var failure: Throwable? = null
        GitHubRepository(service, prefs).updates(apps).catch { failure = it }.collect { latest = it }
        assertEquals(listOf("com.beemdevelopment.aegis"), latest.map { it.packageName })
        assertNotNull("Incomplete scan must remain visible as a failure", failure)
        assertEquals(1, calls["AdAway"])
        assertEquals(1, calls["Aegis"])
    }

    @Test fun transientFailureOnlyRetriesItsOwnRepository() = runBlocking {
        val calls = ConcurrentHashMap<String, Int>()
        val service = service { repo ->
            val count = calls.merge(repo, 1, Int::plus)!!
            if (repo == "AdAway" && count < 3) throw IOException("temporary")
            releases()
        }
        var latest = emptyList<AppUpdate>()
        GitHubRepository(service, prefs).updates(apps).collect { latest = it }
        assertEquals(2, latest.size)
        assertEquals(3, calls["AdAway"])
        assertEquals(1, calls["Aegis"])
    }

    @Test fun exhaustedTransientFailureDoesNotRestartHealthyRepositories() = runBlocking {
        val calls = ConcurrentHashMap<String, Int>()
        val service = service { repo ->
            calls.merge(repo, 1, Int::plus)
            if (repo == "AdAway") throw IOException("offline")
            releases()
        }
        var latest = emptyList<AppUpdate>()
        var failure: Throwable? = null
        GitHubRepository(service, prefs).updates(apps).catch { failure = it }.collect { latest = it }
        assertNotNull(failure)
        assertEquals(1, latest.size)
        assertEquals(3, calls["AdAway"])
        assertEquals(1, calls["Aegis"])
    }

    @Test fun searchRetainsHealthyRepositoriesWhenAnotherFails() = runBlocking {
        val service = service { repo ->
            if (repo == "Calculator") throw HttpException(Response.error<String>(403, "rate limited".toResponseBody()))
            releases()
        }
        var latest: Result<List<AppUpdate>>? = null
        GitHubRepository(service, prefs).search("FossifyOrg").collect { latest = it }
        assertTrue(latest?.isSuccess == true)
        assertTrue(latest!!.getOrThrow().isNotEmpty())
        assertFalse(latest.getOrThrow().any { it.packageName == "org.fossify.calculator" })
    }

    private fun service(answer: (String) -> List<GitHubRelease>) = object : GitHubService {
        override suspend fun getReleases(user: String, repo: String) = answer(repo)
    }

    private fun releases() = listOf(GitHubRelease(
        "v999.0", false, listOf(GitHubReleaseAsset(100, "https://example.com/app.apk")),
        "v999.0", GitHubAuthor("https://example.com/icon.png")
    ))
}

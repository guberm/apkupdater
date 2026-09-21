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
import org.junit.Before
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

    @Before fun clearRequestState() {
        prefs.githubReleaseCache.put(emptyList())
        prefs.githubRetryAt.put(0L)
        prefs.githubResumeUntil.put(0L)
    }

    @Test fun quotaPauseAndResumeSurviveRepositoryRecreation() = runBlocking {
        var time = 1_800_000_000_000L
        val calls = mutableListOf<String>()
        var blocked = true
        val service = service { repo ->
            calls.add(repo)
            if (repo == "Aegis" && blocked) {
                val raw = okhttp3.Response.Builder().request(okhttp3.Request.Builder().url("https://api.github.com").build())
                    .protocol(okhttp3.Protocol.HTTP_1_1).code(403).message("Forbidden")
                    .header("X-RateLimit-Remaining", "0")
                    .header("X-RateLimit-Reset", ((time + 3_600_000) / 1000).toString()).build()
                throw HttpException(Response.error<String>("quota".toResponseBody(), raw))
            }
            releases()
        }
        // Seed a healthy repository before the quota is exhausted.
        GitHubRepository(service, prefs) { time }.updates(apps.take(1)).collect()
        GitHubRepository(service, prefs) { time }.updates(apps).catch {}.collect()
        assertEquals(listOf("AdAway", "Aegis"), calls)
        var latest = emptyList<AppUpdate>()
        GitHubRepository(service, Prefs(org.koin.core.context.GlobalContext.get().get())) { time }
            .updates(apps).catch {}.collect { latest = it }
        assertEquals(2, calls.size)
        assertEquals(listOf("org.adaway"), latest.map { it.packageName })
        time += 3_601_000
        blocked = false
        GitHubRepository(service, prefs) { time }.updates(apps).collect { latest = it }
        assertEquals(listOf("AdAway", "Aegis", "Aegis"), calls)
        assertEquals(2, latest.size)
        assertEquals(0L, prefs.githubResumeUntil.get())
    }

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
        assertTrue("Failure must identify the repository", failure!!.message.orEmpty().contains("AdAway/AdAway"))
        assertTrue("Failure must include the HTTP status", failure!!.message.orEmpty().contains("HTTP 404"))
        val saved = Prefs(org.koin.core.context.GlobalContext.get().get()).githubDiagnostics.get().last()
        assertEquals(com.apkupdater.data.ui.SourceStatusState.Partial, saved.state)
        assertEquals("AdAway/AdAway", saved.failures.single().repository)
        assertTrue(com.apkupdater.util.readAppLogs(listOf(saved)).contains("AdAway/AdAway: HTTP 404"))
        assertEquals(1, calls["AdAway"])
        assertEquals(1, calls["Aegis"])
    }

    @Test fun eightySixRepositoriesMakeProgressAcrossQuotaWindows() = runBlocking {
        var time = 1_800_000_000_000L
        var count = 0
        var reset = false
        val manyApps = com.apkupdater.data.github.GitHubApps.distinctBy { it.packageName }
            .distinctBy { "${it.user}/${it.repo}" }.take(86)
            .map { AppInstalled(it.repo, it.packageName, "1.0", 1) }
        val service = service {
            count++
            if (!reset && count > 60) {
                val raw = okhttp3.Response.Builder().request(okhttp3.Request.Builder().url("https://api.github.com").build())
                    .protocol(okhttp3.Protocol.HTTP_1_1).code(403).message("Forbidden")
                    .header("X-RateLimit-Remaining", "0")
                    .header("X-RateLimit-Reset", ((time + 3_600_000) / 1000).toString()).build()
                throw HttpException(Response.error<String>("quota".toResponseBody(), raw))
            }
            releases()
        }
        GitHubRepository(service, prefs) { time }.updates(manyApps).catch {}.collect()
        assertEquals(61, count)
        assertEquals(60, prefs.githubDiagnostics.get().last().successfulChecks)
        assertTrue(prefs.githubDiagnostics.get().last().description().contains("deferred until quota reset"))
        GitHubRepository(service, prefs) { time }.updates(manyApps).catch {}.collect()
        assertEquals("No network requests during cooldown", 61, count)
        time += 3_601_000
        reset = true
        GitHubRepository(service, prefs) { time }.updates(manyApps).collect()
        val report = prefs.githubDiagnostics.get().last()
        assertEquals(report.totalChecks, report.successfulChecks)
        assertEquals("Only unfinished checks should use the new quota", report.totalChecks + 1, count)
    }

    @Test fun cacheExpiresAndCachedReleasesUseCurrentInstalledVersion() = runBlocking {
        var time = 1_800_000_000_000L
        var calls = 0
        val service = service { calls++; releases() }
        val repository = GitHubRepository(service, prefs) { time }
        repository.updates(apps.take(1)).collect()
        var latest = emptyList<AppUpdate>()
        repository.updates(listOf(AppInstalled("AdAway", "org.adaway", "999.0", 999)))
            .collect { latest = it }
        assertEquals(1, calls)
        assertTrue(latest.isEmpty())
        time += 15 * 60 * 1000 + 1
        repository.updates(apps.take(1)).collect()
        assertEquals(2, calls)
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

    @Test fun noUpdatesFromHealthyRepositoryStillMeansPartialCheckSuccess() = runBlocking {
        val service = service { repo ->
            if (repo == "AdAway") throw HttpException(Response.error<String>(404, "missing".toResponseBody()))
            emptyList()
        }
        var error: Throwable? = null
        GitHubRepository(service, prefs).updates(apps).catch { error = it }.collect()
        val report = (error as com.apkupdater.data.github.GitHubScanException).report
        assertEquals(1, report.successfulChecks)
        assertEquals(2, report.totalChecks)
        assertEquals(com.apkupdater.data.ui.SourceStatusState.Partial, report.state)
    }

    @Test fun allFailuresAreNotPartialAndHistoryIsBounded() = runBlocking {
        val service = service { throw HttpException(Response.error<String>(404, "missing".toResponseBody())) }
        repeat(22) { GitHubRepository(service, prefs).updates(apps).catch {}.collect() }
        val saved = prefs.githubDiagnostics.get()
        assertEquals(20, saved.size)
        assertEquals(0, saved.last().successfulChecks)
        assertEquals(2, saved.last().failures.size)
        assertEquals(com.apkupdater.data.ui.SourceStatusState.Failed, saved.last().state)
    }

    private fun releases() = listOf(GitHubRelease(
        "v999.0", false, listOf(GitHubReleaseAsset(100, "https://example.com/app.apk")),
        "v999.0", GitHubAuthor("https://example.com/icon.png")
    ))
}

package com.apkupdater.repository

import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import android.util.Log
import com.apkupdater.BuildConfig
import com.apkupdater.data.github.GitHubApps
import com.apkupdater.data.github.GitHubRelease
import com.apkupdater.data.github.GitHubReleaseAsset
import com.apkupdater.data.github.GitHubFailure
import com.apkupdater.data.github.GitHubCachedReleases
import com.apkupdater.data.github.GitHubRepositoryException
import com.apkupdater.data.github.GitHubScanException
import com.apkupdater.data.github.GitHubScanReport
import com.apkupdater.data.github.gitHubFailure
import com.apkupdater.data.ui.AppInstalled
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.data.ui.GitHubSource
import com.apkupdater.data.ui.Link
import com.apkupdater.data.ui.getApp
import com.apkupdater.prefs.Prefs
import com.apkupdater.service.GitHubService
import com.apkupdater.util.combine
import com.apkupdater.util.filterVersionTag
import com.apkupdater.util.retryTransiently
import io.github.g00fy2.versioncompare.Version
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException


class GitHubRepository(
    private val service: GitHubService,
    private val prefs: Prefs,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val requestLock = Mutex()

    private suspend fun releases(user: String, repo: String): List<GitHubRelease> = requestLock.withLock {
        val key = "$user/$repo"
        val time = now()
        val resuming = prefs.githubResumeUntil.get() > time
        val cache = prefs.githubReleaseCache.get().filter { time - it.checkedAt in 0 until RESUME_TTL }
        cache.firstOrNull {
            it.repository == key && (resuming || time - it.checkedAt < CACHE_TTL)
        }?.let { return@withLock it.releases }
        val retryAt = prefs.githubRetryAt.get()
        if (retryAt > time) throw GitHubRepositoryException(
            GitHubFailure(key, "GitHub quota paused; check deferred (no request sent)", retryAt = retryAt),
            IOException("Waiting for GitHub quota reset")
        )
        try {
            val result = service.getReleases(user, repo)
            prefs.githubReleaseCache.put(cache.filterNot { it.repository == key } + GitHubCachedReleases(key, time, result))
            result
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val failure = gitHubFailure(key, error, time)
            if (failure.reason == "GitHub rate limit reached" || failure.retryAt != null) {
                // Serialize requests so a quota response stops queued checks before they hit GitHub.
                prefs.githubRetryAt.put((failure.retryAt ?: (time + 3_600_000)).coerceIn(time + 60_000, time + RESUME_TTL))
                if (!resuming) prefs.githubResumeUntil.put(time + RESUME_TTL)
            }
            throw error
        }
    }

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val checks = mutableListOf<Flow<List<AppUpdate>>>()
        if (BuildConfig.APPLICATION_ID == "com.guberdev.apkupdater") checks.add(selfCheck())

        GitHubApps.forEach { app ->
            apps.find { it.packageName == app.packageName }?.let {
                checks.add(checkApp(apps, app.user, app.repo, app.packageName, it.version, app.extra))
            }
        }

        combineChecks(checks, "Updates").collect { emit(it) }
    }.catch {
        Log.e("GitHubRepository", "Error fetching releases.", it)
        throw it
    }

    suspend fun search(text: String) = flow {
        val checks = mutableListOf<Flow<List<AppUpdate>>>()

        GitHubApps.forEach { app ->
            if (app.repo.contains(text, true) || app.user.contains(text, true) || app.packageName.contains(text, true)) {
                checks.add(checkApp(null, app.user, app.repo, app.packageName, "?", app.extra))
            }
        }

        if (checks.isEmpty()) {
            emit(Result.success(emptyList()))
        } else {
            var hasResults = false
            var failure: Throwable? = null
            combineChecks(checks, "Search").catch {
                if (it is CancellationException) throw it
                Log.e("GitHubRepository", "Incomplete search.", it)
                failure = it
            }.collect {
                hasResults = it.isNotEmpty()
                emit(Result.success(it))
            }
            if (!hasResults) failure?.let { emit(Result.failure(it)) }
        }
    }.catch {
        emit(Result.failure(it))
        Log.e("GitHubRepository", "Error searching.", it)
    }

    private fun selfCheck() = flow {
        if (BuildConfig.APPLICATION_ID != "com.guberdev.apkupdater") {
            emit(emptyList())
            return@flow
        }
        val release = releases("guberm", "apkupdater")
            .filter { filterPreRelease(it) }
            .firstOrNull { release -> release.assets.any { it.browser_download_url.endsWith("/com.guberdev.apkupdater-release.apk") } }
        if (release == null) {
            emit(emptyList())
            return@flow
        }
        if (Version(filterVersionTag(release.tag_name)) > Version(BuildConfig.VERSION_NAME)) {
            emit(listOf(AppUpdate(
                name = "APKUpdater",
                packageName = BuildConfig.APPLICATION_ID,
                version = release.tag_name,
                oldVersion = BuildConfig.VERSION_NAME,
                versionCode = 0L,
                oldVersionCode = BuildConfig.VERSION_CODE.toLong(),
                source = GitHubSource,
                link = Link.Url(release.assets.first { it.browser_download_url.endsWith("/com.guberdev.apkupdater-release.apk") }.browser_download_url),
                whatsNew = release.body,
                sourceUrl = release.html_url
            )))
        } else {
            // We need to emit empty so it can be combined later
            emit(listOf())
        }
    }.retryTransiently().catch {
        if (it is CancellationException) throw it
        Log.e("GitHubRepository", "Error checking self-update.", it)
        throw (it as? GitHubRepositoryException ?: GitHubRepositoryException(gitHubFailure("guberm/apkupdater", it, now()), it))
    }

    private fun checkApp(
        apps: List<AppInstalled>?,
        user: String,
        repo: String,
        packageName: String,
        currentVersion: String,
        extra: Regex?
    ) = flow {
        val r = releases(user, repo)
        val releases = if (packageName == "com.apkupdater.ci") {
            // TODO: Find a better way to do this
            r.filter { it.name.contains("CI-Release-3.x")}
        } else {
            r.filter { filterPreRelease(it) }.filter { findApkAsset(it.assets).isNotEmpty() }
        }

        val release = releases.firstOrNull { findApkAssetArch(it.assets, extra).browser_download_url.isNotBlank() }
        if (release != null && Version(filterVersionTag(release.tag_name)) > Version(currentVersion)) {
            val app = apps?.getApp(packageName)
            val asset = findApkAssetArch(release.assets, extra)
            if (asset.browser_download_url.isBlank()) {
                emit(emptyList())
                return@flow
            }
            emit(listOf(AppUpdate(
                name = repo,
                packageName = packageName,
                version = release.tag_name,
                oldVersion = app?.version ?: "?",
                versionCode = 0L,
                oldVersionCode = app?.versionCode ?: 0L,
                source = GitHubSource,
                link = Link.Url(asset.browser_download_url, asset.size),
                whatsNew = release.body,
                iconUri = if (apps == null) release.author.avatar_url.toUri() else Uri.EMPTY,
                sourceUrl = release.html_url
            )))
        } else {
            emit(emptyList())
        }
    }.retryTransiently().catch {
        if (it is CancellationException) throw it
        Log.e("GitHubRepository", "Error fetching releases for $packageName.", it)
        throw (it as? GitHubRepositoryException ?: GitHubRepositoryException(gitHubFailure("$user/$repo", it, now()), it))
    }

    private fun filterPreRelease(release: GitHubRelease) = when {
        prefs.ignorePreRelease.get() && release.prerelease -> false
        else -> true
    }

    // Emit healthy repositories as they finish, then report an incomplete scan without losing them.
    private fun combineChecks(checks: List<Flow<List<AppUpdate>>>, operation: String) = flow {
        var failures = emptyList<GitHubFailure>()
        var successful = 0
        fun report() = GitHubScanReport(System.currentTimeMillis(), BuildConfig.VERSION_NAME,
            operation, checks.size, successful, failures)
        try {
            if (checks.isEmpty()) emit(emptyList())
            else checks.map { check ->
                check.map<List<AppUpdate>, Result<List<AppUpdate>>?> { Result.success(it) }
                    .onStart { emit(null) }
                    .catch {
                        if (it is CancellationException) throw it
                        emit(Result.failure(it))
                    }
            }.combine { results ->
                successful = results.count { it?.isSuccess == true }
                failures = results.mapNotNull { result -> result?.exceptionOrNull()?.let {
                    (it as? GitHubRepositoryException)?.failure ?: gitHubFailure("Unknown repository", it)
                } }
                emit(results.flatMap { it?.getOrDefault(emptyList()).orEmpty() })
            }.collect()
            if (failures.isNotEmpty()) throw GitHubScanException(report())
            if (operation == "Updates") prefs.githubResumeUntil.put(0L)
        } finally {
            // Keep completed and interrupted scans independently of Android's rolling logcat buffer.
            runCatching {
                synchronized(prefs) {
                    prefs.githubDiagnostics.put((prefs.githubDiagnostics.get() + report()).takeLast(20))
                }
            }.onFailure { Log.e("GitHubRepository", "Unable to persist scan diagnostics", it) }
        }
    }

    private fun findApkAsset(assets: List<GitHubReleaseAsset>) = assets
        .filter { it.isInstallablePackageAsset() }
        .maxByOrNull { it.size }
        ?.browser_download_url
        .orEmpty()

    private fun findApkAssetArch(
        assets: List<GitHubReleaseAsset>,
        extra: Regex?
    ): GitHubReleaseAsset {
        val apks = assets
            .filter { it.isInstallablePackageAsset() }
            .filter { filterExtra(it, extra) }

        when {
            apks.isEmpty() -> return GitHubReleaseAsset(0L, "")
            apks.size == 1 -> return apks.first()
            else -> {
                // Try to match exact arch
                Build.SUPPORTED_ABIS.forEach { arch ->
                    apks.forEach { apk ->
                        if (apk.browser_download_url.contains(arch, true)) {
                            return apk
                        }
                    }
                }
                // Try to match arm64
                if (Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
                    apks.forEach { apk ->
                        if (apk.browser_download_url.contains("arm64", true)) {
                            return apk
                        }
                    }
                }
                // Try to match x64
                if (Build.SUPPORTED_ABIS.contains("x86_64")) {
                    apks.forEach { apk ->
                        if (apk.browser_download_url.contains("x64", true)) {
                            return apk
                        }
                    }
                }
                // Try to match arm
                if (Build.SUPPORTED_ABIS.contains("armeabi-v7a")) {
                    apks.forEach { apk ->
                        if (apk.browser_download_url.contains("arm", true)) {
                            return apk
                        }
                    }
                }
                // If no match, return biggest apk in the hope it's universal
                return apks.maxByOrNull { it.size } ?: GitHubReleaseAsset(0L, "")
            }
        }
    }

    private fun filterExtra(asset: GitHubReleaseAsset, extra: Regex?) = when(extra) {
        null -> true
        else -> asset.browser_download_url.matches(extra)
    }

    private fun GitHubReleaseAsset.isInstallablePackageAsset() =
        INSTALLABLE_PACKAGE_EXTENSIONS.any { browser_download_url.endsWith(it, ignoreCase = true) }

    private companion object {
        const val CACHE_TTL = 15 * 60 * 1000L
        const val RESUME_TTL = 24 * 60 * 60 * 1000L
        val INSTALLABLE_PACKAGE_EXTENSIONS = listOf(".apk", ".apkm", ".apks", ".xapk")
    }

}

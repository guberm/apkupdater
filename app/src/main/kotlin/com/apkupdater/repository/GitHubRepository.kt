package com.apkupdater.repository

import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import android.util.Log
import com.apkupdater.BuildConfig
import com.apkupdater.data.github.GitHubApps
import com.apkupdater.data.github.GitHubRelease
import com.apkupdater.data.github.GitHubReleaseAsset
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


class GitHubRepository(
    private val service: GitHubService,
    private val prefs: Prefs
) {

    suspend fun updates(apps: List<AppInstalled>) = flow {
        val checks = mutableListOf(selfCheck())

        GitHubApps.forEach { app ->
            apps.find { it.packageName == app.packageName }?.let {
                checks.add(checkApp(apps, app.user, app.repo, app.packageName, it.version, app.extra))
            }
        }

        checks.combine { all ->
            emit(all.flatMap { it })
        }.collect()
    }.retryTransiently().catch {
        Log.e("GitHubRepository", "Error fetching releases.", it)
        throw it
    }

    suspend fun search(text: String) = flow {
        val checks = mutableListOf<Flow<List<AppUpdate>>>()

        GitHubApps.forEach { app ->
            if (app.repo.contains(text, true) || app.user.contains(text, true) || app.packageName.contains(text, true)) {
                checks.add(checkApp(null, app.user, app.repo, app.packageName, "?", null))
            }
        }

        if (checks.isEmpty()) {
            emit(Result.success(emptyList()))
        } else {
            checks.combine { all ->
                val r = all.flatMap { it }
                emit(Result.success(r))
            }.collect()
        }
    }.retryTransiently().catch {
        emit(Result.failure(it))
        Log.e("GitHubRepository", "Error searching.", it)
    }

    private fun selfCheck() = flow {
        if (BuildConfig.APPLICATION_ID != "com.guberdev.apkupdater") {
            emit(emptyList())
            return@flow
        }
        val release = service.getReleases("guberm", "apkupdater")
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
        Log.e("GitHubRepository", "Error checking self-update.", it)
        throw it
    }

    private fun checkApp(
        apps: List<AppInstalled>?,
        user: String,
        repo: String,
        packageName: String,
        currentVersion: String,
        extra: Regex?
    ) = flow {
        val r = service.getReleases(user, repo)
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
        Log.e("GitHubRepository", "Error fetching releases for $packageName.", it)
        throw it
    }

    private fun filterPreRelease(release: GitHubRelease) = when {
        prefs.ignorePreRelease.get() && release.prerelease -> false
        else -> true
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
        val INSTALLABLE_PACKAGE_EXTENSIONS = listOf(".apk", ".apkm", ".apks", ".xapk")
    }

}

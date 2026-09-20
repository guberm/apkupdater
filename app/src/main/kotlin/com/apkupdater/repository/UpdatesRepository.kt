package com.apkupdater.repository

import android.util.Log
import java.io.IOException
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.data.ui.CachedSourceResult
import com.apkupdater.data.ui.FdroidRepo
import com.apkupdater.data.ui.Source
import com.apkupdater.data.ui.normalized
import com.apkupdater.data.ui.SourceStatus
import com.apkupdater.data.ui.SourceStatusState
import com.apkupdater.data.ui.UpdatesRefreshStatus
import com.apkupdater.data.ui.toAppUpdates
import com.apkupdater.data.ui.toCachedUpdate
import com.apkupdater.prefs.Prefs
import com.apkupdater.R
import com.apkupdater.service.FdroidService
import com.apkupdater.util.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

private const val CACHE_MAX_AGE_MILLIS = 7 * 24 * 60 * 60 * 1_000L
private const val SOURCE_TIMEOUT_MILLIS = 30_000L

class UpdatesRepository(
    private val appsRepository: AppsRepository,
    private val apkMirrorRepository: ApkMirrorRepository,
    private val gitHubRepository: GitHubRepository,
    private val fdroidRepository: FdroidRepository,
    private val izzyRepository: FdroidRepository,
    private val aptoideRepository: AptoideRepository,
    private val apkPureRepository: ApkPureRepository,
    private val apkComboRepository: ApkComboRepository,
    private val uptodownRepository: UptodownRepository,
    private val gitLabRepository: GitLabRepository,
    private val playRepository: PlayRepository,
    private val fdroidService: FdroidService,
    private val prefs: Prefs
) {

    private val refreshStatus = MutableStateFlow(UpdatesRefreshStatus())
    private val cacheLock = Any()

    fun status(): StateFlow<UpdatesRefreshStatus> = refreshStatus.asStateFlow()

    fun updates(onlySources: Set<String>? = null): Flow<List<AppUpdate>> {
        val refreshId = System.currentTimeMillis()
        return flow {
            appsRepository.getApps().collect { result ->
                result.onSuccess { apps ->
                    val filtered = apps.filter { !it.ignored }
                    val sources = mutableListOf<Flow<List<AppUpdate>>>()
                    refreshStatus.value = UpdatesRefreshStatus(isRefreshing = true, installedCount = filtered.size)

                    fun addSource(name: String, source: Flow<List<AppUpdate>>) {
                        val cached = readCachedSource(name)
                        if (onlySources != null && name !in onlySources) {
                            setSourceStatus(
                                name,
                                if (cached == null) SourceStatusState.Skipped else SourceStatusState.Cached,
                                cached?.updates?.size ?: 0,
                                if (cached == null) "Not retried" else "Last successful result"
                            )
                            sources += flowOf(cached?.toAppUpdates().orEmpty())
                            return
                        }

                        setSourceStatus(name, SourceStatusState.Loading, 0)
                        val boundedSource = flow {
                            val completed = withTimeoutOrNull(SOURCE_TIMEOUT_MILLIS) {
                                source.collect { emit(it) }
                                true
                            } == true
                            if (!completed) throw IOException("Source timed out after ${SOURCE_TIMEOUT_MILLIS / 1_000}s")
                        }
                        sources += boundedSource
                            .onStart { setSourceStatus(name, SourceStatusState.Loading, 0) }
                            .onEach {
                                writeCachedSource(name, it)
                                setSourceStatus(name, SourceStatusState.Success, it.size)
                            }
                            .catch { error ->
                                val fallback = readCachedSource(name)
                                setSourceStatus(
                                    name,
                                    if (fallback == null) SourceStatusState.Failed else SourceStatusState.Cached,
                                    fallback?.updates?.size ?: 0,
                                    error.javaClass.simpleName
                                )
                                Log.e("UpdatesRepository", "refresh=$refreshId error source=$name", error)
                                emit(fallback?.toAppUpdates().orEmpty())
                            }
                    }

                    Log.d("UpdatesRepository", "refresh=$refreshId request installed=${apps.size} eligible=${filtered.size}")
                    apps.forEach {
                        Log.d(
                            "UpdatesRepository",
                            "refresh=$refreshId installed package=${it.packageName} versionCode=${it.versionCode} " +
                                "version=${it.version} ignored=${it.ignored}"
                        )
                    }
                    if (prefs.useApkMirror.get()) addSource("ApkMirror", apkMirrorRepository.updates(filtered))
                    if (prefs.useGitHub.get()) addSource("GitHub", gitHubRepository.updates(filtered))
                    if (prefs.useFdroid.get()) addSource("F-Droid (Main)", fdroidRepository.updates(filtered))
                    if (prefs.useIzzy.get()) addSource("F-Droid (Izzy)", izzyRepository.updates(filtered))
                    prefs.customFdroidRepos.get().map(FdroidRepo::normalized).forEach { repo ->
                        if (repo.name.isNotBlank() && repo.url.startsWith("https://")) {
                            addSource(
                                repo.name,
                                FdroidRepository(fdroidService, repo.url, Source(repo.name, R.drawable.ic_fdroid), prefs).updates(filtered)
                            )
                        }
                    }
                    if (prefs.useAptoide.get()) addSource("Aptoide", aptoideRepository.updates(filtered))
                    if (prefs.useApkPure.get()) addSource("ApkPure", apkPureRepository.updates(filtered))
                    if (prefs.useApkCombo.get()) addSource("APKCombo", apkComboRepository.updates(filtered))
                    if (prefs.useUptodown.get()) addSource("Uptodown", uptodownRepository.updates(filtered))
                    if (prefs.useGitLab.get()) addSource("GitLab", gitLabRepository.updates(filtered))
                    if (prefs.usePlay.get()) addSource("Play", playRepository.updates(filtered))
                    refreshStatus.update { it.copy(enabledSourceCount = sources.size) }

                    if (sources.isNotEmpty()) {
                        var mergedCount = 0
                        try {
                            sources.combine { updates ->
                                val merged = updates.flatMap { it }
                                mergedCount = merged.size
                                refreshStatus.update { it.copy(updateCount = mergedCount) }
                                logUpdates(refreshId, "Merged", merged)
                                emit(merged)
                            }.collect()
                        } finally {
                            refreshStatus.update {
                                it.copy(isRefreshing = false, updateCount = mergedCount, lastRefreshAt = System.currentTimeMillis())
                            }
                        }
                    } else {
                        refreshStatus.update {
                            it.copy(isRefreshing = false, updateCount = 0, lastRefreshAt = System.currentTimeMillis())
                        }
                        logUpdates(refreshId, "Merged", emptyList())
                        emit(emptyList())
                    }
                }.onFailure {
                    refreshStatus.update { it.copy(isRefreshing = false) }
                    Log.e("UpdatesRepository", "refresh=$refreshId error getting installed apps", it)
                }
            }
        }.catch {
            refreshStatus.update { it.copy(isRefreshing = false, lastRefreshAt = System.currentTimeMillis()) }
            Log.e("UpdatesRepository", "refresh=$refreshId error getting updates", it)
        }
    }

    private fun setSourceStatus(name: String, state: SourceStatusState, count: Int, detail: String = "") {
        refreshStatus.update { current ->
            val statuses = current.sourceStatuses
                .filterNot { it.name == name }
                .plus(SourceStatus(name, state, count, detail))
                .sortedBy(SourceStatus::name)
            current.copy(sourceStatuses = statuses)
        }
    }

    private fun readCachedSource(name: String): CachedSourceResult? = synchronized(cacheLock) {
        prefs.cachedUpdateSources.get()
            .firstOrNull { it.sourceName == name }
            ?.takeIf { System.currentTimeMillis() - it.checkedAt <= CACHE_MAX_AGE_MILLIS }
    }

    private fun writeCachedSource(name: String, updates: List<AppUpdate>) = synchronized(cacheLock) {
        val cached = CachedSourceResult(
            sourceName = name,
            checkedAt = System.currentTimeMillis(),
            updates = updates.map(AppUpdate::toCachedUpdate)
        )
        prefs.cachedUpdateSources.put(
            prefs.cachedUpdateSources.get().filterNot { it.sourceName == name } + cached
        )
    }
}

private fun logUpdates(refreshId: Long, scope: String, updates: List<AppUpdate>) {
    Log.d("UpdatesRepository", "refresh=$refreshId result source=$scope count=${updates.size}")
    updates.forEach {
        Log.d(
            "UpdatesRepository",
            "refresh=$refreshId update source=${it.source.name} package=${it.packageName} " +
                "installed=${it.oldVersionCode} available=${it.versionCode} version=${it.version}"
        )
    }
}

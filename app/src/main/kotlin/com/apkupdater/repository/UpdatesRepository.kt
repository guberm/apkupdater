package com.apkupdater.repository

import android.util.Log
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.prefs.Prefs
import com.apkupdater.util.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart


class UpdatesRepository(
    private val appsRepository: AppsRepository,
    private val apkMirrorRepository: ApkMirrorRepository,
    private val gitHubRepository: GitHubRepository,
    private val fdroidRepository: FdroidRepository,
    private val izzyRepository: FdroidRepository,
    private val aptoideRepository: AptoideRepository,
    private val apkPureRepository: ApkPureRepository,
    private val gitLabRepository: GitLabRepository,
    private val playRepository: PlayRepository,
    private val prefs: Prefs
) {

    fun updates(): Flow<List<AppUpdate>> {
        val refreshId = System.currentTimeMillis()
        return flow {
            appsRepository.getApps().collect { result ->
                result.onSuccess { apps ->
                    val filtered = apps.filter { !it.ignored }
                    val sources = mutableListOf<Flow<List<AppUpdate>>>()
                    fun addSource(name: String, source: Flow<List<AppUpdate>>) {
                        sources += source
                            .onStart { Log.d("UpdatesRepository", "refresh=$refreshId request source=$name apps=${filtered.size}") }
                            .onEach { logUpdates(refreshId, name, it) }
                            .catch {
                                Log.e("UpdatesRepository", "refresh=$refreshId error source=$name", it)
                                throw it
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
                    if (prefs.useAptoide.get()) addSource("Aptoide", aptoideRepository.updates(filtered))
                    if (prefs.useApkPure.get()) addSource("ApkPure", apkPureRepository.updates(filtered))
                    if (prefs.useGitLab.get()) addSource("GitLab", gitLabRepository.updates(filtered))
                    if (prefs.usePlay.get()) addSource("Play", playRepository.updates(filtered))

                    if (sources.isNotEmpty()) {
                        sources
                            .combine { updates ->
                                val merged = updates.flatMap { it }
                                logUpdates(refreshId, "Merged", merged)
                                emit(merged)
                            }
                            .collect()
                    } else {
                        logUpdates(refreshId, "Merged", emptyList())
                        emit(emptyList())
                    }
                }.onFailure {
                    Log.e("UpdatesRepository", "refresh=$refreshId error getting installed apps", it)
                }
            }
        }.catch {
            Log.e("UpdatesRepository", "refresh=$refreshId error getting updates", it)
        }
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

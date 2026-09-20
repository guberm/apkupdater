package com.apkupdater.repository

import android.util.Log
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.R
import com.apkupdater.data.ui.FdroidRepo
import com.apkupdater.data.ui.Source
import com.apkupdater.data.ui.normalized
import com.apkupdater.prefs.Prefs
import com.apkupdater.service.FdroidService
import com.apkupdater.util.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

class SearchRepository(
    private val apkMirrorRepository: ApkMirrorRepository,
    private val fdroidRepository: FdroidRepository,
    private val izzyRepository: FdroidRepository,
    private val aptoideRepository: AptoideRepository,
    private val gitHubRepository: GitHubRepository,
    private val apkPureRepository: ApkPureRepository,
    private val apkComboRepository: ApkComboRepository,
    private val uptodownRepository: UptodownRepository,
    private val gitLabRepository: GitLabRepository,
    private val playRepository: PlayRepository,
    private val fdroidService: FdroidService,
    private val prefs: Prefs
) {

    fun search(text: String) = flow {
        val sources = mutableListOf<Flow<Result<List<AppUpdate>>>>()
        if (prefs.useApkMirror.get()) sources.add(apkMirrorRepository.search(text))
        if (prefs.useFdroid.get()) sources.add(fdroidRepository.search(text))
        if (prefs.useIzzy.get()) sources.add(izzyRepository.search(text))
        prefs.customFdroidRepos.get().map(FdroidRepo::normalized).forEach { repo ->
            if (repo.name.isNotBlank() && repo.url.startsWith("https://")) {
                sources.add(FdroidRepository(fdroidService, repo.url, Source(repo.name, R.drawable.ic_fdroid), prefs).search(text))
            }
        }
        if (prefs.useAptoide.get()) sources.add(aptoideRepository.search(text))
        if (prefs.useGitHub.get()) sources.add(gitHubRepository.search(text))
        if (prefs.useApkPure.get()) sources.add(apkPureRepository.search(text))
        if (prefs.useApkCombo.get()) sources.add(apkComboRepository.search(text))
        if (prefs.useUptodown.get()) sources.add(uptodownRepository.search(text))
        if (prefs.useGitLab.get()) sources.add(gitLabRepository.search(text))
        if (prefs.usePlay.get()) sources.add(playRepository.search(text))

        if (sources.isNotEmpty()) {
            sources.combine { updates ->
                val result = updates.filter { it.isSuccess }.mapNotNull { it.getOrNull() }
                emit(Result.success(result.flatten().sortedBy { it.name }))
            }.collect()
        } else {
            emit(Result.success(emptyList()))
        }
    }.catch {
        emit(Result.failure(it))
        Log.e("SearchRepository", "Error searching.", it)
    }

}

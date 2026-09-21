package com.apkupdater.viewmodel

import androidx.lifecycle.viewModelScope
import com.apkupdater.R
import com.apkupdater.data.snack.TextSnack
import com.apkupdater.data.ui.AppInstallStatus
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.data.ui.SearchUiState
import com.apkupdater.data.ui.removeId
import com.apkupdater.data.ui.setIsInstalling
import com.apkupdater.data.ui.setProgress
import com.apkupdater.data.ui.preserveActiveDownloads
import com.apkupdater.prefs.Prefs
import com.apkupdater.repository.SearchRepository
import com.apkupdater.util.Badger
import com.apkupdater.util.Downloader
import com.apkupdater.util.InstallLog
import com.apkupdater.util.SessionInstaller
import com.apkupdater.util.SnackBar
import com.apkupdater.util.Stringer
import com.apkupdater.util.launchWithMutex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex


class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val installer: SessionInstaller,
    private val badger: Badger,
    private val downloader2: Downloader,
    prefs: Prefs,
    private val snackBar: SnackBar,
    private val stringer: Stringer,
    installLog: InstallLog
) : InstallViewModel(downloader2, installer, prefs, snackBar, stringer, installLog) {

    private val mutex = Mutex()
    private val searchMutex = Mutex()
    private val state = MutableStateFlow<SearchUiState>(SearchUiState.Success(emptyList()))
    private var job: Job? = null

    init {
        subscribeToInstallStatus()
        subscribeToInstallProgress { progress ->
            state.update { current ->
                if (current.updates().any { it.id == progress.id }) SearchUiState.Success(current.mutableUpdates().setProgress(progress)) else current
            }
        }
    }

    fun state(): StateFlow<SearchUiState> = state

    fun search(text: String) {
        job?.cancel()
        job = searchJob(text)
    }

    private fun searchJob(text: String) = viewModelScope.launchWithMutex(searchMutex, Dispatchers.IO) {
        state.update { if (it.updates().any { app -> app.isInstalling }) it else SearchUiState.Loading }
        badger.changeSearchBadge("")
        searchRepository.search(text).collect {
            it.onSuccess { apps ->
                state.update { SearchUiState.Success(apps.preserveActiveDownloads(it.updates())) }
                badger.changeSearchBadge(apps.size.toString())
            }.onFailure {
                badger.changeSearchBadge("!")
                state.update { if (it.updates().any { app -> app.isInstalling }) it else SearchUiState.Error }
            }
        }
    }

    public override fun cancelInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
        downloader2.cancelDownload(id)
        state.update { SearchUiState.Success(it.mutableUpdates().setIsInstalling(id, false)) }
        installer.finish()
    }

    override fun finishInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
        state.update { SearchUiState.Success(it.mutableUpdates().removeId(id)) }
        badger.changeSearchBadge(state.value.updates().size.toString())
        installer.finish()
    }

    override fun downloadAndRootInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
        state.update { SearchUiState.Success(it.mutableUpdates().setIsInstalling(update.id, true)) }
        downloadAndRootInstall(update.id, update.packageName, update.link)
    }

    override fun downloadAndInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
        if(installer.checkPermission()) {
            state.update { SearchUiState.Success(it.mutableUpdates().setIsInstalling(update.id, true)) }
            downloadAndInstall(update.id, update.packageName, update.link)
        }
    }

    override fun sendInstallSnack(log: AppInstallStatus) {
        if (log.snack) {
            state.value.updates().find { log.id == it.id }?.let { app ->
                val message = if (log.success) R.string.install_success else R.string.install_failure
                snackBar.snackBar(viewModelScope, TextSnack(stringer.get(message, app.name)))
            }
        }
    }

}

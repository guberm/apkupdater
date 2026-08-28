package com.apkupdater.viewmodel

import android.net.Uri
import android.util.Log
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.apkupdater.R
import com.apkupdater.data.snack.TextSnack
import com.apkupdater.data.ui.AppInstallProgress
import com.apkupdater.data.ui.AppInstallStatus
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.data.ui.Link
import com.apkupdater.prefs.Prefs
import com.apkupdater.util.Downloader
import com.apkupdater.util.InstallLog
import com.apkupdater.util.SessionInstaller
import com.apkupdater.util.SnackBar
import com.apkupdater.util.Stringer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach


abstract class InstallViewModel(
    private val downloader: Downloader,
    private val installer: SessionInstaller,
    private val prefs: Prefs,
    private val snackBar: SnackBar,
    private val stringer: Stringer,
    protected val installLog: InstallLog
): ViewModel() {

    fun install(update: AppUpdate) {
        if (prefs.rootInstall.get()) {
            downloadAndRootInstall(update)
        } else {
            downloadAndInstall(update)
        }
    }

    fun openSource(update: AppUpdate, uriHandler: UriHandler) {
        if (update.sourceUrl.isBlank()) return
        runCatching { uriHandler.openUri(update.sourceUrl) }
            .onFailure {
                Log.e("InstallViewModel", "Unable to open source URL: ${update.sourceUrl}", it)
                snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.open_source_failure)))
            }
    }

    protected fun subscribeToInstallStatus() = installLog.status().onEach {
        sendInstallSnack(it)
        if (it.success) {
            finishInstall(it.id).join()
        } else {
            installLog.emitProgress(AppInstallProgress(it.id, 0L))
            cancelInstall(it.id).join()
        }
    }.launchIn(viewModelScope)

    protected fun subscribeToInstallProgress(
        block: (AppInstallProgress) -> Unit
    ) = installLog.progress().onEach {
        block(it)
    }.launchIn(viewModelScope)

    protected fun downloadAndRootInstall(id: Int, link: Link) = runCatching {
        when (link) {
            is Link.Url -> {
                if (installer.rootInstall(downloader.download(link.link))) {
                    finishInstall(id)
                } else {
                    cancelInstall(id)
                }
            }
            else -> {
                snackBar.snackBar(
                    viewModelScope,
                    TextSnack(stringer.get(R.string.root_install_not_supported))
                )
                cancelInstall(id)
            }
        }
    }.getOrElse {
        Log.e("InstallViewModel", "Error in downloadAndRootInstall.", it)
        cancelInstall(id)
    }

    protected suspend fun downloadAndInstall(id: Int, packageName: String, link: Link) = runCatching {
        installLog.emitProgress(AppInstallProgress(id, 0L))
        when (link) {
            Link.Empty -> {
                Log.e("InstallViewModel", "downloadAndInstall: Unsupported.")
                sendDownloadFailure(packageName)
                cancelInstall(id)
            }
            is Link.Play -> {
                val files = link.getInstallFiles()
                Log.d("InstallViewModel", "Play: id=$id pkg=$packageName splits=${files.size} totalBytes=${files.sumOf { it.size }}")
                files.forEachIndexed { i, f -> Log.d("InstallViewModel", "Play split[$i]: url=${f.url} size=${f.size}") }
                val totalBytes = files.sumOf { it.size }
                installLog.emitProgress(AppInstallProgress(id, 0L, totalBytes))
                // Download sequentially with per-byte progress updates
                var alreadyDownloaded = 0L
                val tempFiles = mutableListOf<java.io.File>()
                try {
                    files.forEach { file ->
                        val offsetForThisFile = alreadyDownloaded
                        tempFiles += downloader.downloadFile(file.url) { curr, _ ->
                            installLog.emitProgress(AppInstallProgress(id, offsetForThisFile + curr, totalBytes))
                        }
                        alreadyDownloaded += file.size
                    }
                    Log.d("InstallViewModel", "Play: downloads complete, tempFiles=${tempFiles.map { "${it.name}:${it.length()}" }}")
                    // Save to custom directory if configured
                    val customDirStr = prefs.downloadDir.get()
                    if (customDirStr.isNotEmpty()) {
                        val treeUri = Uri.parse(customDirStr)
                        tempFiles.forEachIndexed { i, f ->
                            val name = if (tempFiles.size == 1) "$packageName.apk" else "${packageName}_$i.apk"
                            Log.d("InstallViewModel", "Play: copying to custom dir name=$name treeUri=$treeUri")
                            downloader.copyToUri(f, treeUri, name)?.let { uri -> trackDownloadedUri(id, uri) }
                        }
                    }
                    installer.install(id, packageName, tempFiles.map { it.inputStream() })
                } finally {
                    tempFiles.forEach { it.delete() }
                }
            }
            is Link.Url -> {
                installLog.emitProgress(AppInstallProgress(id, 0L, link.size))
                val stream = downloader.downloadStream(link.link, id)
                if (stream == null) {
                    Log.e("InstallViewModel", "downloadAndInstall: download failed for $packageName")
                    sendDownloadFailure(packageName)
                    cancelInstall(id)
                } else {
                    installer.installPackage(id, packageName, stream)
                }
            }
            is Link.Xapk -> {
                val stream = downloader.downloadStream(link.link, id)
                if (stream == null) {
                    Log.e("InstallViewModel", "downloadAndInstall: XAPK download failed for $packageName")
                    sendDownloadFailure(packageName)
                    cancelInstall(id)
                } else {
                    installer.installXapk(id, packageName, stream)
                }
            }
        }
    }.getOrElse {
        if (it is CancellationException) throw it
        Log.e("InstallViewModel", "Error in downloadAndInstall.", it)
        sendDownloadFailure(packageName)
        cancelInstall(id)
    }

    private fun sendDownloadFailure(packageName: String) {
        snackBar.snackBar(viewModelScope, TextSnack(stringer.get(R.string.download_failure, packageName)))
    }

    protected abstract fun sendInstallSnack(log: AppInstallStatus)
    protected abstract fun downloadAndInstall(update: AppUpdate): Job
    protected abstract fun downloadAndRootInstall(update: AppUpdate): Job
    protected abstract fun cancelInstall(id: Int): Job
    protected abstract fun finishInstall(id: Int): Job
    protected open fun trackDownloadedUri(id: Int, uri: Uri) {}
}

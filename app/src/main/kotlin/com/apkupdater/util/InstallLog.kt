package com.apkupdater.util

import com.apkupdater.data.ui.AppInstallProgress
import com.apkupdater.data.ui.AppInstallStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap


class InstallLog {

    private val status = MutableSharedFlow<AppInstallStatus>(100)
    private val progress = MutableSharedFlow<AppInstallProgress>(100)
    private val expectedInstallIds = ConcurrentHashMap<Int, Boolean>()
    @Volatile var currentInstallId: Int = 0

    fun status() = status.asSharedFlow()
    fun progress() = progress.asSharedFlow()

    fun expectInstall(id: Int) {
        expectedInstallIds[id] = true
    }

    fun isExpectedInstall(id: Int) = expectedInstallIds.containsKey(id)

    fun finishExpectedInstall(id: Int) {
        expectedInstallIds.remove(id)
        if (currentInstallId == id) currentInstallId = 0
    }

    fun cancelCurrentInstall() {
        if (currentInstallId != 0 && isExpectedInstall(currentInstallId)) {
            val id = currentInstallId
            finishExpectedInstall(id)
            status.tryEmit(AppInstallStatus(false, id, false))
        }
    }

    fun emitStatus(newStatus: AppInstallStatus) = status.tryEmit(newStatus)
    fun emitProgress(newProgress: AppInstallProgress) = progress.tryEmit(newProgress)

}

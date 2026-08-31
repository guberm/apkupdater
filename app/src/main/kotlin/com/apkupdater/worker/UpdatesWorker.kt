package com.apkupdater.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.apkupdater.prefs.Prefs
import com.apkupdater.repository.UpdatesRepository
import com.apkupdater.util.UpdatesNotification
import com.apkupdater.viewmodel.filterVisibleUpdates
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit


private val REFRESH_INTERVAL_MINUTES = listOf(15L, 30L, 60L, 120L, 180L, 360L, 720L, 1_440L)

internal fun refreshIntervalMinutes(option: Int) =
    REFRESH_INTERVAL_MINUTES.getOrElse(option) { REFRESH_INTERVAL_MINUTES.last() }

internal fun updateAppCount(packageNames: List<String>) = packageNames.distinct().size


class UpdatesWorker(
    context: Context,
    workerParams: WorkerParameters
): CoroutineWorker(context, workerParams), KoinComponent {

    companion object: KoinComponent {
        private const val WORK_NAME = "AutoRefreshWorker"
        private const val LEGACY_WORK_NAME = "UpdatesWorker"
        private val prefs: Prefs by inject()

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
            workManager.cancelUniqueWork(LEGACY_WORK_NAME)
        }

        fun schedule(
            workManager: WorkManager,
            enabled: Boolean,
            policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.UPDATE
        ) {
            if (!enabled) return cancel(workManager)

            workManager.cancelUniqueWork(LEGACY_WORK_NAME)
            val request = PeriodicWorkRequestBuilder<UpdatesWorker>(
                refreshIntervalMinutes(prefs.refreshInterval.get()),
                TimeUnit.MINUTES
            ).build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, policy, request)
        }
    }

    private val updatesRepository: UpdatesRepository by inject()
    private val notification: UpdatesNotification by inject()

    override suspend fun doWork(): Result {
        updatesRepository.updates().collect {
            val visibleUpdates = filterVisibleUpdates(
                updates = it,
                ignoredVersions = prefs.ignoredVersions.get().toSet(),
                ignoredUpdates = prefs.ignoredUpdates.get().toSet(),
                ignoreSameVersion = prefs.ignoreSameVersion.get(),
                ignoreAlpha = prefs.ignoreAlpha.get(),
                ignoreBeta = prefs.ignoreBeta.get()
            )
            notification.showUpdateNotification(updateAppCount(visibleUpdates.map { update -> update.packageName }))
        }
        return Result.success()
    }

}

package com.apkupdater.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.apkupdater.R
import com.apkupdater.data.snack.TextSnack
import com.apkupdater.data.ui.AppInstallProgress
import com.apkupdater.data.ui.AppInstallStatus
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.data.ui.Link
import com.apkupdater.data.ui.UpdatesUiState
import com.apkupdater.data.ui.removeId
import com.apkupdater.data.ui.removePackage
import com.apkupdater.data.ui.setIsInstalling
import com.apkupdater.data.ui.setProgress
import com.apkupdater.prefs.Prefs
import com.apkupdater.repository.UpdatesRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

enum class UpdatesFilter { All, ByPackage, BySource }


class UpdatesViewModel(
	private val updatesRepository: UpdatesRepository,
	private val installer: SessionInstaller,
	private val prefs: Prefs,
	private val badger: Badger,
	private val downloader2: Downloader,
	private val snackBar: SnackBar,
	private val stringer: Stringer,
	installLog: InstallLog,
	private val applicationScope: kotlinx.coroutines.CoroutineScope,
	private val application: Application
) : InstallViewModel(downloader2, installer, prefs, snackBar, stringer, installLog) {

	private val mutex = Mutex()
	private val state = MutableStateFlow<UpdatesUiState>(UpdatesUiState.Loading)
	val isRefreshing = MutableStateFlow(false)
	private val downloadJobs = ConcurrentHashMap<Int, Job>()
	private val downloadedUris = ConcurrentHashMap<Int, MutableList<Uri>>()

	val filterMode = MutableStateFlow(UpdatesFilter.All)
	val filterQuery = MutableStateFlow("")
	val groupByPackage = MutableStateFlow(prefs.groupByPackageDefault.get())

	fun setFilter(filter: UpdatesFilter) { filterMode.value = filter; filterQuery.value = "" }
	fun toggleGroupByPackage() { groupByPackage.value = !groupByPackage.value }
	fun getAlternatives(packageName: String, updates: List<AppUpdate>): List<AppUpdate> =
		updates.filter { it.packageName == packageName }
	fun setFilterQuery(query: String) { filterQuery.value = query }

	fun filteredUpdates(updates: List<AppUpdate>, filter: UpdatesFilter = filterMode.value, query: String = filterQuery.value): List<AppUpdate> {
		return when (filter) {
			UpdatesFilter.All -> updates
			UpdatesFilter.ByPackage -> if (query.isEmpty()) updates else updates.filter { it.packageName.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true) }
			UpdatesFilter.BySource -> if (query.isEmpty()) updates else updates.filter { it.source.name.contains(query, ignoreCase = true) }
		}
	}

	init {
		subscribeToInstallStatus()
		subscribeToInstallProgress { progress ->
			state.value = UpdatesUiState.Success(state.value.mutableUpdates().setProgress(progress))
		}
	}

	fun state(): StateFlow<UpdatesUiState> = state

	fun refresh(load: Boolean = true) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		isRefreshing.value = true
		try {
			if (load) state.value = UpdatesUiState.Loading
			badger.changeUpdatesBadge("")
			updatesRepository.updates().collect {
				setSuccess(it)
			}
		} finally {
			isRefreshing.value = false
		}
	}

	fun installAll() = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		if(installer.checkPermission()) {
			prepareUpdates(state.value.updates(), groupByPackage = true).forEach { update ->
				if (state.value.updates().any { it.id == update.id && it.isInstalling }) return@forEach
				state.value = UpdatesUiState.Success(state.value.mutableUpdates().setIsInstalling(update.id, true))
				val job = applicationScope.launch {
					performDownloadAndInstall(update)
				}
				downloadJobs[update.id] = job
			}
		}
	}

	fun ignoreVersion(update: AppUpdate) = ignoreUpdate(ignoreVersionKey(update))

	fun ignoreVersionFromSource(update: AppUpdate) = ignoreUpdate(ignoreVersionSourceKey(update))

	fun ignoreAppFromSource(update: AppUpdate) = ignoreUpdate(ignoreAppSourceKey(update))

	fun ignoreApp(update: AppUpdate) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		val ignored = prefs.ignoredApps.get().toMutableList()
		if (!ignored.contains(update.packageName)) ignored.add(update.packageName)
		prefs.ignoredApps.put(ignored)
		setSuccess(state.value.mutableUpdates().filter { it.packageName != update.packageName }.toMutableList())
	}

	private fun ignoreUpdate(key: String) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		prefs.ignoredUpdates.put((prefs.ignoredUpdates.get() + key).distinct())
		setSuccess(state.value.mutableUpdates())
	}

	public override fun cancelInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		downloader2.cancelDownload(id)
		downloadJobs.remove(id)?.cancel()
		cleanupDownload(id)
		state.value = UpdatesUiState.Success(state.value.mutableUpdates().setIsInstalling(id, false))
		installer.finish()
	}

	override fun finishInstall(id: Int) = viewModelScope.launchWithMutex(mutex, Dispatchers.IO) {
		cleanupDownload(id)
		setSuccess(state.value.mutableUpdates().removePackage(id))
		installer.finish()
	}

	override fun downloadAndRootInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
		state.value = UpdatesUiState.Success(state.value.mutableUpdates().setIsInstalling(update.id, true))
		downloadAndRootInstall(update.id, update.link)
	}

	override fun downloadAndInstall(update: AppUpdate) = viewModelScope.launch(Dispatchers.IO) {
		if(installer.checkPermission()) {
			state.value = UpdatesUiState.Success(state.value.mutableUpdates().setIsInstalling(update.id, true))
			val job = viewModelScope.launch(Dispatchers.IO) {
				performDownloadAndInstall(update)
			}
			downloadJobs[update.id] = job
			job.join()
			downloadJobs.remove(update.id)
		}
	}

	private suspend fun performDownloadAndInstall(update: AppUpdate) {
		try {
			val customDirStr = prefs.downloadDir.get()
			Log.d("UpdatesViewModel", "downloadAndInstall: pkg=${update.packageName} customDir='$customDirStr' linkType=${update.link::class.simpleName}")
			if (customDirStr.isNotEmpty() && update.link is Link.Url) {
				val treeUri = Uri.parse(customDirStr)
				Log.d("UpdatesViewModel", "downloadAndInstall: using custom dir treeUri=$treeUri url=${update.link.link}")
				installLog.emitProgress(AppInstallProgress(update.id, 0L, update.link.size))
				val savedUri = downloader2.downloadToUri(update.link.link, treeUri, "${update.packageName}.apk") { curr, total ->
					installLog.emitProgress(AppInstallProgress(update.id, curr, if (total > 0) total else update.link.size))
				}
				Log.d("UpdatesViewModel", "downloadAndInstall: savedUri=$savedUri")
				if (savedUri != null) {
					downloadedUris.getOrPut(update.id) { mutableListOf() }.add(savedUri)
					val stream = application.contentResolver.openInputStream(savedUri)
					Log.d("UpdatesViewModel", "downloadAndInstall: openInputStream stream=${stream != null} savedUri=$savedUri")
					if (stream != null) {
						installer.installPackage(update.id, update.packageName, stream)
					} else {
						Log.e("UpdatesViewModel", "downloadAndInstall: stream is null for savedUri=$savedUri")
						cancelInstall(update.id)
					}
				} else {
					Log.e("UpdatesViewModel", "downloadAndInstall: downloadToUri returned null, falling back to normal download")
					downloadAndInstall(update.id, update.packageName, update.link)
				}
			} else {
				Log.d("UpdatesViewModel", "downloadAndInstall: proceeding (Play link handles custom dir internally, or no custom dir set)")
				downloadAndInstall(update.id, update.packageName, update.link)
			}
		} finally {
			downloadJobs.remove(update.id)
		}
	}

	override fun trackDownloadedUri(id: Int, uri: Uri) {
		downloadedUris.getOrPut(id) { mutableListOf() }.add(uri)
	}

	override fun sendInstallSnack(log: AppInstallStatus) {
		if (log.snack) {
			state.value.updates().find { log.id == it.id }?.let { app ->
				val message = if (log.success) R.string.install_success else R.string.install_failure
				snackBar.snackBar(viewModelScope, TextSnack(stringer.get(message, app.name)))
			}
		}
	}

	private fun setSuccess(updates: List<AppUpdate>) = filterVisibleUpdates(
		updates = updates,
		ignoredVersions = prefs.ignoredVersions.get().toSet(),
		ignoredUpdates = prefs.ignoredUpdates.get().toSet(),
		ignoreSameVersion = prefs.ignoreSameVersion.get(),
		ignoreAlpha = prefs.ignoreAlpha.get(),
		ignoreBeta = prefs.ignoreBeta.get()
	)
		.let {
			state.value = UpdatesUiState.Success(it)
			badger.changeUpdatesBadge(it.distinctBy(AppUpdate::packageName).size.toString())
		}

	private fun cleanupDownload(id: Int) {
		downloadedUris.remove(id)?.let { uris ->
			if (prefs.deleteAfterInstall.get()) {
				uris.forEach { uri ->
					runCatching { DocumentsContract.deleteDocument(application.contentResolver, uri) }
				}
			}
		}
	}

}

internal fun prepareUpdates(
	updates: List<AppUpdate>,
	groupByPackage: Boolean = false
): List<AppUpdate> {
	val valid = updates.filter { it.packageName.isNotBlank() }
	if (!groupByPackage) return valid

	return valid
		.groupBy { it.packageName }
		.values
		.map { alternatives -> alternatives.maxBy { it.versionCode } }
}

internal fun filterVisibleUpdates(
	updates: List<AppUpdate>,
	ignoredVersions: Set<Int>,
	ignoredUpdates: Set<String>,
	ignoreSameVersion: Boolean,
	ignoreAlpha: Boolean,
	ignoreBeta: Boolean
) = prepareUpdates(updates)
	.filter { it.id !in ignoredVersions }
	.filter { shouldKeepUpdateForIgnoredUpdates(it, ignoredUpdates) }
	.filter { !ignoreSameVersion || it.version != it.oldVersion }
	.filter {
		shouldKeepUpdateForIgnoredReleaseLabels(
			version = it.version,
			ignoreAlpha = ignoreAlpha,
			ignoreBeta = ignoreBeta
		)
	}

internal fun shouldKeepUpdateForIgnoredReleaseLabels(
	version: String,
	ignoreAlpha: Boolean,
	ignoreBeta: Boolean
): Boolean = when {
	ignoreAlpha && version.contains("alpha", ignoreCase = true) -> false
	ignoreBeta && version.contains("beta", ignoreCase = true) -> false
	else -> true
}

internal fun ignoreAppSourceKey(update: AppUpdate) =
	"app-source|${update.packageName}|${update.source.name}"

internal fun ignoreVersionKey(update: AppUpdate) =
	"version|${update.packageName}|${update.versionCode}|${update.version}"

internal fun ignoreVersionSourceKey(update: AppUpdate) =
	"version-source|${update.packageName}|${update.versionCode}|${update.version}|${update.source.name}"

internal fun shouldKeepUpdateForIgnoredUpdates(update: AppUpdate, ignored: Set<String>) =
	ignoreAppSourceKey(update) !in ignored &&
		ignoreVersionKey(update) !in ignored &&
		ignoreVersionSourceKey(update) !in ignored

package com.apkupdater.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkupdater.BuildConfig
import com.apkupdater.R
import com.apkupdater.data.ui.GitHubSource
import com.apkupdater.data.ui.SettingsUiState
import com.apkupdater.ui.component.ButtonSetting
import com.apkupdater.ui.component.DropDownSetting
import com.apkupdater.ui.component.LargeTitle
import com.apkupdater.ui.component.LoadingImageApp
import com.apkupdater.ui.component.MediumText
import com.apkupdater.ui.component.MediumTitle
import com.apkupdater.ui.component.SegmentedButtonSetting
import com.apkupdater.ui.component.SourceIcon
import com.apkupdater.ui.component.SwitchSetting
import com.apkupdater.ui.theme.statusBarColor
import com.apkupdater.util.getAppName
import com.apkupdater.viewmodel.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel


@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) = Column {
	when (viewModel.state.collectAsStateWithLifecycle().value) {
		SettingsUiState.Settings -> {
			SettingsTopBar(viewModel)
			Settings(viewModel)
		}
		SettingsUiState.About -> {
			AboutTopBar(viewModel)
			About()
		}
		SettingsUiState.Ignored -> {
			IgnoredTopBar(viewModel)
			IgnoredScreen(viewModel)
		}
	}
}

@Composable
fun About() = LazyColumn(
	Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
	item {
		Column(Modifier.padding(vertical = 16.dp)) {
			LoadingImageApp(BuildConfig.APPLICATION_ID)
			LargeTitle(stringResource(R.string.app_name), Modifier.align(CenterHorizontally))
			MediumText("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", Modifier.align(CenterHorizontally))
		}
	}
	item {
		AboutItem(
			"GitHub - APK Updater",
			stringResource(R.string.about_github),
			"https://github.com/guberm/apkupdater",
			{ SourceIcon(GitHubSource, Modifier.size(64.dp).align(CenterVertically)) }
		)
		AboutItem(
			"Guber.dev",
			stringResource(R.string.about_guber_dev),
			"https://guber.dev",
			{ Icon(Icons.Default.Info, "Guber.dev", Modifier.size(64.dp).align(CenterVertically)) }
		)
	}
}


@Composable
fun AboutItem(
	title: String,
	body: String,
	link: String,
	icon: @Composable RowScope.() -> Unit,
	handler: UriHandler = LocalUriHandler.current
) = OutlinedCard(
	Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable { handler.openUri(link) }) {
	Row(Modifier.padding(8.dp)) {
		icon()
		Column(Modifier.padding(start = 16.dp)) {
			MediumTitle(title)
			MediumText(body, maxLines = 2)
		}
	}
}

@Composable
fun Settings(viewModel: SettingsViewModel) = LazyColumn {
	item {
		LargeTitle(stringResource(R.string.settings_ui), Modifier.padding(start = 16.dp, top = 16.dp))
		SwitchSetting(
			{ viewModel.getPlayTextAnimations() },
			{ viewModel.setPlayTextAnimations(it) },
			stringResource(R.string.play_text_animations),
			R.drawable.ic_animation
		)
		SegmentedButtonSetting(
			stringResource(R.string.theme),
			listOf(
				stringResource(R.string.theme_system),
				stringResource(R.string.theme_dark),
				stringResource(R.string.theme_light)
			),
			{ viewModel.getTheme() },
			{ viewModel.setTheme(it) },
			R.drawable.ic_theme
		)
	}

	item {
		LargeTitle(stringResource(R.string.settings_sources), Modifier.padding(start = 16.dp, top = 16.dp))
		SwitchSetting(
			{ viewModel.getUseGitHub() },
			{ viewModel.setUseGitHub(it) },
			stringResource(R.string.source_github),
			R.drawable.ic_github
		)
		SwitchSetting(
			{ viewModel.getUseGitLab() },
			{ viewModel.setUseGitLab(it) },
			stringResource(R.string.source_gitlab),
			R.drawable.ic_gitlab
		)
		SwitchSetting(
			{ viewModel.getUseApkMirror() },
			{ viewModel.setUseApkMirror(it) },
			stringResource(R.string.source_apkmirror),
			R.drawable.ic_apkmirror
		)
		DropDownSetting(
			text = stringResource(R.string.settings_apkmirror_arch),
			options = listOf("Auto", "arm64-v8a", "armeabi-v7a", "x86_64", "x86"),
			getValue = { viewModel.getApkMirrorArch() },
			setValue = { viewModel.setApkMirrorArch(it) },
			icon = R.drawable.ic_apkmirror,
			width = 130
		)
		SwitchSetting(
			{ viewModel.getUseFdroid() },
			{ viewModel.setUseFdroid(it) },
			stringResource(R.string.source_fdroid),
			R.drawable.ic_fdroid
		)
		SwitchSetting(
			{ viewModel.getUseIzzy() },
			{ viewModel.setUseIzzy(it) },
			stringResource(R.string.source_izzy),
			R.drawable.ic_izzy
		)
		SwitchSetting(
			{ viewModel.getUseAptoide() },
			{ viewModel.setUseAptoide(it) },
			stringResource(R.string.source_aptoide),
			R.drawable.ic_aptoide
		)
		SwitchSetting(
			{ viewModel.getUseApkPure() },
			{ viewModel.setUseApkPure(it) },
			stringResource(R.string.source_apkpure),
			R.drawable.ic_apkpure
		)
		SwitchSetting(
			{ viewModel.getUseApkCombo() },
			{ viewModel.setUseApkCombo(it) },
			stringResource(R.string.source_apkcombo),
			R.drawable.ic_download
		)
		SwitchSetting(
			{ viewModel.getUseUptodown() },
			{ viewModel.setUseUptodown(it) },
			stringResource(R.string.source_uptodown),
			R.drawable.ic_download
		)
		SwitchSetting(
			{ viewModel.getUsePlay() },
			{ viewModel.setUsePlay(it) },
			stringResource(R.string.source_play) + " (Alpha)",
			R.drawable.ic_play
		)
		CustomFdroidSettings(viewModel)
	}

	item {
		LargeTitle(stringResource(R.string.settings_options), Modifier.padding(start = 16.dp, top = 16.dp))
		SwitchSetting(
			{ viewModel.getRootInstall() },
			{ viewModel.setRootInstall(it) },
			stringResource(R.string.root_install),
			R.drawable.ic_root
		)
		SwitchSetting(
			{ viewModel.getIgnoreAlpha() },
			{ viewModel.setIgnoreAlpha(it) },
			stringResource(R.string.ignore_alpha),
			R.drawable.ic_alpha
		)
		SwitchSetting(
			{ viewModel.getIgnoreBeta() },
			{ viewModel.setIgnoreBeta(it) },
			stringResource(R.string.ignore_beta),
			R.drawable.ic_beta
		)
		SwitchSetting(
			{ viewModel.getIgnorePreRelease() },
			{ viewModel.setIgnorePreRelease(it) },
			stringResource(R.string.ignore_preRelease),
			R.drawable.ic_pre_release
		)
		SwitchSetting(
			{ viewModel.getIgnoreSameVersion() },
			{ viewModel.setIgnoreSameVersion(it) },
			stringResource(R.string.ignore_same_version),
			R.drawable.ic_visible
		)
		SwitchSetting(
			{ viewModel.getUseSafeStores() },
			{ viewModel.setUseSafeStores(it) },
			stringResource(R.string.use_safe_stores),
			R.drawable.ic_safe
		)
		SwitchSetting(
			{ viewModel.getNewInstaller() },
			{ viewModel.setNewInstaller(it) },
			stringResource(R.string.settings_experimental_installer),
			R.drawable.ic_root
		)
	}
	item {
		val enabled = viewModel.shizukuInstall.collectAsStateWithLifecycle().value
		key(enabled) {
			SwitchSetting(
				{ enabled },
				{ viewModel.setShizukuInstall(it) },
				stringResource(R.string.settings_shizuku_installer),
				R.drawable.ic_root
			)
		}
	}

	item {
		val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
		LargeTitle(stringResource(R.string.settings_autorefresh), Modifier.padding(start = 16.dp, top = 16.dp))
		SwitchSetting(
			getValue = { viewModel.getAutoRefreshEnabled() },
			setValue = { viewModel.setAutoRefreshEnabled(it, launcher) },
			text = stringResource(R.string.settings_autorefresh_enable),
			icon = R.drawable.ic_alarm
		)
		DropDownSetting(
			text = stringResource(R.string.settings_autorefresh_interval),
			options = listOf(
				stringResource(R.string.settings_interval_15_minutes),
				stringResource(R.string.settings_interval_30_minutes),
				stringResource(R.string.settings_interval_1_hour),
				stringResource(R.string.settings_interval_2_hours),
				stringResource(R.string.settings_interval_3_hours),
				stringResource(R.string.settings_interval_6_hours),
				stringResource(R.string.settings_interval_12_hours),
				stringResource(R.string.settings_interval_24_hours)
			),
			getValue = { viewModel.getRefreshInterval() },
			setValue = { viewModel.setRefreshInterval(it) },
			icon = R.drawable.ic_frequency,
			width = 180
		)
		SwitchSetting(
			{ viewModel.getRefreshOnWifiOnly() },
			{ viewModel.setRefreshOnWifiOnly(it) },
			stringResource(R.string.settings_autorefresh_wifi_only),
			R.drawable.ic_frequency
		)
		SwitchSetting(
			{ viewModel.getRefreshWhileCharging() },
			{ viewModel.setRefreshWhileCharging(it) },
			stringResource(R.string.settings_autorefresh_charging),
			R.drawable.ic_alarm
		)
	}
	item {
		val context = LocalContext.current
		LargeTitle(stringResource(R.string.settings_utils), Modifier.padding(start = 16.dp, top = 16.dp))
		ButtonSetting(
			stringResource(R.string.copy_app_list),
			{ viewModel.copyAppList() },
			R.drawable.ic_root,
			R.drawable.ic_copy
		)
		ButtonSetting(
			stringResource(R.string.copy_app_logs),
			{ viewModel.copyAppLogs() },
			R.drawable.ic_root,
			R.drawable.ic_copy
		)
		ButtonSetting(
			stringResource(R.string.send_app_logs),
			{ viewModel.sendAppLogs(context) },
			R.drawable.ic_root,
			R.drawable.ic_send
		)
	}
	item {
		val context = LocalContext.current
		val dirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
			if (uri != null) {
				runCatching {
					context.contentResolver.takePersistableUriPermission(
						uri,
						android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
					)
				}
			}
			viewModel.setDownloadDir(uri?.toString() ?: "")
		}
		LargeTitle(stringResource(R.string.settings_options) + " 2", Modifier.padding(start = 16.dp, top = 16.dp))
		SwitchSetting(
			{ viewModel.getGroupByPackageDefault() },
			{ viewModel.setGroupByPackageDefault(it) },
			stringResource(R.string.settings_group_by_default),
			R.drawable.ic_filter
		)
		SwitchSetting(
			{ viewModel.getDeleteAfterInstall() },
			{ viewModel.setDeleteAfterInstall(it) },
			stringResource(R.string.settings_delete_after_install),
			R.drawable.ic_download
		)
		val currentDir = viewModel.getDownloadDir()
		val dirSubtitle = if (currentDir.isEmpty()) {
			stringResource(R.string.settings_download_dir_default)
		} else {
			runCatching {
				val decoded = android.net.Uri.decode(currentDir)
				val last = decoded.substringAfterLast(":")
				last.ifEmpty { decoded }
			}.getOrDefault(currentDir)
		}
		ButtonSetting(
			stringResource(R.string.settings_download_dir),
			{ dirLauncher.launch(null) },
			R.drawable.ic_folder,
			R.drawable.ic_folder,
			dirSubtitle
		)
		ButtonSetting(
			stringResource(R.string.settings_clear_download_cache),
			{ viewModel.clearDownloadCache() },
			R.drawable.ic_download,
			R.drawable.ic_refresh,
			stringResource(R.string.settings_clear_download_cache_description)
		)
		ButtonSetting(
			stringResource(R.string.settings_ignored),
			{ viewModel.setIgnored() },
			R.drawable.ic_block,
			R.drawable.ic_block
		)
	}
}

@Composable
private fun CustomFdroidSettings(viewModel: SettingsViewModel) {
	val repositories = viewModel.customFdroidRepos.collectAsStateWithLifecycle().value
	val name = remember { mutableStateOf("") }
	val url = remember { mutableStateOf("") }
	val invalid = remember { mutableStateOf(false) }

	LargeTitle(stringResource(R.string.settings_custom_fdroid), Modifier.padding(start = 16.dp, top = 16.dp))
	OutlinedTextField(
		value = name.value,
		onValueChange = { name.value = it; invalid.value = false },
		modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
		label = { Text(stringResource(R.string.settings_custom_fdroid_name)) },
		singleLine = true
	)
	OutlinedTextField(
		value = url.value,
		onValueChange = { url.value = it; invalid.value = false },
		modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
		label = { Text(stringResource(R.string.settings_custom_fdroid_url)) },
		singleLine = true
	)
	Button(
		onClick = {
			if (viewModel.addCustomFdroidRepo(name.value, url.value)) {
				name.value = ""
				url.value = ""
				invalid.value = false
			} else {
				invalid.value = true
			}
		},
		modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
	) {
		Text(stringResource(R.string.settings_custom_fdroid_add))
	}
	if (invalid.value) {
		Text(
			stringResource(R.string.settings_custom_fdroid_invalid),
			color = MaterialTheme.colorScheme.error,
			modifier = Modifier.padding(horizontal = 16.dp)
		)
	}
	repositories.forEach { repo ->
		Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = CenterVertically) {
			Column(Modifier.weight(1f)) {
				MediumTitle(repo.name)
				MediumText(repo.url, maxLines = 1)
			}
			TextButton(onClick = { viewModel.removeCustomFdroidRepo(repo) }) {
				Text(stringResource(R.string.settings_custom_fdroid_remove))
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar(viewModel: SettingsViewModel) = TopAppBar(
	title = { Text(stringResource(R.string.tab_settings)) },
	colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.statusBarColor()),
	windowInsets = WindowInsets(0),
	actions = {
		IconButton(onClick = { viewModel.setAbout() }) {
			Icon(painterResource(R.drawable.ic_info), stringResource(R.string.about))
		}
	},
	navigationIcon = {
		Box(Modifier.minimumInteractiveComponentSize().size(40.dp), Alignment.Center) {
			Icon(Icons.Filled.Settings, "Tab Icon")
		}
	}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutTopBar(viewModel: SettingsViewModel) = TopAppBar(
	title = { Text(stringResource(R.string.about)) },
	colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.statusBarColor()),
	windowInsets = WindowInsets(0),
	actions = {
		IconButton(onClick = { viewModel.setSettings() }) {
			Icon(Icons.Default.Settings, stringResource(R.string.tab_settings))
		}
	},
	navigationIcon = {
		Box(Modifier.minimumInteractiveComponentSize().size(40.dp), Alignment.Center) {
			Icon(Icons.Filled.Info, "Tab Icon")
		}
	}
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IgnoredTopBar(viewModel: SettingsViewModel) = TopAppBar(
	title = { Text(stringResource(R.string.settings_ignored)) },
	colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.statusBarColor()),
	windowInsets = WindowInsets(0),
	actions = {
		IconButton(onClick = { viewModel.setSettings() }) {
			Icon(Icons.Default.Settings, stringResource(R.string.tab_settings))
		}
	},
	navigationIcon = {
		Box(Modifier.minimumInteractiveComponentSize().size(40.dp), Alignment.Center) {
			Icon(painterResource(R.drawable.ic_block), "Tab Icon")
		}
	}
)

@Composable
fun IgnoredScreen(viewModel: SettingsViewModel) {
	val ignoredApps = remember { mutableStateOf(viewModel.getIgnoredApps()) }

	if (ignoredApps.value.isEmpty()) {
		Box(Modifier.fillMaxSize()) {
			MediumTitle(stringResource(R.string.settings_ignored_empty), Modifier.align(Alignment.Center))
		}
	} else {
		LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
			items(ignoredApps.value) { packageName ->
				IgnoredAppItem(packageName) {
					viewModel.unignoreApp(packageName)
					ignoredApps.value = viewModel.getIgnoredApps()
				}
			}
		}
	}
}

@Composable
fun IgnoredAppItem(packageName: String, onUnignore: () -> Unit) = OutlinedCard(
	Modifier.fillMaxWidth().padding(bottom = 8.dp, top = 8.dp)
) {
	Row(Modifier.padding(8.dp)) {
		LoadingImageApp(packageName, Modifier.size(48.dp).align(Alignment.CenterVertically))
		Column(Modifier.padding(start = 12.dp).weight(1f).align(Alignment.CenterVertically)) {
			MediumTitle(LocalContext.current.getAppName(packageName).ifEmpty { packageName })
			MediumText(packageName)
		}
		IconButton(onClick = onUnignore, Modifier.align(Alignment.CenterVertically)) {
			Icon(painterResource(R.drawable.ic_visible), stringResource(R.string.unignore_app))
		}
	}
}

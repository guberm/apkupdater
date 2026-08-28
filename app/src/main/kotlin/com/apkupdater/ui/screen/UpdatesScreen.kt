package com.apkupdater.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkupdater.R
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.ui.component.DefaultErrorScreen
import com.apkupdater.ui.component.DownloadIcon
import com.apkupdater.ui.component.EmptyGrid
import com.apkupdater.ui.component.LoadingGrid
import com.apkupdater.ui.component.RefreshIcon
import com.apkupdater.ui.component.TvInstalledGrid
import com.apkupdater.ui.component.TvUpdateItem
import com.apkupdater.ui.theme.statusBarColor
import com.apkupdater.viewmodel.UpdatesFilter
import com.apkupdater.viewmodel.UpdatesViewModel


@Composable
fun UpdatesScreen(viewModel: UpdatesViewModel) {
	viewModel.state().collectAsStateWithLifecycle().value.onLoading {
		UpdatesScreenLoading(viewModel)
	}.onError {
		UpdatesScreenError()
	}.onSuccess {
		UpdatesScreenSuccess(viewModel, it.updates)
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesTopBar(viewModel: UpdatesViewModel) = TopAppBar(
	title = {
		Text(stringResource(R.string.tab_updates))
	},
	colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.statusBarColor()),
	windowInsets = WindowInsets(0),
	actions = {
		if(koinInject<Prefs>().newInstaller.get()) {
			IconButton(onClick = { viewModel.installAll() }) {
				DownloadIcon(stringResource(R.string.install_all))
			}
		}
		IconButton(onClick = { viewModel.refresh() }) {
			RefreshIcon(stringResource(R.string.refresh_updates))
		}
	},
	navigationIcon = {
		Box(Modifier.minimumInteractiveComponentSize().size(40.dp), Alignment.Center) {
			Icon(Icons.Filled.ThumbUp, "Tab Icon")
		}
	}
)

@Composable
fun UpdatesFilterBar(viewModel: UpdatesViewModel, updates: List<AppUpdate>) {
	val currentFilter by viewModel.filterMode.collectAsStateWithLifecycle()
	val currentQuery by viewModel.filterQuery.collectAsStateWithLifecycle()
	val groupByPackage by viewModel.groupByPackage.collectAsStateWithLifecycle()

	Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
		Row(
			Modifier.horizontalScroll(rememberScrollState()),
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			FilterChip(
				selected = currentFilter == UpdatesFilter.All,
				onClick = { viewModel.setFilter(UpdatesFilter.All) },
				label = { Text(stringResource(R.string.filter_all)) }
			)
			FilterChip(
				selected = currentFilter == UpdatesFilter.ByPackage,
				onClick = { viewModel.setFilter(UpdatesFilter.ByPackage) },
				label = { Text(stringResource(R.string.filter_by_package)) }
			)
			FilterChip(
				selected = currentFilter == UpdatesFilter.BySource,
				onClick = { viewModel.setFilter(UpdatesFilter.BySource) },
				label = { Text(stringResource(R.string.filter_by_source)) }
			)
			FilterChip(
				selected = groupByPackage,
				onClick = { viewModel.toggleGroupByPackage() },
				label = { Text(stringResource(R.string.filter_group)) }
			)
		}
		when (currentFilter) {
			UpdatesFilter.ByPackage -> OutlinedTextField(
				value = currentQuery,
				onValueChange = { viewModel.setFilterQuery(it) },
				modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
				placeholder = { Text(stringResource(R.string.filter_search_hint)) },
				singleLine = true
			)
			UpdatesFilter.BySource -> {
				val sources = updates.map { it.source }.distinctBy { it.name }
				Row(
					Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					sources.forEach { source ->
						FilterChip(
							selected = currentQuery == source.name,
							onClick = {
								viewModel.setFilterQuery(if (currentQuery == source.name) "" else source.name)
							},
							label = { Text(source.name) }
						)
					}
				}
			}
			else -> {}
		}
	}
}

@Composable
fun UpdatesScreenLoading(viewModel: UpdatesViewModel) = Column {
	UpdatesTopBar(viewModel)
	LoadingGrid()
}

@Composable
fun UpdatesScreenError() = DefaultErrorScreen()

@Composable
fun UpdatesScreenSuccess(
	viewModel: UpdatesViewModel,
	updates: List<AppUpdate>
) = Column {
	val handler = LocalUriHandler.current
	val currentFilter by viewModel.filterMode.collectAsStateWithLifecycle()
	val currentQuery by viewModel.filterQuery.collectAsStateWithLifecycle()
	val groupByPackage by viewModel.groupByPackage.collectAsStateWithLifecycle()
	val displayedUpdates = run {
		val filtered = viewModel.filteredUpdates(updates, currentFilter, currentQuery)
		if (groupByPackage) filtered.distinctBy { it.packageName } else filtered
	}

	UpdatesTopBar(viewModel)
	UpdatesFilterBar(viewModel, updates)

	when {
		displayedUpdates.isEmpty() -> EmptyGrid(stringResource(R.string.no_updates_found))
		else -> TvGrid(viewModel, displayedUpdates, updates, groupByPackage, handler)
	}
}

@Composable
fun TvGrid(
	viewModel: UpdatesViewModel,
	updates: List<AppUpdate>,
	allUpdates: List<AppUpdate>,
	groupByPackage: Boolean,
	handler: UriHandler
) = TvInstalledGrid {
	items(updates) { update ->
		val alts = if (groupByPackage) allUpdates.filter { it.packageName == update.packageName } else listOf(update)
		TvUpdateItem(
			app = update,
			alternatives = alts,
			onInstall = { chosen -> viewModel.install(chosen) },
			onIgnoreVersion = { viewModel.ignoreVersion(update.id) },
			onIgnoreApp = { viewModel.ignoreApp(update.packageName) },
			onCancel = { viewModel.cancelInstall(alts.firstOrNull { it.isInstalling }?.id ?: update.id) },
			onOpenSource = { chosen -> viewModel.openSource(chosen, handler) }
		)
	}
}

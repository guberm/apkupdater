package com.apkupdater.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.apkupdater.R
import com.apkupdater.data.ui.AppInstalled
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.data.ui.Link
import com.apkupdater.util.clickableNoRipple
import com.apkupdater.util.getAppName


@Composable
fun AppImage(app: AppInstalled, onIgnore: (String) -> Unit = {}) = Box {
	LoadingImageApp(app.packageName)
	TextBubble(app.versionCode, Modifier.align(Alignment.BottomStart))
	IgnoreIcon(
		app.ignored,
		{ onIgnore(app.packageName) },
		Modifier.align(Alignment.TopEnd).padding(4.dp)
	)
}

@Composable
fun UpdateImage(
    app: AppUpdate,
    alternatives: List<AppUpdate> = emptyList(),
    onInstall: (AppUpdate) -> Unit = {},
    onCancel: () -> Unit = {}
) = Box {
	LoadingImageApp(app.packageName)
	TextBubble(app.versionCode, Modifier.align(Alignment.BottomStart))
	if (alternatives.any { it.isInstalling }) {
		CircularProgressIndicator(
			Modifier
				.align(Alignment.TopEnd)
				.size(30.dp)
				.padding(4.dp)
				.clickableNoRipple(onCancel),
			color = MaterialTheme.colorScheme.primary
		)
	} else {
		var expanded by remember { mutableStateOf(false) }
		Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
			Row(
				Modifier.clickableNoRipple {
					if (alternatives.size > 1) expanded = true else onInstall(app)
				},
				horizontalArrangement = Arrangement.spacedBy(2.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				val iconSize = if (alternatives.size > 1) 20.dp else 28.dp
				alternatives.forEach { alt ->
					SourceIcon(alt.source, Modifier.size(iconSize))
				}
			}
			DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
				alternatives.forEach { alt ->
					DropdownMenuItem(
						text = {
							Column {
								MediumText(alt.source.name)
								SmallText(alt.version)
							}
						},
						leadingIcon = { SourceIcon(alt.source, Modifier.size(20.dp)) },
						onClick = { expanded = false; onInstall(alt) }
					)
				}
			}
		}
	}
}


@Composable
fun SearchImage(app: AppUpdate, onInstall: (Link) -> Unit = {}) = Box {
	LoadingImage(app.iconUri)
	TextBubble(app.versionCode, Modifier.align(Alignment.BottomStart))
	InstallProgressIcon(app.isInstalling) { onInstall(app.link) }
	SourceIcon(
		app.source,
		Modifier.align(Alignment.TopStart).padding(4.dp).size(28.dp)
	)
}

@Composable
fun InstalledItem(app: AppInstalled, onIgnore: (String) -> Unit = {}) = Column(
	modifier = Modifier.alpha(if (app.ignored) 0.5f else 1f)
) {
	AppImage(app, onIgnore)
	Column(Modifier.padding(top = 4.dp)) {
		ScrollableText { SmallText(app.packageName) }
		MediumTitle(app.name)
	}
}

@Composable
fun UpdateItem(
    app: AppUpdate,
    alternatives: List<AppUpdate> = emptyList(),
    onInstall: (AppUpdate) -> Unit = {},
    onCancel: () -> Unit = {},
    onIgnoreApp: () -> Unit = {}
) = Column {
	UpdateImage(app, alternatives, onInstall, onCancel)
	Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
		Column(Modifier.weight(1f)) {
			ScrollableText { SmallText(app.packageName) }
			MediumTitle(app.name.ifEmpty { LocalContext.current.getAppName(app.packageName) })
		}
		IgnoreAppIcon(onIgnoreApp, Modifier.size(20.dp).padding(2.dp))
	}
}

@Composable
fun SearchItem(app: AppUpdate, onInstall: (Link) -> Unit = {}) = Column {
	SearchImage(app, onInstall)
	Column(Modifier.padding(top = 4.dp)) {
		ScrollableText { SmallText(app.packageName) }
		MediumTitle(app.name)
	}
}

@Composable
fun DefaultErrorScreen() = Box(Modifier.fillMaxSize()) {
	HugeText(
		stringResource(R.string.something_went_wrong),
		Modifier.align(Alignment.Center),
		2
	)
}

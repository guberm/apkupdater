package com.apkupdater.ui.component

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.apkupdater.R
import com.apkupdater.data.ui.AppInstalled
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.data.ui.Link
import com.apkupdater.util.getAppName
import com.apkupdater.util.toAnnotatedString


@Composable
fun TvCommonItem(
    packageName: String,
    name: String,
    version: String,
    oldVersion: String?,
    versionCode: Long,
    oldVersionCode: Long?,
    uri: Uri? = null,
    single: Boolean = false
) = Row(Modifier.fillMaxWidth()) {
    if (uri == null) {
        LoadingImageApp(packageName, Modifier.size(88.dp).align(Alignment.CenterVertically))
    } else {
        LoadingImage(uri, Modifier.size(88.dp).align(Alignment.CenterVertically))
    }
    Column(Modifier.weight(1f).align(Alignment.CenterVertically).padding(start = 8.dp, end = 8.dp, top = 8.dp)) {
        LargeTitle(name.ifEmpty { LocalContext.current.getAppName(packageName) }.ifEmpty { packageName }, maxLines = 2)
        MediumText(packageName)
        val code = if (versionCode == 0L) "?" else versionCode.toString()
        if (oldVersion != null && !single) {
            MediumText(stringResource(R.string.old_version_format, oldVersion, oldVersionCode?.toString() ?: "?"))
            MediumText(stringResource(R.string.new_version_format, version, code))
        } else {
            MediumText(version)
            MediumText(code)
        }
    }
}

@Composable
fun TvInstallButton(
    app: AppUpdate,
    alternatives: List<AppUpdate> = listOf(app),
    onInstall: (AppUpdate) -> Unit,
    onCancel: () -> Unit = {},
    onOpenSource: (AppUpdate) -> Unit = {}
) = Box {
    var expanded by remember { mutableStateOf(false) }
    val candidates = alternatives.latestPerSource().filter { it.link != Link.Empty || it.sourceUrl.isNotBlank() }
    val installing = alternatives.firstOrNull { it.isInstalling }
    Button(
        enabled = installing != null || candidates.isNotEmpty(),
        modifier = Modifier
            .widthIn(min = 64.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        onClick = {
            if (installing != null) onCancel()
            else expanded = true
        }
    ) {
        if (installing != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
                Text(stringResource(R.string.cancel_download_action), maxLines = 1)
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DownloadIcon(stringResource(R.string.install_cd), Modifier.size(20.dp))
                Text(stringResource(R.string.download_action))
            }
        }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        candidates.forEach { alt ->
            DropdownMenuItem(
                text = {
                    val action = stringResource(if (alt.link == Link.Empty) R.string.download_website else R.string.download_action)
                    SmallText("${alt.source.name} · ${alt.version} · $action")
                },
                leadingIcon = { SourceIcon(alt.source, Modifier.size(20.dp)) },
                onClick = {
                    expanded = false
                    if (alt.link == Link.Empty) onOpenSource(alt) else onInstall(alt)
                }
            )
        }
    }
}

@Composable
fun BoxScope.TvSourceIcon(app: AppUpdate) = SourceIcon(
    app.source,
    Modifier
        .align(Alignment.CenterStart)
        .padding(top = 0.dp, bottom = 8.dp, start = 8.dp, end = 8.dp)
        .size(32.dp)
)

@Composable
fun TvInstalledItem(app: AppInstalled, onIgnore: (String) -> Unit = {}) = Card(
    modifier = Modifier.alpha(if (app.ignored) 0.5f else 1f)
) {
    Column {
        TvCommonItem(app.packageName, app.name, app.version, null, app.versionCode, null)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ElevatedButton(
                modifier = Modifier.padding(top = 0.dp, bottom = 8.dp, start = 8.dp, end = 8.dp),
                onClick = { onIgnore(app.packageName) }
            ) {
                Text(stringResource(if (app.ignored) R.string.unignore_cd else R.string.ignore_cd))
            }
        }
    }
}

@Composable
fun TvIgnoreVersionButton(
    app: AppUpdate,
    alternatives: List<AppUpdate>,
    onIgnoreVersion: (AppUpdate) -> Unit,
    onIgnoreVersionFromSource: (AppUpdate) -> Unit,
) = Box {
    var expanded by remember { mutableStateOf(false) }
    ElevatedButton(
        modifier = Modifier.padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        onClick = { expanded = true }
    ) {
        Text(stringResource(R.string.ignore_version))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ignore_version_all_sources)) },
            onClick = { expanded = false; onIgnoreVersion(app) }
        )
        alternatives.latestPerSource().forEach { update ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ignore_version_source, update.version, update.source.name)) },
                leadingIcon = { SourceIcon(update.source, Modifier.size(20.dp)) },
                onClick = { expanded = false; onIgnoreVersionFromSource(update) }
            )
        }
    }
}

@Composable
fun TvIgnoreAppButton(
    app: AppUpdate,
    alternatives: List<AppUpdate>,
    onIgnoreApp: (AppUpdate) -> Unit,
    onIgnoreAppFromSource: (AppUpdate) -> Unit,
) = Box {
    var expanded by remember { mutableStateOf(false) }
    ElevatedButton(
        modifier = Modifier.padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        onClick = { expanded = true }
    ) {
        Text(stringResource(R.string.ignore_app))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ignore_app_cd)) },
            onClick = { expanded = false; onIgnoreApp(app) }
        )
        alternatives.latestPerSource().forEach { update ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ignore_app_source, update.source.name)) },
                leadingIcon = { SourceIcon(update.source, Modifier.size(20.dp)) },
                onClick = { expanded = false; onIgnoreAppFromSource(update) }
            )
        }
    }
}

@Composable
fun TvOpenSourceButton(
    app: AppUpdate,
    alternatives: List<AppUpdate> = listOf(app),
    onOpenSource: (AppUpdate) -> Unit
) = Box {
    var expanded by remember { mutableStateOf(false) }
    val candidates = alternatives.latestPerSource()
    val sources = candidates.filter { it.sourceUrl.isNotBlank() }
    TextButton(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        enabled = sources.isNotEmpty(),
        onClick = { expanded = true }
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.open_source))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, Modifier.size(20.dp))
        }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        sources.forEach { update ->
            DropdownMenuItem(
                text = { Text("${update.source.name} · ${update.version}") },
                leadingIcon = { SourceIcon(update.source, Modifier.size(20.dp)) },
                onClick = { expanded = false; onOpenSource(update) }
            )
        }
    }
}

private fun List<AppUpdate>.latestPerSource() =
    groupBy { it.source.name }.values.map { it.maxBy(AppUpdate::versionCode) }

@Composable
private fun TvDownloadProgress(update: AppUpdate) {
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
        val fraction = if (update.total > 0) (update.progress.toFloat() / update.total).coerceIn(0f, 1f) else null
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(if (fraction == 1f) R.string.preparing_installation else R.string.downloading_progress),
                style = MaterialTheme.typography.labelMedium)
            Text(if (fraction == null) update.source.name else "${update.source.name} · ${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TvUpdateMenu(
    app: AppUpdate,
    alternatives: List<AppUpdate>,
    onIgnoreApp: (AppUpdate) -> Unit,
    onIgnoreAppFromSource: (AppUpdate) -> Unit,
    onIgnoreVersion: (AppUpdate) -> Unit,
    onIgnoreVersionFromSource: (AppUpdate) -> Unit
) = Box {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.MoreVert, stringResource(R.string.more_actions))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ignore_app_cd)) },
            onClick = { expanded = false; onIgnoreApp(app) }
        )
        alternatives.latestPerSource().forEach { update ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ignore_app_source, update.source.name)) },
                onClick = { expanded = false; onIgnoreAppFromSource(update) }
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        DropdownMenuItem(
            text = { Text(stringResource(R.string.ignore_version_all_sources)) },
            onClick = { expanded = false; onIgnoreVersion(app) }
        )
        alternatives.latestPerSource().forEach { update ->
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ignore_version_source, update.version, update.source.name)) },
                onClick = { expanded = false; onIgnoreVersionFromSource(update) }
            )
        }
    }
}

@Composable
fun TvUpdateItem(
    app: AppUpdate,
    alternatives: List<AppUpdate> = listOf(app),
    onInstall: (AppUpdate) -> Unit = {},
    onIgnoreVersion: (AppUpdate) -> Unit = {},
    onIgnoreVersionFromSource: (AppUpdate) -> Unit = {},
    onIgnoreApp: (AppUpdate) -> Unit = {},
    onIgnoreAppFromSource: (AppUpdate) -> Unit = {},
    onCancel: () -> Unit = {},
    onOpenSource: (AppUpdate) -> Unit = {}
) = Card {
    Column {
        TvCommonItem(app.packageName, app.name, app.version, app.oldVersion, app.versionCode, app.oldVersionCode)
        WhatsNew(app.whatsNew)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvOpenSourceButton(app, alternatives, onOpenSource)
            TvInstallButton(app, alternatives, onInstall, onCancel, onOpenSource)
            TvUpdateMenu(app, alternatives, onIgnoreApp, onIgnoreAppFromSource, onIgnoreVersion, onIgnoreVersionFromSource)
        }
        alternatives.firstOrNull { it.isInstalling }?.let { TvDownloadProgress(it) }
    }
}

@Composable
fun TvSearchItem(
    app: AppUpdate,
    onInstall: (String) -> Unit = {},
    onOpenSource: (AppUpdate) -> Unit = {},
    onCancel: () -> Unit = {}
) = Card {
    Column {
        TvCommonItem(app.packageName, app.name, app.version, app.oldVersion, app.versionCode, app.oldVersionCode, app.iconUri, true)
        WhatsNew(app.whatsNew)
        Box {
            TvSourceIcon(app)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TvOpenSourceButton(app, onOpenSource = onOpenSource)
                TvInstallButton(app, listOf(app), { onInstall(it.packageName) }, onCancel, onOpenSource)
            }
        }
        if (app.isInstalling) TvDownloadProgress(app)
    }
}

@Composable
fun WhatsNew(whatsNew: String) {
    if (whatsNew.isNotEmpty()) {
        val text = HtmlCompat
            .fromHtml(
                whatsNew.trim().replace("&lt;br&gt;", "<br>", ignoreCase = true),
                HtmlCompat.FROM_HTML_MODE_COMPACT
            )
            .toAnnotatedString()
        ExpandingAnnotatedText(text, Modifier.padding(8.dp).fillMaxWidth())
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

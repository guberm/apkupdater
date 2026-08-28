package com.apkupdater.ui.component

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.apkupdater.util.to2f
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
    onCancel: () -> Unit = {}
) = Box {
    var expanded by remember { mutableStateOf(false) }
    val installing = alternatives.firstOrNull { it.isInstalling }
    ElevatedButton(
        modifier = Modifier
            .padding(bottom = 8.dp)
            .widthIn(min = 64.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        onClick = {
            if (installing != null) onCancel()
            else if (alternatives.size > 1) expanded = true
            else onInstall(app)
        }
    ) {
        if (installing != null) {
            if (installing.total != 0L && installing.progress != 0L) {
                val p = (installing.progress.toFloat() / installing.total) * 100f
                Text("${p.to2f()}%", maxLines = 1)
            } else {
                CircularProgressIndicator(Modifier.size(24.dp))
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                alternatives.forEach { alt ->
                    SourceIcon(alt.source, Modifier.size(20.dp))
                }
            }
        }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        alternatives.forEach { alt ->
            DropdownMenuItem(
                text = { SmallText(alt.version) },
                leadingIcon = { SourceIcon(alt.source, Modifier.size(20.dp)) },
                onClick = { expanded = false; onInstall(alt) }
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
    val sources = alternatives.latestPerSource().filter { it.sourceUrl.isNotBlank() }
    ElevatedButton(
        modifier = Modifier.padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        enabled = sources.isNotEmpty(),
        onClick = {
            if (sources.size == 1) onOpenSource(sources.first()) else expanded = true
        }
    ) {
        Text(stringResource(R.string.open_source))
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

@OptIn(ExperimentalLayoutApi::class)
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
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
        ) {
            TvOpenSourceButton(app, alternatives, onOpenSource)
            TvIgnoreAppButton(app, alternatives, onIgnoreApp, onIgnoreAppFromSource)
            TvIgnoreVersionButton(app, alternatives, onIgnoreVersion, onIgnoreVersionFromSource)
            TvInstallButton(app, alternatives, onInstall, onCancel)
        }
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
                if (app.link != Link.Empty) {
                    TvInstallButton(app, listOf(app), { onInstall(it.packageName) }, onCancel)
                }
            }
        }
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

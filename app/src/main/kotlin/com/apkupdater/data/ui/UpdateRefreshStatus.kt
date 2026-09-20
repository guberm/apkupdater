package com.apkupdater.data.ui

import android.net.Uri

enum class SourceStatusState {
    Loading,
    Success,
    Cached,
    Failed,
    Skipped
}

data class SourceStatus(
    val name: String,
    val state: SourceStatusState,
    val updateCount: Int = 0,
    val detail: String = ""
)

data class UpdatesRefreshStatus(
    val isRefreshing: Boolean = false,
    val installedCount: Int = 0,
    val enabledSourceCount: Int = 0,
    val sourceStatuses: List<SourceStatus> = emptyList(),
    val updateCount: Int = 0,
    val lastRefreshAt: Long = 0L
) {
    val failedSources: Set<String>
        get() = sourceStatuses.filter { it.state == SourceStatusState.Failed }.mapTo(mutableSetOf(), SourceStatus::name)
}

data class CachedUpdate(
    val name: String,
    val packageName: String,
    val version: String,
    val oldVersion: String,
    val versionCode: Long,
    val oldVersionCode: Long,
    val sourceName: String,
    val sourceResourceId: Int,
    val iconUrl: String = "",
    val linkUrl: String = "",
    val whatsNew: String = "",
    val sourceUrl: String = "",
    val linkType: String = "url"
)

data class CachedSourceResult(
    val sourceName: String,
    val checkedAt: Long,
    val updates: List<CachedUpdate>
)

fun AppUpdate.toCachedUpdate() = CachedUpdate(
    name = name,
    packageName = packageName,
    version = version,
    oldVersion = oldVersion,
    versionCode = versionCode,
    oldVersionCode = oldVersionCode,
    sourceName = source.name,
    sourceResourceId = source.resourceId,
    iconUrl = iconUri.toString().takeUnless { it == Uri.EMPTY.toString() }.orEmpty(),
    linkUrl = when (val value = link) {
        is Link.Url -> value.link
        is Link.Xapk -> value.link
        else -> ""
    },
    linkType = when (link) {
        is Link.Xapk -> "xapk"
        is Link.Play -> "play"
        else -> "url"
    },
    whatsNew = whatsNew,
    sourceUrl = sourceUrl
)

fun CachedUpdate.toAppUpdate(playLink: ((String) -> Link)? = null) = AppUpdate(
    name = name,
    packageName = packageName,
    version = version,
    oldVersion = oldVersion,
    versionCode = versionCode,
    oldVersionCode = oldVersionCode,
    source = listOf(ApkMirrorSource, GitHubSource, GitLabSource, FdroidSource, IzzySource,
        AptoideSource, ApkPureSource, ApkComboSource, UptodownSource, PlaySource)
        .firstOrNull { it.name == sourceName } ?: Source(sourceName, FdroidSource.resourceId),
    iconUri = iconUrl.takeIf(String::isNotBlank)?.let(Uri::parse) ?: Uri.EMPTY,
    link = when {
        sourceName == PlaySource.name -> playLink?.invoke(packageName) ?: Link.Empty
        linkUrl.isBlank() -> Link.Empty
        linkType == "xapk" -> Link.Xapk(linkUrl)
        else -> Link.Url(linkUrl)
    },
    whatsNew = whatsNew,
    sourceUrl = sourceUrl
)

fun CachedSourceResult.toAppUpdates(playLink: ((String) -> Link)? = null) = updates.map { it.toAppUpdate(playLink) }

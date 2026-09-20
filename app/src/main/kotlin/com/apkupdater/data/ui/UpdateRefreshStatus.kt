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
    val sourceUrl: String = ""
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
    linkUrl = (link as? Link.Url)?.link.orEmpty(),
    whatsNew = whatsNew,
    sourceUrl = sourceUrl
)

fun CachedUpdate.toAppUpdate() = AppUpdate(
    name = name,
    packageName = packageName,
    version = version,
    oldVersion = oldVersion,
    versionCode = versionCode,
    oldVersionCode = oldVersionCode,
    source = Source(sourceName, sourceResourceId),
    iconUri = iconUrl.takeIf(String::isNotBlank)?.let(Uri::parse) ?: Uri.EMPTY,
    link = linkUrl.takeIf(String::isNotBlank)?.let(Link::Url) ?: Link.Empty,
    whatsNew = whatsNew,
    sourceUrl = sourceUrl
)

fun CachedSourceResult.toAppUpdates() = updates.map(CachedUpdate::toAppUpdate)

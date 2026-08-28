package com.apkupdater.data.fdroid

import androidx.core.net.toUri
import com.apkupdater.data.ui.AppInstalled
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.data.ui.Link
import com.apkupdater.data.ui.Source

data class FdroidUpdate(
    val apk: FdroidPackage,
    val app: FdroidApp
)

fun FdroidUpdate.toAppUpdate(current: AppInstalled?, source: Source, url: String) = AppUpdate(
    name = app.localized["en-US"]?.name ?: app.name,
    packageName = app.packageName,
    version = apk.versionName,
    oldVersion = current?.version ?: "?",
    versionCode = apk.versionCode,
    oldVersionCode = current?.versionCode ?: 0L,
    source = source,
    iconUri = if(app.icon.isEmpty())
        "https://f-droid.org/assets/ic_repo_app_default.png".toUri()
    else
        "${url}icons-640/${app.icon}".toUri(),
    link = Link.Url("$url${apk.apkName}"),
    whatsNew = if (current != null) app.localized["en-US"]?.whatsNew.orEmpty() else app.localized["en-US"]?.summary.orEmpty(),
    sourceUrl = if (url.contains("izzysoft", ignoreCase = true)) {
        "https://apt.izzysoft.de/fdroid/index/apk/${app.packageName}"
    } else {
        "https://f-droid.org/packages/${app.packageName}/"
    }
)

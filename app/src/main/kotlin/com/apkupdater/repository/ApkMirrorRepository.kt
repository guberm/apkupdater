package com.apkupdater.repository

import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import com.apkupdater.data.apkmirror.AppExistsRequest
import com.apkupdater.data.apkmirror.AppExistsResponseApk
import com.apkupdater.data.apkmirror.AppExistsResponseData
import com.apkupdater.data.apkmirror.toAppUpdate
import com.apkupdater.data.ui.ApkMirrorSource
import com.apkupdater.data.ui.AppInstalled
import com.apkupdater.data.ui.AppUpdate
import com.apkupdater.data.ui.Link
import com.apkupdater.data.ui.getApp
import com.apkupdater.data.ui.getPackageNames
import com.apkupdater.data.ui.getSignature
import com.apkupdater.data.ui.getVersionCode
import com.apkupdater.prefs.Prefs
import com.apkupdater.service.ApkMirrorService
import com.apkupdater.util.combine
import com.apkupdater.util.orFalse
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import org.jsoup.Jsoup


class ApkMirrorRepository(
    private val service: ApkMirrorService,
    private val prefs: Prefs
) {

    private val deviceArch = when {
        Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "arm64-v8a"
        Build.SUPPORTED_ABIS.contains("x86_64") -> "x86_64"
        Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "armeabi-v7a"
        Build.SUPPORTED_ABIS.contains("x86") -> "x86"
        else -> "armeabi-v7a"
    }

    companion object {
        val archOptions = listOf("auto", "arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    }

    private val api = Build.VERSION.SDK_INT

    suspend fun updates(apps: List<AppInstalled>) = flow {
        apps.chunked(100)
            .map { appExists(it.getPackageNames()) }
            .combine { all -> emit(parseUpdates(all.flatMap { it }, apps)) }
            .collect()
    }

    suspend fun search(text: String) = flow {
        val baseUrl = "https://www.apkmirror.com"
        val searchQuery = "/?post_type=app_release&searchtype=app&s="
        val doc = Jsoup.connect("$baseUrl$searchQuery$text").get()
        val row = doc.select("div.appRow")
        val developers = row.select("a.byDeveloper").drop(1)
        val titles = row.select("h5.appRowTitle")
        val images = row.select("img").drop(1)
        val count = minOf(developers.size, titles.size, images.size)
        val result = (0 until count).map {
            AppUpdate(
                name = titles[it].attr("title"),
                link = Link.Empty,
                iconUri = "$baseUrl${images[it].attr("src")}".replace("=32", "=128").toUri(),
                version = "?",
                oldVersion = "?",
                versionCode = 0L,
                oldVersionCode = 0L,
                source = ApkMirrorSource,
                packageName = developers[it].text(), // Developer name in this case
                sourceUrl = "$baseUrl${titles[it].selectFirst("a")?.attr("href")}"
            )
        }
        emit(Result.success(result))
    }.catch {
        emit(Result.failure(it))
        Log.e("ApkMirrorRepository", "Error searching.", it)
    }

    private fun appExists(apps: List<String>) = flow {
        emit(service.appExists(AppExistsRequest(apps, buildIgnoreList())).data)
    }.catch {
        emit(emptyList())
        Log.e("ApkMirrorRepository", "Error getting updates.", it)
    }

    private fun parseUpdates(updates: List<AppExistsResponseData>, apps: List<AppInstalled>)
    = updates
        .filter { it.exists == true }
        .mapNotNull { data ->
            data.apks
                .asSequence()
                .filter { filterSignature(it, apps.getSignature(data.pname))}
                .filter { filterArch(it) }
                .filter { it.versionCode > apps.getVersionCode(data.pname) }
                .filter { filterMinApi(it) }
                .filter { filterAndroidTv(it) }
                .filter { filterWearOS(it) }
                .maxByOrNull { it.versionCode }
                ?.let { apk -> apps.getApp(data.pname)?.let { app -> apk.toAppUpdate(app, data.release) } }
        }

    private fun filterSignature(apk: AppExistsResponseApk, signature: String?) = when {
        apk.signaturesSha1.isNullOrEmpty() -> true
        apk.signaturesSha1.contains(signature) -> true
        else -> false
    }

    private fun filterArch(app: AppExistsResponseApk): Boolean {
        if (app.arches.isEmpty()) return true
        if (app.arches.contains("universal") || app.arches.contains("noarch")) return true
        val archIndex = prefs.apkMirrorArch.get()
        return if (archIndex == 0) {
            app.arches.find { a -> Build.SUPPORTED_ABIS.contains(a) } != null ||
            app.arches.find { a -> a.contains(deviceArch) } != null
        } else {
            val selectedArch = archOptions.getOrElse(archIndex) { deviceArch }
            app.arches.contains(selectedArch)
        }
    }

    private fun filterAndroidTv(apk: AppExistsResponseApk): Boolean {
        return apk.capabilities?.contains("leanback_standalone").orFalse()
                || apk.capabilities?.contains("leanback").orFalse()
    }

    private fun filterWearOS(apk: AppExistsResponseApk): Boolean {
        // For the moment filter out all standalone Wear OS apps
        if (apk.capabilities?.contains("wear_standalone").orFalse()) {
            return false
        }
        return true
    }

    private fun filterMinApi(apk: AppExistsResponseApk) = runCatching {
        when {
            apk.minapi.toInt() > api -> false
            else -> true
        }
    }.getOrDefault(true)

    private fun buildIgnoreList() = mutableListOf<String>().apply {
        if (prefs.ignoreAlpha.get()) add("alpha")
        if (prefs.ignoreBeta.get()) add("beta")
    }

}

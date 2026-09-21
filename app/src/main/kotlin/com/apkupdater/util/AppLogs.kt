package com.apkupdater.util

import java.io.File
import com.apkupdater.data.github.GitHubScanReport
import com.apkupdater.data.github.diagnosticTime

internal fun readAppLogs(diagnostics: List<GitHubScanReport> = emptyList()): String = formatAppLogs(runCatching { Runtime.getRuntime()
    .exec("logcat -d")
    .inputStream
    .bufferedReader()
    .use { it.readText() } }.getOrDefault("Logcat unavailable"), diagnostics)

internal fun formatAppLogs(logcat: String, diagnostics: List<GitHubScanReport>): String = buildString {
    appendLine("=== Saved GitHub diagnostics (last 20 scans; UTC) ===")
    if (diagnostics.isEmpty()) appendLine("No saved GitHub scans yet.")
    diagnostics.forEach {
        appendLine("${diagnosticTime(it.checkedAt)} | APK Updater ${it.version} | ${it.operation} | ${it.state}")
        appendLine(it.description())
    }
    appendLine("=== Android logcat ===")
    append(logcat)
}

internal fun writeAppLogs(directory: File, logs: String): File =
    File(directory.apply(File::mkdirs), "apkupdater-logs.txt").apply { writeText(logs) }

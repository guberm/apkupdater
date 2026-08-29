package com.apkupdater.util

import java.io.File

internal fun readAppLogs(): String = Runtime.getRuntime()
    .exec("logcat -d")
    .inputStream
    .bufferedReader()
    .use { it.readText() }

internal fun writeAppLogs(directory: File, logs: String): File =
    File(directory.apply(File::mkdirs), "apkupdater-logs.txt").apply { writeText(logs) }

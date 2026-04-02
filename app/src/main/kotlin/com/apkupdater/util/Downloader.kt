package com.apkupdater.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import okhttp3.Call
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap


class Downloader(
    private val client: OkHttpClient,
    private val apkPureClient: OkHttpClient,
    private val auroraClient: OkHttpClient,
    private val dir: File,
    private val context: Context
) {

    private val activeCalls = ConcurrentHashMap<Int, Call>()

    fun cancelDownload(id: Int) {
        activeCalls.remove(id)?.cancel()
    }

    fun download(url: String): File {
        val file = File(dir, randomUUID())
        client.newCall(downloadRequest(url)).execute().use {
            if (it.isSuccessful) {
                it.body.byteStream().copyTo(file.outputStream())
            }
        }
        return file
    }

    /** Downloads [url] to a temp file using the correct client for the URL, returns the file. Retries on transient IO errors. */
    fun downloadFile(url: String, onProgress: ((Long, Long) -> Unit)? = null): File {
        val clientName = when {
            url.contains("apkpure") -> "apkPureClient"
            else -> "auroraClient"
        }
        val c = when {
            url.contains("apkpure") -> apkPureClient
            // Play Store and all other URLs use auroraClient (long timeouts, proper UA)
            else -> auroraClient
        }
        var lastException: Exception? = null
        repeat(3) { attempt ->
            val file = File(dir, randomUUID())
            Log.d("Downloader", "downloadFile: attempt=${attempt + 1} url=$url client=$clientName dest=${file.absolutePath}")
            try {
                c.newCall(downloadFileRequest(url)).execute().use { response ->
                    Log.d("Downloader", "downloadFile: response code=${response.code} success=${response.isSuccessful} attempt=${attempt + 1}")
                    if (response.isSuccessful) {
                        val total = response.body.contentLength()
                        val input = response.body.byteStream()
                        val output = file.outputStream()
                        if (onProgress == null) {
                            input.copyTo(output)
                        } else {
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var bytesRead = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                bytesRead += read
                                onProgress(bytesRead, if (total >= 0) total else bytesRead)
                            }
                        }
                        output.close()
                        Log.d("Downloader", "downloadFile: written ${file.length()} bytes -> ${file.absolutePath}")
                        return file
                    } else {
                        Log.e("Downloader", "downloadFile: FAILED code=${response.code} url=$url")
                        file.delete()
                    }
                }
            } catch (e: java.io.IOException) {
                Log.e("Downloader", "downloadFile: IOException on attempt ${attempt + 1} url=$url", e)
                file.delete()
                lastException = e
            }
        }
        Log.e("Downloader", "downloadFile: all retries exhausted for url=$url")
        lastException?.let { throw it }
        return File(dir, randomUUID()) // empty file fallback
    }

    fun downloadStream(url: String, downloadId: Int = -1): InputStream? = runCatching {
        val clientName = when {
            url.contains("apkpure") -> "apkPureClient"
            url.contains("aurora") -> "auroraClient"
            else -> "client"
        }
        val c = when {
            url.contains("apkpure") -> apkPureClient
            url.contains("aurora") -> auroraClient
            else -> client
        }
        Log.d("Downloader", "downloadStream: url=$url downloadId=$downloadId client=$clientName")
        val call = c.newCall(downloadRequest(url))
        if (downloadId >= 0) activeCalls[downloadId] = call
        val response = call.execute()
        if (downloadId >= 0) activeCalls.remove(downloadId)
        if (response.isSuccessful) {
            Log.d("Downloader", "downloadStream: success code=${response.code} url=$url")
            return response.body.byteStream()
        } else {
            response.close()
            Log.e("Downloader", "downloadStream: FAILED code=${response.code} url=$url")
        }
        return null
    }.getOrElse {
        if (downloadId >= 0) activeCalls.remove(downloadId)
        Log.e("Downloader", "downloadStream: exception url=$url", it)
        null
    }

    /** Downloads [url] into the SAF tree [treeUri] with the given [filename]. Returns the new document URI, or null on failure. */
    fun downloadToUri(url: String, treeUri: Uri, filename: String, onProgress: ((Long, Long) -> Unit)? = null): Uri? = runCatching {
        Log.d("Downloader", "downloadToUri: url=$url treeUri=$treeUri filename=$filename")
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
        Log.d("Downloader", "downloadToUri: treeDocId=$treeDocId docUri=$docUri")
        val newDocUri = DocumentsContract.createDocument(
            context.contentResolver, docUri,
            "application/vnd.android.package-archive", filename
        )
        if (newDocUri == null) {
            Log.e("Downloader", "downloadToUri: createDocument returned null for treeUri=$treeUri")
            return null
        }
        Log.d("Downloader", "downloadToUri: newDocUri=$newDocUri")

        // Download to a temp file first so the full content is available before writing to SAF
        // Use downloadFile() so the correct HTTP client is chosen per URL (apkpure, aurora, etc.)
        val tempFile = downloadFile(url, onProgress)
        Log.d("Downloader", "downloadToUri: tempFile=${tempFile.absolutePath} exists=${tempFile.exists()} size=${tempFile.length()}")
        if (!tempFile.exists() || tempFile.length() == 0L) {
            tempFile.delete()
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, newDocUri) }
            Log.e("Downloader", "downloadToUri: tempFile empty or missing — aborting")
            return null
        }

        val outStream = context.contentResolver.openOutputStream(newDocUri)
        if (outStream == null) {
            tempFile.delete()
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, newDocUri) }
            Log.e("Downloader", "downloadToUri: openOutputStream returned null for $newDocUri")
            return null
        }
        outStream.use { output -> tempFile.inputStream().use { input -> input.copyTo(output) } }
        tempFile.delete()
        Log.d("Downloader", "downloadToUri: success newDocUri=$newDocUri")
        newDocUri
    }.getOrElse {
        Log.e("Downloader", "downloadToUri: exception url=$url treeUri=$treeUri", it)
        null
    }

    /** Copies a local [file] into the SAF tree [treeUri] with the given [filename]. Returns the new document URI, or null on failure. */
    fun copyToUri(file: File, treeUri: Uri, filename: String): Uri? = runCatching {
        Log.d("Downloader", "copyToUri: file=${file.name} size=${file.length()} treeUri=$treeUri filename=$filename")
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
        val newDocUri = DocumentsContract.createDocument(
            context.contentResolver, docUri,
            "application/vnd.android.package-archive", filename
        ) ?: return null
        val outStream = context.contentResolver.openOutputStream(newDocUri) ?: run {
            Log.e("Downloader", "copyToUri: openOutputStream returned null for $newDocUri")
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, newDocUri) }
            return null
        }
        outStream.use { output -> file.inputStream().use { input -> input.copyTo(output) } }
        Log.d("Downloader", "copyToUri: success newDocUri=$newDocUri")
        newDocUri
    }.getOrElse {
        Log.e("Downloader", "copyToUri: exception file=${file.name} treeUri=$treeUri", it)
        null
    }

    private fun downloadRequest(url: String) = Request.Builder().url(url).build()

    /** Request with cache disabled — prevents OkHttp CacheInterceptor from buffering large binaries. */
    private fun downloadFileRequest(url: String) = Request.Builder()
        .url(url)
        .cacheControl(CacheControl.Builder().noStore().build())
        .build()

}

package com.apkupdater.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.CancellationException
import okhttp3.Call
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
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
    private val cancelledDownloads = ConcurrentHashMap.newKeySet<Int>()

    fun clearDownloadCache(): Int = clearDownloadCache(dir)

    fun cancelDownload(id: Int) {
        cancelledDownloads.add(id)
        activeCalls.remove(id)?.cancel()
    }

    fun download(url: String): File {
        val resolved = resolveDownloadUrl(url)
        val file = File(dir, randomUUID())
        val c = if (ApkMirrorDownloadResolver.isApkMirrorUrl(resolved.url)) auroraClient else client
        try {
            c.newCall(downloadRequest(resolved.url, resolved.referer)).execute().use { response ->
                if (response.isSuccessful && !response.isUnexpectedApkMirrorHtml(url)) {
                    response.body.byteStream().use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }
                } else {
                    throw IOException("Download failed with HTTP ${response.code}: $url")
                }
            }
            if (file.length() == 0L) throw IOException("Downloaded file is empty: $url")
            return file
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    /** Downloads [url] to a temp file using the correct client for the URL, returns the file. Retries on transient IO errors. */
    fun downloadFile(url: String, onProgress: ((Long, Long) -> Unit)? = null): File {
        val resolved = resolveDownloadUrl(url)
        val (clientName, c) = downloadClient(resolved.url)
        var lastException: Exception? = null
        repeat(3) { attempt ->
            val file = File(dir, randomUUID())
            Log.d("Downloader", "downloadFile: attempt=${attempt + 1} url=${resolved.url} client=$clientName dest=${file.absolutePath}")
            try {
                c.newCall(downloadFileRequest(resolved.url, resolved.referer)).execute().use { response ->
                    Log.d("Downloader", "downloadFile: response code=${response.code} success=${response.isSuccessful} attempt=${attempt + 1}")
                    if (response.isSuccessful && !response.isUnexpectedApkMirrorHtml(url)) {
                        val total = response.body.contentLength()
                        response.body.byteStream().use { input ->
                            file.outputStream().use { output ->
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
                            }
                        }
                        if (file.length() == 0L) throw IOException("Downloaded file is empty: ${resolved.url}")
                        Log.d("Downloader", "downloadFile: written ${file.length()} bytes -> ${file.absolutePath}")
                        return file
                    } else {
                        Log.e("Downloader", "downloadFile: FAILED code=${response.code} url=${resolved.url}")
                        file.delete()
                    }
                }
            } catch (e: java.io.IOException) {
                Log.e("Downloader", "downloadFile: IOException on attempt ${attempt + 1} url=${resolved.url}", e)
                file.delete()
                lastException = e
            }
        }
        Log.e("Downloader", "downloadFile: all retries exhausted for url=${resolved.url}")
        throw lastException ?: IOException("Download failed: ${resolved.url}")
    }

    fun downloadStream(url: String, downloadId: Int = -1): InputStream? {
        val resolved = resolveDownloadUrl(url)
        val (clientName, c) = downloadClient(resolved.url)
        if (downloadId >= 0) cancelledDownloads.remove(downloadId)

        repeat(3) { attempt ->
            if (downloadId >= 0 && cancelledDownloads.contains(downloadId)) {
                throw CancellationException("Download cancelled: $downloadId")
            }
            val call = c.newCall(downloadRequest(resolved.url, resolved.referer))
            if (downloadId >= 0) activeCalls[downloadId] = call
            Log.d("Downloader", "downloadStream: attempt=${attempt + 1} url=${resolved.url} downloadId=$downloadId client=$clientName")
            try {
                val response = call.execute()
                if (response.isSuccessful && !response.isUnexpectedApkMirrorHtml(url)) {
                    Log.d("Downloader", "downloadStream: success code=${response.code} attempt=${attempt + 1} url=${resolved.url}")
                    val bodyStream = response.body.byteStream()
                    return object : FilterInputStream(bodyStream) {
                        override fun close() {
                            try {
                                super.close()
                            } finally {
                                response.close()
                                if (downloadId >= 0) activeCalls.remove(downloadId, call)
                            }
                        }
                    }
                } else {
                    val code = response.code
                    response.close()
                    if (downloadId >= 0) activeCalls.remove(downloadId, call)
                    Log.e("Downloader", "downloadStream: FAILED code=$code attempt=${attempt + 1} url=${resolved.url}")
                }
            } catch (error: IOException) {
                if (downloadId >= 0) activeCalls.remove(downloadId, call)
                if (downloadId >= 0 && cancelledDownloads.contains(downloadId)) {
                    throw CancellationException("Download cancelled: $downloadId", error)
                }
                Log.e("Downloader", "downloadStream: IOException attempt=${attempt + 1} url=${resolved.url}", error)
            }
        }
        Log.e("Downloader", "downloadStream: all retries exhausted url=${resolved.url}")
        return null
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

    private fun resolveDownloadUrl(url: String) = runCatching {
        val resolverClient = if (ApkMirrorDownloadResolver.isApkMirrorUrl(url)) auroraClient else client
        ApkMirrorDownloadResolver.resolve(resolverClient, url)
    }.getOrElse {
        Log.e("Downloader", "resolveDownloadUrl: failed url=$url", it)
        ResolvedDownloadUrl(url)
    }

    private fun downloadClient(url: String) = when {
        url.contains("apkpure") -> "apkPureClient" to apkPureClient
        else -> "auroraClient" to auroraClient
    }

    private fun Response.isUnexpectedApkMirrorHtml(originalUrl: String): Boolean {
        if (!ApkMirrorDownloadResolver.isApkMirrorUrl(originalUrl)) return false
        val type = body.contentType()
        return type?.type == "text" && type.subtype.contains("html", ignoreCase = true)
    }

    private fun downloadRequest(url: String, referer: String? = null) = Request.Builder()
        .url(url)
        .apply { referer?.let { header("Referer", it) } }
        .build()

    /** Request with cache disabled — prevents OkHttp CacheInterceptor from buffering large binaries. */
    private fun downloadFileRequest(url: String, referer: String? = null) = Request.Builder()
        .url(url)
        .apply { referer?.let { header("Referer", it) } }
        .cacheControl(CacheControl.Builder().noStore().build())
        .build()

}

internal fun clearDownloadCache(directory: File): Int =
    directory.listFiles()?.count { it.deleteRecursively() } ?: 0

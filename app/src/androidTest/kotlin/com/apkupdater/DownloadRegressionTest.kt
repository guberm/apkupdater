package com.apkupdater

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.apkupdater.util.Downloader
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DownloadRegressionTest {
    @Test fun interruptedBodyIsRetriedFromStartWithProgressAndNoPartialFiles() {
        var attempts = 0
        val progress = mutableListOf<Long>()
        withDownloader(body = {
            if (++attempts == 1) object : ResponseBody() {
                override fun contentType() = null
                override fun contentLength() = 100L
                override fun source() = object : ForwardingSource(Buffer().writeUtf8("partial")) {
                    var readOnce = false
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        if (readOnce) throw IOException("stream was reset: CANCEL")
                        readOnce = true
                        return super.read(sink, byteCount)
                    }
                }.buffer()
            } else "complete APK bytes".toResponseBody()
        }) { downloader, directory ->
            val file = downloader.downloadFile("https://example.com/app.apk", 42) { bytes, _ -> progress += bytes }
            assertEquals("complete APK bytes", file.readText())
            assertEquals(2, attempts)
            assertTrue(progress.any { it > 0 })
            assertEquals(2, progress.count { it == 0L })
            assertEquals(listOf(file), directory.listFiles()!!.toList())
        }
    }

    @Test fun cancellationIsNotRetriedOrReportedAsIoFailure() {
        var attempts = 0
        withDownloader(body = { attempts++; ByteArray(32_768).toResponseBody() }) { downloader, directory ->
            val result = runCatching {
                downloader.downloadFile("https://example.com/app.apk", 43) { bytes, _ ->
                    if (bytes > 0) downloader.cancelDownload(43)
                }
            }
            assertTrue(result.exceptionOrNull() is CancellationException)
            assertEquals(1, attempts)
            assertTrue(directory.listFiles()!!.isEmpty())
        }
    }

    @Test fun missingDownloadIsNotRetried() {
        var attempts = 0
        withDownloader(code = 404, body = { attempts++; "missing".toResponseBody() }) { downloader, directory ->
            assertTrue(runCatching { downloader.downloadFile("https://example.com/app.apk") }.isFailure)
            assertEquals(1, attempts)
            assertTrue(directory.listFiles()!!.isEmpty())
        }
    }

    private fun withDownloader(code: Int = 200, body: () -> ResponseBody, test: (Downloader, File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "download-regression-${System.nanoTime()}").apply { mkdirs() }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(code).message("test").body(body()).build()
        }.build()
        try { test(Downloader(client, client, client, directory, context), directory) }
        finally { directory.deleteRecursively() }
    }
}

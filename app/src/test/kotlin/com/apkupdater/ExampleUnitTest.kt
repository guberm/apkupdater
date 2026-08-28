package com.apkupdater

import com.apkupdater.util.clearDownloadCache
import org.junit.Test

import org.junit.Assert.*
import java.nio.file.Files

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun clearDownloadCacheDeletesOnlyFilesInsideTheDownloadDirectory() {
        val directory = Files.createTempDirectory("apkupdater-downloads").toFile()
        try {
            directory.resolve("one.apk").writeText("one")
            directory.resolve("two.apk").writeText("two")

            assertEquals(2, clearDownloadCache(directory))
            assertTrue(directory.exists())
            assertTrue(directory.listFiles().isNullOrEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }
}

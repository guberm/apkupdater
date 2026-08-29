package com.apkupdater

import com.apkupdater.util.writeAppLogs
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class AppLogsTest {

    @Test
    fun writesShareableLogFile() {
        val directory = Files.createTempDirectory("apkupdater-logs").toFile()

        val file = writeAppLogs(directory, "first line\nsecond line")

        assertEquals("apkupdater-logs.txt", file.name)
        assertEquals("first line\nsecond line", file.readText())
        directory.deleteRecursively()
    }
}

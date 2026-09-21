package com.apkupdater

import com.apkupdater.data.github.*
import com.apkupdater.data.ui.SourceStatus
import com.apkupdater.data.ui.SourceStatusState
import com.apkupdater.data.ui.UpdatesRefreshStatus
import com.apkupdater.util.formatAppLogs
import com.google.gson.Gson
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class GitHubDiagnosticsTest {
    @Test fun confirmedQuotaIncludesResetButNeverResponseBodyOrHeaders() {
        val failure = gitHubFailure("owner/repo", http(403,
            "X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "1790009572",
            "Authorization" to "secret-token"))
        assertEquals(1790009572000L, failure.retryAt)
        assertTrue(failure.description().contains("rate limit reached"))
        assertTrue(failure.description().contains("UTC"))
        assertFalse(failure.description().contains("secret"))
    }

    @Test fun genericForbiddenDoesNotInventQuotaOrReset() {
        val failure = gitHubFailure("owner/repo", http(403, "X-RateLimit-Reset" to "1790009572"))
        assertEquals("Access forbidden (rate limit not confirmed)", failure.reason)
        assertNull(failure.retryAt)
        assertEquals(403, failure.httpCode)
    }

    @Test fun retryAfterAndMalformedResetAreHandled() {
        assertEquals(31000L, gitHubFailure("owner/repo", http(429, "Retry-After" to "30"), 1000L).retryAt)
        assertNull(gitHubFailure("owner/repo", http(403, "X-RateLimit-Remaining" to "0",
            "X-RateLimit-Reset" to "9223372036854775807")).retryAt)
    }

    @Test fun partialIsBasedOnSuccessfulChecksAndRemainsRetryable() {
        val report = GitHubScanReport(1, "test", "Updates", 2, 1,
            listOf(GitHubFailure("owner/repo", "Repository not found", 404)))
        assertEquals(SourceStatusState.Partial, report.state)
        assertEquals(SourceStatusState.Failed, report.copy(successfulChecks = 0).state)
        assertEquals(setOf("GitHub"), UpdatesRefreshStatus(sourceStatuses = listOf(
            SourceStatus("GitHub", report.state, 0, report.description()))).failedSources)
    }

    @Test fun persistedDiagnosticsExportEvenWithEmptyLogcat() {
        val report = GitHubScanReport(1, "3.1.27", "Updates", 2, 1,
            listOf(gitHubFailure("owner/repo", http(404))))
        val restored = Gson().fromJson(Gson().toJson(report), GitHubScanReport::class.java)
        val exported = formatAppLogs("", listOf(restored))
        assertTrue(exported.contains("3.1.27 | Updates | Partial"))
        assertTrue(exported.contains("owner/repo: HTTP 404"))
        assertEquals(report, restored)
    }

    private fun http(code: Int, vararg headers: Pair<String, String>): HttpException {
        val raw = okhttp3.Response.Builder().request(Request.Builder().url("https://api.github.com").build())
            .protocol(Protocol.HTTP_1_1).code(code).message("error")
            .apply { headers.forEach { (name, value) -> addHeader(name, value) } }.build()
        return HttpException(Response.error<String>("secret body".toResponseBody(), raw))
    }
}

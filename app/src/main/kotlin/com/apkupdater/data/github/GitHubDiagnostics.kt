package com.apkupdater.data.github

import com.apkupdater.data.ui.SourceStatusState
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class GitHubCachedReleases(
    val repository: String,
    val checkedAt: Long,
    val releases: List<GitHubRelease>
)

data class GitHubFailure(
    val repository: String,
    val reason: String,
    val httpCode: Int? = null,
    val retryAt: Long? = null
) {
    fun description(): String = "$repository: " +
        (httpCode?.let { "HTTP $it — " } ?: "") + reason +
        (retryAt?.let { "; retry after ${diagnosticTime(it)}" } ?: "")
}

data class GitHubScanReport(
    val checkedAt: Long,
    val version: String,
    val operation: String,
    val totalChecks: Int,
    val successfulChecks: Int,
    val failures: List<GitHubFailure>
) {
    val state: SourceStatusState get() = when {
        successfulChecks == totalChecks -> SourceStatusState.Success
        successfulChecks > 0 -> SourceStatusState.Partial
        else -> SourceStatusState.Failed
    }

    fun description(): String = buildString {
        val deferred = failures.count { it.reason.contains("check deferred") }
        append("$successfulChecks/$totalChecks repositories checked successfully; ${failures.size - deferred} failed")
        if (deferred > 0) append("; $deferred deferred until quota reset")
        append("\nSaved responses may be reused for 15 minutes, or up to 24 hours while completing a quota-limited check.")
        val unfinished = totalChecks - successfulChecks - failures.size
        if (unfinished > 0) append("; $unfinished unfinished (scan interrupted)")
        failures.forEach { append("\n${it.description()}") }
    }
}

class GitHubRepositoryException(val failure: GitHubFailure, cause: Throwable) :
    Exception(failure.description(), cause)

class GitHubScanException(val report: GitHubScanReport) : IOException(report.description())

internal fun gitHubFailure(repository: String, error: Throwable, now: Long = System.currentTimeMillis()): GitHubFailure {
    val http = error as? HttpException
    val code = http?.code()
    val headers = http?.response()?.headers()
    val exhausted = headers?.get("X-RateLimit-Remaining") == "0"
    val retrySeconds = headers?.get("Retry-After")?.toLongOrNull()?.takeIf { it in 0..86_400 }
    val resetSeconds = headers?.get("X-RateLimit-Reset")?.toLongOrNull()?.takeIf { it in 1..Long.MAX_VALUE / 1000 }
    val limited = code == 429 || (code == 403 && exhausted)
    val retryAt = if (code == 403 || code == 429) {
        retrySeconds?.let { now + it * 1000 } ?: if (limited) resetSeconds?.times(1000) else null
    } else null
    // Only retain known-safe fields, never response bodies, request headers or exception messages.
    val reason = when {
        limited -> "GitHub rate limit reached"
        code == 403 -> "Access forbidden (rate limit not confirmed)"
        code == 404 -> "Repository not found or inaccessible"
        code == 401 -> "Authentication required"
        code != null -> "GitHub request failed"
        error is java.net.SocketTimeoutException -> "Network timeout"
        error is IOException -> "Network or connection failure"
        else -> "Unable to read GitHub release data"
    }
    return GitHubFailure(repository, reason, code, retryAt)
}

internal fun diagnosticTime(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US)
    .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(time))

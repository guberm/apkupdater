package com.apkupdater.util.play

import android.util.Log
import com.aurora.gplayapi.data.models.PlayResponse
import com.apkupdater.BuildConfig
import com.apkupdater.util.toSha256
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cache
import okhttp3.Credentials
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit


class PlayHttpClient(
    cache: Cache
) : IProxyHttpClient {

    companion object {
        private const val POST = "POST"
        private const val GET = "GET"
        private val credentialHeaders = setOf("authorization", "cookie", "set-cookie", "x-dfe-device-id")
    }

    private val _responseCode = MutableStateFlow(100)
    override val responseCode: StateFlow<Int> get() = _responseCode.asStateFlow()
    private val okHttpClientBuilder = OkHttpClient().newBuilder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .cache(cache)
    private var okHttpClient = okHttpClientBuilder.build()

    override fun setProxy(proxyInfo: ProxyInfo): PlayHttpClient {
        val proxy = Proxy(
            if (proxyInfo.protocol == "SOCKS") Proxy.Type.SOCKS else Proxy.Type.HTTP,
            InetSocketAddress.createUnresolved(proxyInfo.host, proxyInfo.port)
        )

        val proxyUser = proxyInfo.proxyUser
        val proxyPassword = proxyInfo.proxyPassword

        if (!proxyUser.isNullOrBlank() && !proxyPassword.isNullOrBlank()) {
            okHttpClientBuilder.proxyAuthenticator { _, response ->
                if (response.request.header("Proxy-Authorization") != null) {
                    return@proxyAuthenticator null
                }

                val credential = Credentials.basic(proxyUser, proxyPassword)
                response.request
                    .newBuilder()
                    .header("Proxy-Authorization", credential)
                    .build()
            }
        }

        okHttpClient = okHttpClientBuilder.proxy(proxy).build()
        return this
    }

    @Throws(IOException::class)
    fun post(url: String, headers: Map<String, String>, requestBody: RequestBody): PlayResponse {
        val request = Request.Builder()
            .url(url)
            .headers(headers.toHeaders())
            .method(POST, requestBody)
            .build()
        return processRequest(request)
    }

    @Throws(IOException::class)
    override fun post(
        url: String,
        headers: Map<String, String>,
        params: Map<String, String>
    ): PlayResponse {
        val request = Request.Builder()
            .url(buildUrl(url, params))
            .headers(headers.toHeaders())
            .method(POST, "".toRequestBody(null))
            .build()
        return processRequest(request)
    }

    override fun postAuth(url: String, body: ByteArray): PlayResponse {
        val requestBody = body.toRequestBody("application/json".toMediaType(), 0, body.size)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "com.aurora.store-4.4.2-56")
            .method(POST, requestBody)
            .build()
        return processRequest(request)
    }

    @Throws(IOException::class)
    override fun post(url: String, headers: Map<String, String>, body: ByteArray): PlayResponse {
        val requestBody = body.toRequestBody(
            "application/x-protobuf".toMediaType(),
            0,
            body.size
        )
        return post(url, headers, requestBody)
    }

    @Throws(IOException::class)
    override fun get(url: String, headers: Map<String, String>): PlayResponse {
        return get(url, headers, mapOf())
    }

    @Throws(IOException::class)
    override fun get(
        url: String,
        headers: Map<String, String>,
        params: Map<String, String>
    ): PlayResponse {
        val request = Request.Builder()
            .url(buildUrl(url, params))
            .headers(headers.toHeaders())
            .method(GET, null)
            .build()
        return processRequest(request)
    }

    override fun getAuth(url: String): PlayResponse {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "com.aurora.store-4.4.2-56")
            .method(GET, null)
            .build()
        return processRequest(request)
    }

    @Throws(IOException::class)
    override fun get(
        url: String,
        headers: Map<String, String>,
        paramString: String
    ): PlayResponse {
        val request = Request.Builder()
            .url(url + paramString)
            .headers(headers.toHeaders())
            .method(GET, null)
            .build()
        return processRequest(request)
    }

    private fun processRequest(request: Request): PlayResponse {
        // Reset response code as flow doesn't sends the same value twice
        _responseCode.value = 0

        Log.i("PlayHttpClient", "request ${request.logSummary()}")
        return try {
            executeWithPlayRateLimitRetry(request.method, request.url.encodedPath, { it.code }) {
                okHttpClient.newCall(request).execute().use(::buildPlayResponse)
            }
        } catch (error: IOException) {
            Log.e("PlayHttpClient", "error ${request.logSummary()}", error)
            throw error
        }
    }

    private fun buildUrl(url: String, params: Map<String, String>): HttpUrl {
        val urlBuilder = url.toHttpUrl().newBuilder()
        params.forEach {
            urlBuilder.addQueryParameter(it.key, it.value)
        }
        return urlBuilder.build()
    }

    private fun buildPlayResponse(response: Response): PlayResponse {
        val responseBytes = response.body.bytes()
        return PlayResponse(
            isSuccessful = response.isSuccessful,
            code = response.code,
            responseBytes = responseBytes,
            errorString = if (!response.isSuccessful) response.message else ""
        ).also {
            _responseCode.value = response.code
            Log.i(
                "PlayHttpClient",
                "result code=${response.code} bytes=${responseBytes.size} ${response.request.logSummary()} " +
                    "responseHeaders=${response.headers.logSummary()}"
            )
        }
    }

    private fun Request.logSummary(): String = if (BuildConfig.SENSITIVE_LOGGING) {
        "method=$method url=$url headers=$headers bodyBytes=${runCatching { body?.contentLength() }.getOrNull()}"
    } else {
        "method=$method endpoint=${url.toString().substringBefore('?')} " +
            "query=${url.queryParameterNames.sorted()} headers=${headers.names().sorted()} " +
            "credentials=${headers.credentialFingerprints()} " +
            "bodyBytes=${runCatching { body?.contentLength() }.getOrNull()}"
    }

    private fun okhttp3.Headers.logSummary(): String = if (BuildConfig.SENSITIVE_LOGGING) toString().trim() else
        "names=${names().sorted()} credentials=${credentialFingerprints()}"

    private fun okhttp3.Headers.credentialFingerprints(): Map<String, String> = names()
        .filter { it.lowercase() in credentialHeaders }
        .associateWith { values(it).joinToString("\n").toByteArray().toSha256().take(12) }
}

internal fun <T> executeWithPlayRateLimitRetry(
    method: String,
    encodedPath: String,
    responseCode: (T) -> Int,
    sleep: (Long) -> Unit = { Thread.sleep(it) },
    execute: () -> T
): T {
    var response = execute()
    if (method != "GET" || encodedPath != "/fdfe/delivery") return response

    for (delay in longArrayOf(1_000L, 2_000L, 4_000L)) {
        if (responseCode(response) != 429) return response
        sleep(delay)
        response = execute()
    }
    return response
}

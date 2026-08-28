package com.apkupdater.util

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_MUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
import android.util.Log
import androidx.core.net.toUri
import com.apkupdater.BuildConfig
import com.apkupdater.data.ui.AppInstallProgress
import com.apkupdater.data.ui.AppInstallStatus
import com.apkupdater.prefs.Prefs
import com.apkupdater.ui.activity.MainActivity
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipFile
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.io.DEFAULT_BUFFER_SIZE
import kotlin.io.copyTo
import kotlin.io.outputStream


@OptIn(ExperimentalAtomicApi::class)
class SessionInstaller(
    private val context: Context,
    private val installLog: InstallLog,
    private val prefs: Prefs
) {
    companion object {
        const val INSTALL_ACTION = "installAction"
    }

    init {
        // Cleanup old sessions
        val installer = context.packageManager.packageInstaller
        runCatching {
            installer.mySessions.forEach { session ->
                runCatching { installer.abandonSession(session.sessionId) }
                    .onFailure { Log.w("SessionInstaller", "Unable to abandon stale session ${session.sessionId}", it) }
            }
        }.onFailure { Log.w("SessionInstaller", "Unable to inspect stale install sessions", it) }
    }

    private val installMutex = AtomicBoolean(false)
    private val installNewMutex = Mutex()

    suspend fun install(id: Int, packageName: String, stream: InputStream) {
        if (prefs.newInstaller.get()) installNew(id, packageName, listOf(stream)) else installOld(id, packageName, listOf(stream))
    }

    suspend fun install(id: Int, packageName: String, streams: List<InputStream>) {
        if (prefs.newInstaller.get()) installNew(id, packageName, streams) else installOld(id, packageName, streams)
    }

    suspend fun installPackage(id: Int, packageName: String, stream: InputStream) {
        val file = File(context.cacheDir, randomUUID())
        stream.use { input -> input.copyTo(file.outputStream()) }

        try {
            val zip = runCatching { ZipFile(file) }.getOrNull()
            if (zip == null) {
                install(id, packageName, file.inputStream())
                return
            }

            zip.use { archive ->
                val apks = archive.entries()
                    .asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                    .toList()

                if (apks.isEmpty()) {
                    install(id, packageName, file.inputStream())
                } else {
                    install(id, packageName, apks.map { archive.getInputStream(it) })
                }
            }
        } finally {
            file.delete()
        }
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    private suspend fun installNew(
        id: Int,
        packageName: String,
        streams: List<InputStream>
    ): Boolean = installNewMutex.withLock {
        Log.i("InstallViewModel", "installNew: start id=$id pkg=$packageName splits=${streams.size}")
        val packageInstaller: PackageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 24) params.setAppPackageName(packageName)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        if (Build.VERSION.SDK_INT >= 33) params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)

        return@withLock suspendCancellableCoroutine { continuation ->

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, @SuppressLint("UnsafeIntentLaunch") intent: Intent?) {
                    Log.i("InstallViewModel", "installNew: broadcast status=${intent?.extras?.getInt(PackageInstaller.EXTRA_STATUS)} id=$id pkg=$packageName")
                    when (val extra = intent?.extras?.getInt(PackageInstaller.EXTRA_STATUS)) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            Log.i("InstallViewModel", "installNew: STATUS_PENDING_USER_ACTION id=$id")
                            installLog.currentInstallId = intent.getAppId() ?: 0
                            // Launch intent to confirm install (onReceive runs on main thread — no scope.launch needed)
                            val confirmationIntent = intent.getIntentExtra()
                            if (confirmationIntent == null) {
                                Log.e("InstallViewModel", "installNew: missing confirmation intent id=$id")
                                installLog.emitStatus(AppInstallStatus(false, id, true))
                                runCatching { context.unregisterReceiver(this) }
                                if (continuation.isActive) continuation.resume(false) {_, _, _ -> }
                            } else {
                                confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(confirmationIntent) }
                                    .onFailure { e ->
                                        Log.e("InstallViewModel", "installNew: startActivity failed id=$id", e)
                                        installLog.emitStatus(AppInstallStatus(false, id, true))
                                        runCatching { context.unregisterReceiver(this) }
                                        if (continuation.isActive) continuation.resume(false) {_, _, _ -> }
                                    }
                            }
                        }
                        PackageInstaller.STATUS_SUCCESS -> {
                            Log.i("InstallViewModel", "installNew: SUCCESS id=$id pkg=$packageName")
                            installLog.emitStatus(AppInstallStatus(true, id, true))
                            runCatching { context.unregisterReceiver(this) }
                            if (continuation.isActive) continuation.resume(true) {_, _, _ -> }
                        }
                        else -> {
                            Log.i("InstallViewModel", "installNew: FAILURE status=$extra id=$id pkg=$packageName")
                            installLog.emitStatus(AppInstallStatus(false, id, true))
                            runCatching { context.unregisterReceiver(this) }
                            if (continuation.isActive) continuation.resume(false) {_, _, _ -> }
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, IntentFilter("$INSTALL_ACTION.$id"), Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, IntentFilter("$INSTALL_ACTION.$id"))
            }

            val callback = object : PackageInstaller.SessionCallback() {
                override fun onBadgingChanged(sessionId: Int) {}
                override fun onActiveChanged(sessionId: Int, active: Boolean) {}
                override fun onCreated(sessionId: Int) {}
                override fun onProgressChanged(sessionId: Int, p: Float) {
                    Log.i("InstallViewModel", "installNew: sessionProgress=$p sessionId=$sessionId id=$id pkg=$packageName")
                }
                override fun onFinished(sessionId: Int, success: Boolean) {
                    Log.i("InstallViewModel", "installNew: sessionFinished sessionId=$sessionId success=$success id=$id pkg=$packageName")
                    packageInstaller.unregisterSessionCallback(this)
                    runCatching { packageInstaller.openSession(sessionId).close() }.getOrNull()
                }
            }

            packageInstaller.registerSessionCallback(callback, Handler(Looper.getMainLooper()))

            continuation.invokeOnCancellation {
                runCatching {
                    context.unregisterReceiver(receiver)
                    packageInstaller.unregisterSessionCallback(callback)
                }.getOrNull()
            }

            val sessionId = packageInstaller.createSession(params)
            Log.i("InstallViewModel", "installNew: sessionId=$sessionId id=$id pkg=$packageName writing ${streams.size} stream(s)")
            var totalBytes = 0L
            packageInstaller.openSession(sessionId).use { session ->
                streams.forEach { stream ->
                    session.openWrite("$packageName.${randomUUID()}", 0, -1).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var bytes = stream.read(buffer)
                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            totalBytes += bytes
                            installLog.emitProgress(AppInstallProgress(id, totalBytes))
                            bytes = stream.read(buffer)
                        }
                        session.fsync(output)
                    }
                    stream.close()
                }
                Log.i("InstallViewModel", "installNew: committing sessionId=$sessionId id=$id pkg=$packageName totalBytes=$totalBytes")
                val intent = Intent("$INSTALL_ACTION.$id").apply { setPackage(context.packageName) }
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, FLAG_UPDATE_CURRENT or FLAG_MUTABLE)
                session.commit(pending.intentSender)
            }
        }
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    private suspend fun installOld(id: Int, packageName: String, streams: List<InputStream>) {
        val packageInstaller: PackageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(packageName)

        if (Build.VERSION.SDK_INT > 24) {
            params.setOriginatingUid(Process.myUid())
        }

        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }

        if (Build.VERSION.SDK_INT >= 33) {
            params.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
        }

        val sessionId = packageInstaller.createSession(params)
        var bytes = 0L
        packageInstaller.openSession(sessionId).use { session ->
            streams.forEach {
                session.openWrite("$packageName.${randomUUID()}", 0, -1).use { output ->
                    bytes += it.copyToAndNotify(output, id, installLog, bytes)
                    it.close()
                    session.fsync(output)
                }
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                action = "$INSTALL_ACTION.$id"
            }

            installMutex.lock()
            installLog.expectInstall(id)
            val pending = PendingIntent.getActivity(context, 0, intent, FLAG_MUTABLE)
            session.commit(pending.intentSender)
            session.close()
        }
    }

    fun rootInstall(file: File): Boolean {
        val res = Shell.cmd("pm install -r ${file.absolutePath}").exec().isSuccess
        file.delete()
        return res
    }

    fun finish() = installMutex.unlock()

    fun checkPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if(!context.packageManager.canRequestPackageInstalls()) {
                val uri = "package:${BuildConfig.APPLICATION_ID}".toUri()
                val intent = Intent(ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent, null)
                return false
            }
        }
        return true
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun installXapk(id: Int, packageName: String, stream: InputStream) = installPackage(id, packageName, stream)

}

fun InputStream.copyToAndNotify(out: OutputStream, id: Int, installLog: InstallLog, total: Long, bufferSize: Int = DEFAULT_BUFFER_SIZE): Long {
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    while (bytes >= 0) {
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        installLog.emitProgress(AppInstallProgress(id, progress = total + bytesCopied))
        bytes = read(buffer)
    }
    return bytesCopied
}

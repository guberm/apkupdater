package com.apkupdater.util

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** dataSync foreground service: keeps the process out of the cached (freezable) state while downloads/installs run. */
class DownloadService : Service(), KoinComponent {

	private val updatesNotification: UpdatesNotification by inject()

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		val notification = updatesNotification.buildForegroundNotification()
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
		} else {
			startForeground(NOTIFICATION_ID, notification)
		}
		return START_NOT_STICKY
	}

	private companion object {
		const val NOTIFICATION_ID = 43
	}
}

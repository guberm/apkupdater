package com.apkupdater.util

import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks active download/install jobs. startService fires on the 0 -> 1 transition, stopService on
 * 1 -> 0, so the process keeps its dataSync foreground service while any tracked job runs.
 */
class ForegroundJobs(
	internal var startService: () -> Unit = {},
	internal var stopService: () -> Unit = {}
) {

	private val count = AtomicInteger(0)

	suspend fun <T> track(block: suspend () -> T): T {
		if (count.incrementAndGet() == 1) startService()
		try {
			return block()
		} finally {
			if (count.decrementAndGet() == 0) stopService()
		}
	}
}

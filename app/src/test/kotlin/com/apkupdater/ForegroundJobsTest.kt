package com.apkupdater

import com.apkupdater.util.ForegroundJobs
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundJobsTest {

	@Test
	fun `starts service once for concurrent jobs and stops after the last finishes`() = runBlocking {
		val started = mutableListOf<Int>()
		val stopped = mutableListOf<Int>()
		val jobs = ForegroundJobs({ started.add(1) }, { stopped.add(1) })

		val a = async { jobs.track { delay(50); "a" } }
		val b = async { jobs.track { delay(20); "b" } }
		assertEquals(listOf("a", "b"), listOf(a.await(), b.await()))

		assertEquals(1, started.size)
		assertEquals(1, stopped.size)
	}

	@Test
	fun `stop fires when a job throws`() = runBlocking {
		var stopped = 0
		val jobs = ForegroundJobs({}, { stopped++ })
		runCatching { jobs.track<Unit> { throw IllegalStateException("boom") } }
		assertEquals(1, stopped)
	}
}

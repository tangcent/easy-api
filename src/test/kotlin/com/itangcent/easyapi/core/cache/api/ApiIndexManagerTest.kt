package com.itangcent.easyapi.core.cache.api

import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

class ApiIndexManagerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var apiIndex: ApiIndex
    private lateinit var scanExecutor: QueueScanExecutor
    private lateinit var manager: ApiIndexManager

    override fun setUp() {
        super.setUp()
        apiIndex = ApiIndex()
        scanExecutor = QueueScanExecutor()
        manager = createManager(scanExecutor)
    }

    override fun tearDown() {
        runBlocking { manager.stopAndJoin() }
        super.tearDown()
    }

    fun testGetInstanceReturnsProjectService() {
        val service = ApiIndexManager.getInstance(project)
        assertNotNull("ApiIndexManager should be available as a project service", service)
        assertSame("Project service lookup should be stable", service, ApiIndexManager.getInstance(project))
    }

    fun testContinuousSessionCanStopRestartAndRetainCache() = runBlocking {
        val firstEndpoint = endpoint("first")
        scanExecutor.enqueueResult(listOf(firstEndpoint))

        val firstGeneration = manager.startContinuous(triggerInitialScan = false)
        val firstResult = manager.requestFullScan(firstGeneration, "test-first").await()

        assertTrue("First full scan should succeed", firstResult is ApiScanResult.Success)
        manager.stopAndJoin()
        assertFalse("Continuous session should be stopped", manager.isStarted())
        assertEquals(
            "Stopping should retain the last successful cache",
            listOf(firstEndpoint),
            apiIndex.retainedSnapshot()
        )

        val secondEndpoint = endpoint("second")
        scanExecutor.enqueueResult(listOf(secondEndpoint))
        val secondGeneration = manager.startContinuous(triggerInitialScan = false)
        val secondResult = manager.requestFullScan(secondGeneration, "test-second").await()

        assertTrue("Restarted full scan should succeed", secondResult is ApiScanResult.Success)
        assertTrue("Restart should allocate a newer generation", secondGeneration > firstGeneration)
        assertEquals(
            "Restarted session should replace the cache",
            listOf(secondEndpoint),
            apiIndex.retainedSnapshot()
        )
    }

    fun testStoppedRequestIsRejectedWithoutStartingFromSettings() = runBlocking {
        val result = manager.requestFullScan(source = "stopped-test").await()

        assertTrue("A stopped manager should reject full work", result is ApiScanResult.Rejected)
        assertFalse("A rejected request must not start a session", manager.isStarted())
        assertEquals("No scan should execute", 0, scanExecutor.fullScanCount.get())
    }

    fun testOneShotFailureRetainsOldSnapshotAndLeavesNoSession() = runBlocking {
        val retained = endpoint("retained")
        apiIndex.updateEndpoints(listOf(retained))
        scanExecutor.enqueueFailure(IllegalStateException("controlled failure"))

        val result = manager.runOneShotFullScan("manual-test")

        assertTrue("One-shot failure should be observable", result is ApiScanResult.Failed)
        assertEquals(
            "Failed one-shot work must retain the old snapshot",
            listOf(retained),
            apiIndex.retainedSnapshot()
        )
        assertNull("One-shot work must release its session", manager.sessionSnapshot())
    }

    fun testOneShotSuccessReplacesSnapshotWithoutContinuousSession() = runBlocking {
        val retained = endpoint("retained")
        val refreshed = endpoint("refreshed")
        apiIndex.updateEndpoints(listOf(retained))
        scanExecutor.enqueueResult(listOf(refreshed))

        val result = manager.runOneShotFullScan("manual-test")

        assertTrue("One-shot scan should succeed", result is ApiScanResult.Success)
        assertEquals(
            "Successful one-shot work should replace the snapshot",
            listOf(refreshed),
            apiIndex.retainedSnapshot()
        )
        assertFalse("One-shot work must not create a continuous session", manager.isStarted())
        assertNull("One-shot work must release all channels and jobs", manager.sessionSnapshot())
    }

    fun testStopCancelsDelayedInitialWorkWithoutFixedSleep() = runBlocking {
        manager.startContinuous(triggerInitialScan = true)
        val beforeStop = manager.sessionSnapshot()

        assertNotNull("A session snapshot should be visible while running", beforeStop)
        assertTrue("Initial work should be pending", beforeStop!!.initialScanPending)

        manager.stopAndJoin()

        assertNull("Stopping should remove the active session", manager.sessionSnapshot())
        assertEquals("Cancelled initial work must not scan", 0, scanExecutor.fullScanCount.get())
    }

    fun testOldGenerationCannotOverwriteRestartedSession() = runBlocking {
        val oldEndpoint = endpoint("old-generation")
        val newEndpoint = endpoint("new-generation")
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val calls = AtomicInteger(0)
        val executor = object : ApiIndexScanExecutor {
            override suspend fun scanAll(): List<ApiEndpoint> {
                return if (calls.incrementAndGet() == 1) {
                    firstStarted.complete(Unit)
                    try {
                        releaseFirst.await()
                    } catch (e: CancellationException) {
                        withContext(NonCancellable) { releaseFirst.await() }
                    }
                    listOf(oldEndpoint)
                } else {
                    listOf(newEndpoint)
                }
            }

            override suspend fun scanClasses(classes: List<PsiClass>): List<ApiEndpoint> = emptyList()
        }
        manager.stopAndJoin()
        manager = createManager(executor)

        val oldGeneration = manager.startContinuous(triggerInitialScan = false)
        val oldRequest = manager.requestFullScan(oldGeneration, "old")
        firstStarted.await()

        manager.stop()
        val newGeneration = manager.startContinuous(triggerInitialScan = false)
        val newResult = manager.requestFullScan(newGeneration, "new").await()
        releaseFirst.complete(Unit)
        val oldResult = oldRequest.await()

        assertTrue("New generation should commit successfully", newResult is ApiScanResult.Success)
        assertTrue("Cancelled old work should be rejected", oldResult is ApiScanResult.Rejected)
        assertEquals(
            "Late output from the old generation must not overwrite the cache",
            listOf(newEndpoint),
            apiIndex.retainedSnapshot()
        )
    }

    private fun createManager(executor: ApiIndexScanExecutor): ApiIndexManager = ApiIndexManager(
        project = project,
        apiIndex = apiIndex,
        scanExecutor = executor,
        targetAnnotations = { emptySet() },
        dispatcher = Dispatchers.Unconfined,
        initialScanDelayMs = 60_000L,
        minIncrementalScanIntervalMs = 0L
    )

    private fun endpoint(name: String): ApiEndpoint = ApiEndpoint(
        metadata = httpMetadata(path = "/$name", method = HttpMethod.GET),
        name = name,
        className = "example.$name.Controller"
    )

    private class QueueScanExecutor : ApiIndexScanExecutor {
        private val fullScans = ArrayDeque<suspend () -> List<ApiEndpoint>>()
        val fullScanCount = AtomicInteger(0)

        fun enqueueResult(endpoints: List<ApiEndpoint>) {
            fullScans.addLast { endpoints }
        }

        fun enqueueFailure(throwable: Throwable) {
            fullScans.addLast { throw throwable }
        }

        override suspend fun scanAll(): List<ApiEndpoint> {
            fullScanCount.incrementAndGet()
            return if (fullScans.isEmpty()) emptyList() else fullScans.removeFirst().invoke()
        }

        override suspend fun scanClasses(classes: List<PsiClass>): List<ApiEndpoint> = emptyList()
    }
}

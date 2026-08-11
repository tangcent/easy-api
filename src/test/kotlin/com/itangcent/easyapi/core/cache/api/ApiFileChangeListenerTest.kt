package com.itangcent.easyapi.core.cache.api

import com.intellij.util.messages.MessageBusConnection
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.Dispatchers
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

class ApiFileChangeListenerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var sink: FakeRequestSink
    private lateinit var connections: ArrayDeque<MessageBusConnection>
    private lateinit var listener: ApiFileChangeListener
    private val connectionCreations = AtomicInteger(0)

    override fun setUp() {
        super.setUp()
        sink = FakeRequestSink(ApiScanAdmission(GENERATION))
        connections = ArrayDeque<MessageBusConnection>().apply {
            add(mock())
            add(mock())
            add(mock())
        }
        listener = createListener(DEBOUNCE_DELAY_MS)
    }

    override fun tearDown() {
        listener.dispose()
        super.tearDown()
    }

    fun testGetInstanceReturnsProjectService() {
        val service = ApiFileChangeListener.getInstance(project)
        assertNotNull("ApiFileChangeListener should be available", service)
        assertSame("Project service lookup should be stable", service, ApiFileChangeListener.getInstance(project))
    }

    fun testStartIsIdempotentAndStopAllowsRestart() {
        val firstConnection = connections.first()

        assertTrue("First start should create a subscription", listener.start(GENERATION))
        assertFalse("Repeated start should not duplicate a subscription", listener.start(GENERATION))
        assertEquals("Only one connection should be created", 1, connectionCreations.get())

        assertTrue("Stop should disconnect the active subscription", listener.stop())
        verify(firstConnection, times(1)).disconnect()
        assertFalse("Listener should report stopped", listener.isStarted())

        assertTrue("Listener should restart with a new connection", listener.start(GENERATION + 1))
        assertEquals("Restart should create exactly one new connection", 2, connectionCreations.get())
    }

    fun testStopCancelsPendingAndDebounceImmediately() {
        listener.start(GENERATION)

        val result = listener.onFilesChanged(listOf("/src/A.java", "/src/B.kt"))

        assertEquals("Admitted VFS work should be accepted", ApiScanRequestDecision.ACCEPTED, result)
        assertEquals("Pending files should be batched", 2, listener.pendingFileCount())
        assertTrue("A debounce job should be active", listener.hasDebounceWork())

        listener.stop()

        assertEquals("Stop should clear pending files synchronously", 0, listener.pendingFileCount())
        assertFalse("Stop should cancel debounce work synchronously", listener.hasDebounceWork())
        assertEquals("Cancelled debounce must not submit work", 0, sink.vfsSubmissions.get())
    }

    fun testRejectedEventCreatesNoPendingWork() {
        sink.admission = null
        listener.start(GENERATION)

        val result = listener.onFilesChanged(listOf("/src/Disabled.java"))

        assertEquals("Disabled VFS work should be rejected", ApiScanRequestDecision.REJECTED_DISABLED, result)
        assertEquals("Rejected events must not enter the pending set", 0, listener.pendingFileCount())
        assertFalse("Rejected events must not create debounce work", listener.hasDebounceWork())
    }

    fun testStaleAdmissionCreatesNoPendingWork() {
        sink.admission = ApiScanAdmission(GENERATION + 1)
        listener.start(GENERATION)

        val result = listener.onFilesChanged(listOf("/src/Stale.java"))

        assertEquals(
            "A generation mismatch should be rejected before pending work",
            ApiScanRequestDecision.REJECTED_STALE_GENERATION,
            result
        )
        assertEquals("Stale events must not enter the pending set", 0, listener.pendingFileCount())
        assertFalse("Stale events must not create debounce work", listener.hasDebounceWork())
    }

    fun testDebounceCompletionSubmitsOnlyToControllerSink() {
        listener.dispose()
        listener = createListener(debounceDelayMs = 0L)
        listener.start(GENERATION)

        val result = listener.onFilesChanged(listOf("/src/A.java", "/src/A.java"))

        assertEquals("Event should be admitted", ApiScanRequestDecision.ACCEPTED, result)
        assertEquals("Exactly one debounced request should be submitted", 1, sink.vfsSubmissions.get())
        assertEquals("Submitted paths should be deduplicated", listOf("/src/A.java"), sink.lastPaths)
        assertEquals("Pending files should be drained", 0, listener.pendingFileCount())
        assertFalse("Completed debounce should not remain active", listener.hasDebounceWork())
    }

    private fun createListener(debounceDelayMs: Long): ApiFileChangeListener = ApiFileChangeListener(
        project = project,
        requestSinkProvider = { sink },
        connectionFactory = {
            connectionCreations.incrementAndGet()
            connections.removeFirst()
        },
        dispatcher = Dispatchers.Unconfined,
        debounceDelayMs = debounceDelayMs
    )

    private class FakeRequestSink(
        var admission: ApiScanAdmission?
    ) : ApiScanRequestSink {
        val vfsSubmissions = AtomicInteger(0)
        var lastPaths: List<String> = emptyList()

        override fun admitVfs(): ApiScanAdmission? = admission

        override fun submitVfs(
            admission: ApiScanAdmission,
            filePaths: List<String>
        ): ApiScanRequestDecision {
            vfsSubmissions.incrementAndGet()
            lastPaths = filePaths
            return ApiScanRequestDecision.ACCEPTED
        }

        override fun requestVcs(branchName: String): ApiScanRequestDecision =
            ApiScanRequestDecision.ACCEPTED
    }

    companion object {
        private const val GENERATION = 7L
        private const val DEBOUNCE_DELAY_MS = 60_000L
    }
}

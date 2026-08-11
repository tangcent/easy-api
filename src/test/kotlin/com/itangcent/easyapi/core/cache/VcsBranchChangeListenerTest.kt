package com.itangcent.easyapi.core.cache

import com.intellij.util.messages.MessageBusConnection
import com.itangcent.easyapi.core.cache.api.ApiScanAdmission
import com.itangcent.easyapi.core.cache.api.ApiScanRequestDecision
import com.itangcent.easyapi.core.cache.api.ApiScanRequestSink
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

class VcsBranchChangeListenerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var sink: FakeRequestSink
    private lateinit var connections: ArrayDeque<MessageBusConnection>
    private lateinit var listener: VcsBranchChangeListener
    private val connectionCreations = AtomicInteger(0)

    override fun setUp() {
        super.setUp()
        sink = FakeRequestSink()
        connections = ArrayDeque<MessageBusConnection>().apply {
            add(mock())
            add(mock())
        }
        listener = VcsBranchChangeListener(
            project = project,
            requestSinkProvider = { sink },
            connectionFactory = {
                connectionCreations.incrementAndGet()
                connections.removeFirst()
            }
        )
    }

    override fun tearDown() {
        listener.dispose()
        super.tearDown()
    }

    fun testGetInstanceReturnsProjectService() {
        val service = VcsBranchChangeListener.getInstance(project)
        assertNotNull("VcsBranchChangeListener should be available", service)
        assertSame("Project service lookup should be stable", service, VcsBranchChangeListener.getInstance(project))
    }

    fun testStartIsIdempotentAndStopAllowsRestart() {
        val firstConnection = connections.first()

        assertTrue("First start should create a VCS subscription", listener.start(GENERATION))
        assertFalse("Repeated start should not duplicate a VCS subscription", listener.start(GENERATION))
        assertEquals("Only one connection should be created", 1, connectionCreations.get())

        assertTrue("Stop should disconnect the active subscription", listener.stop())
        verify(firstConnection, times(1)).disconnect()
        assertFalse("Listener should report stopped", listener.isStarted())

        assertTrue("Listener should restart after stop", listener.start(GENERATION + 1))
        assertEquals("Restart should allocate one new connection", 2, connectionCreations.get())
    }

    fun testBranchEventForwardsOnlyToControllerSink() {
        listener.start(GENERATION)

        listener.branchWillChange("feature/example")
        listener.branchHasChanged("feature/example")

        assertEquals("One branch event should be forwarded", 1, sink.vcsRequests.get())
        assertEquals("Branch name should be preserved", "feature/example", sink.lastBranch)
    }

    fun testDisabledBranchEventIsRejectedWithoutOtherWork() {
        sink.vcsDecision = ApiScanRequestDecision.REJECTED_DISABLED
        listener.start(GENERATION)

        listener.branchHasChanged("feature/disabled")

        assertEquals("Rejected branch event should still reach admission once", 1, sink.vcsRequests.get())
        assertEquals(
            "The listener should preserve the controller rejection",
            ApiScanRequestDecision.REJECTED_DISABLED,
            sink.vcsDecision
        )
    }

    private class FakeRequestSink : ApiScanRequestSink {
        val vcsRequests = AtomicInteger(0)
        var vcsDecision = ApiScanRequestDecision.ACCEPTED
        var lastBranch: String? = null

        override fun admitVfs(): ApiScanAdmission? = null

        override fun submitVfs(
            admission: ApiScanAdmission,
            filePaths: List<String>
        ): ApiScanRequestDecision = ApiScanRequestDecision.REJECTED_DISABLED

        override fun requestVcs(branchName: String): ApiScanRequestDecision {
            vcsRequests.incrementAndGet()
            lastBranch = branchName
            return vcsDecision
        }
    }

    companion object {
        private const val GENERATION = 11L
    }
}

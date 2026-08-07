package com.itangcent.easyapi.core.cache.api

import com.itangcent.easyapi.core.feature.CoreFeatureIds
import com.itangcent.easyapi.core.feature.DisabledByUser
import com.itangcent.easyapi.core.feature.FeatureStateChange
import com.itangcent.easyapi.core.feature.FeatureStateChangeSource
import com.itangcent.easyapi.core.feature.FeatureStateDelta
import com.itangcent.easyapi.core.feature.FeatureStateEvents
import com.itangcent.easyapi.core.feature.ResolvedFeatureState
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class ApiScanLifecycleControllerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var stateProvider: MutableStateProvider
    private lateinit var resources: FakeLifecycleResources
    private lateinit var controller: ApiScanLifecycleController

    override fun setUp() {
        super.setUp()
        stateProvider = MutableStateProvider(DISABLED_TARGET)
        resources = FakeLifecycleResources()
        controller = ApiScanLifecycleController(
            project = project,
            stateProvider = stateProvider,
            resources = resources,
            dispatcher = Dispatchers.Unconfined
        )
    }

    override fun tearDown() {
        controller.dispose()
        super.tearDown()
    }

    fun testGetInstanceReturnsProjectService() {
        val service = ApiScanLifecycleController.getInstance(project)
        assertNotNull("ApiScanLifecycleController should be available", service)
        assertSame(
            "Project service lookup should be stable",
            service,
            ApiScanLifecycleController.getInstance(project)
        )
    }

    fun testDisabledStartupCreatesNoContinuousResources() = runBlocking {
        val snapshot = controller.reconcileInitial().await()

        assertEquals("Disabled startup should remain stopped", ApiScanLifecycleState.STOPPED, snapshot.state)
        assertEquals("VFS should not be subscribed", 0, resources.vfsActive)
        assertEquals("VCS should not be subscribed", 0, resources.vcsActive)
        assertEquals("Manager should not be running", 0, resources.managerActive)
        assertEquals("No initial full scan should be scheduled", 0, resources.initialFullRequests)
    }

    fun testEnableDisableRestartUsesExactlyOneResourceOfEachKind() = runBlocking {
        stateProvider.target = ENABLED_TARGET
        val enabled = controller.reconcileSaved("enable").await()

        assertEquals("Enable should reach running", ApiScanLifecycleState.RUNNING, enabled.state)
        assertEquals("Exactly one VFS subscription should be active", 1, resources.vfsActive)
        assertEquals("Exactly one VCS subscription should be active", 1, resources.vcsActive)
        assertEquals("Exactly one manager session should be active", 1, resources.managerActive)
        assertEquals("Enable should schedule one initial full scan", 1, resources.initialFullRequests)

        resources.pendingWork = 2
        resources.debounceWork = 1
        stateProvider.target = DISABLED_TARGET
        val disabled = controller.reconcileSaved("disable").await()

        assertEquals("Disable should reach stopped", ApiScanLifecycleState.STOPPED, disabled.state)
        assertEquals("VFS should be disconnected", 0, resources.vfsActive)
        assertEquals("VCS should be disconnected", 0, resources.vcsActive)
        assertEquals("Manager should be stopped", 0, resources.managerActive)
        assertEquals("Disable should clear pending work", 0, resources.pendingWork)
        assertEquals("Disable should clear debounce work", 0, resources.debounceWork)

        stateProvider.target = ENABLED_TARGET
        controller.reconcileSaved("restart").await()

        assertEquals("Restart should create one VFS subscription", 1, resources.vfsActive)
        assertEquals("Restart should create one VCS subscription", 1, resources.vcsActive)
        assertEquals("Restart should create one manager session", 1, resources.managerActive)
        assertEquals("Restart should schedule one additional initial scan", 2, resources.initialFullRequests)
        assertEquals("Resources must never be duplicated", 1, resources.maxManagerActive)
    }

    fun testRepeatedEnabledReconcileDoesNotDuplicateSubscriptions() = runBlocking {
        stateProvider.target = ENABLED_TARGET

        controller.reconcileSaved("first").await()
        controller.reconcileSaved("second").await()
        controller.reconcileSaved("third").await()

        assertEquals("Manager should start once", 1, resources.managerStarts)
        assertEquals("VFS should start once", 1, resources.vfsStarts)
        assertEquals("VCS should start once", 1, resources.vcsStarts)
        assertEquals("Only one initial scan should be scheduled", 1, resources.initialFullRequests)
    }

    fun testDisabledManualRefreshUsesOneShotWithoutSubscriptions() = runBlocking {
        controller.reconcileInitial().await()
        resources.oneShotResult = ApiScanResult.Success(1L, 3)

        val result = controller.manualRefresh().await()

        assertTrue("Manual refresh should return the one-shot result", result is ApiScanResult.Success)
        assertEquals("Exactly one one-shot scan should run", 1, resources.oneShotScans)
        assertEquals("Manual refresh must not start VFS", 0, resources.vfsStarts)
        assertEquals("Manual refresh must not start VCS", 0, resources.vcsStarts)
        assertEquals("Manual refresh must not start a continuous manager", 0, resources.managerStarts)
        assertEquals("Controller should return to stopped", ApiScanLifecycleState.STOPPED, controller.snapshot().state)
    }

    fun testFailedDisabledManualRefreshStillCreatesNoSubscriptions() = runBlocking {
        val failure = IllegalStateException("controlled one-shot failure")
        resources.oneShotResult = ApiScanResult.Failed(1L, failure)

        val result = controller.manualRefresh().await()

        assertTrue("Failure should remain observable", result is ApiScanResult.Failed)
        assertEquals("VFS must remain unsubscribed", 0, resources.vfsActive)
        assertEquals("VCS must remain unsubscribed", 0, resources.vcsActive)
        assertEquals("Continuous manager must remain stopped", 0, resources.managerActive)
        assertEquals("Controller should return to stopped", ApiScanLifecycleState.STOPPED, controller.snapshot().state)
    }

    fun testReconcileCancelsInFlightOneShotAndContinuesToLatestTarget() = runBlocking {
        resources.oneShotWork = CompletableDeferred()
        val manualCompletion = controller.manualRefresh()
        assertEquals(
            "Unfinished one-shot work should remain observable",
            ApiScanLifecycleState.ONE_SHOT_SCANNING,
            controller.snapshot().state
        )

        stateProvider.target = ENABLED_TARGET
        val reconciled = controller.reconcileSaved("enable-during-one-shot").await()
        val manualResult = manualCompletion.await()

        assertTrue("Cancelled one-shot should be rejected", manualResult is ApiScanResult.Rejected)
        assertEquals("Latest enabled target should reach running", ApiScanLifecycleState.RUNNING, reconciled.state)
        assertEquals("One manager session should remain", 1, resources.managerActive)
        assertEquals("One VFS subscription should remain", 1, resources.vfsActive)
        assertEquals("One VCS subscription should remain", 1, resources.vcsActive)
    }

    fun testConcurrentManualCompletionCannotStopOwningOneShot() = runBlocking {
        val firstWork = CompletableDeferred<ApiScanResult>()
        resources.oneShotWork = firstWork

        val firstCompletion = controller.manualRefresh()
        val secondResult = controller.manualRefresh().await()

        assertTrue("Concurrent manual refresh should be rejected", secondResult is ApiScanResult.Rejected)
        assertEquals(
            "Rejected completion must not stop the owning one-shot",
            ApiScanLifecycleState.ONE_SHOT_SCANNING,
            controller.snapshot().state
        )

        firstWork.complete(ApiScanResult.Success(1L, 1))
        val firstResult = firstCompletion.await()

        assertTrue("Owning one-shot should complete successfully", firstResult is ApiScanResult.Success)
        assertEquals("Owning completion should stop one-shot state", ApiScanLifecycleState.STOPPED, controller.snapshot().state)
    }

    fun testDisposeCancelsOutstandingCompletion() {
        resources.oneShotWork = CompletableDeferred()
        val completion = controller.manualRefresh()

        controller.dispose()

        assertTrue("Dispose should cancel unresolved public completions", completion.isCancelled)
    }

    fun testDisabledAutomaticEventsAreRejectedBeforePendingWork() = runBlocking {
        controller.reconcileInitial().await()

        val vfsAdmission = controller.admitVfs()
        val vcsDecision = controller.requestVcs("feature/disabled")
        val gutterDecision = controller.requestGutterIncremental(listOf("/src/Disabled.java"))

        assertNull("Disabled VFS should not receive an admission token", vfsAdmission)
        assertEquals("Disabled VCS should be rejected", ApiScanRequestDecision.REJECTED_DISABLED, vcsDecision)
        assertEquals("Disabled gutter work should be rejected", ApiScanRequestDecision.REJECTED_DISABLED, gutterDecision)
        assertEquals("Rejected events should create no pending work", 0, resources.pendingWork)
        assertEquals("Rejected events should create no debounce work", 0, resources.debounceWork)
    }

    fun testAutoDisabledRejectsVfsWhileKeepingVcsAvailable() = runBlocking {
        stateProvider.target = ENABLED_TARGET.copy(autoScanningEnabled = false)
        controller.reconcileSaved("auto-disabled").await()

        assertNull("Auto-disabled VFS should not be admitted", controller.admitVfs())
        assertEquals(
            "VCS should remain available while scanning is running",
            ApiScanRequestDecision.ACCEPTED,
            controller.requestVcs("feature/example")
        )
        controller.awaitIdle().await()
        assertEquals("Accepted VCS should request one full scan", 1, resources.fullRequests)
    }

    fun testRapidTypedChangesConvergeToLastSavedState() = runBlocking {
        stateProvider.target = ENABLED_TARGET
        publishScanningChange(before = false, after = true)
        stateProvider.target = DISABLED_TARGET
        publishScanningChange(before = true, after = false)
        stateProvider.target = ENABLED_TARGET
        publishScanningChange(before = false, after = true)

        val finalSnapshot = controller.awaitIdle().await()

        assertEquals("Last saved target should win", ApiScanLifecycleState.RUNNING, finalSnapshot.state)
        assertTrue("Last saved target should be enabled", finalSnapshot.target.scanningEnabled)
        assertEquals("Exactly one VFS subscription should remain", 1, resources.vfsActive)
        assertEquals("Exactly one VCS subscription should remain", 1, resources.vcsActive)
        assertEquals("Exactly one manager session should remain", 1, resources.managerActive)
        assertEquals("Pending work should be empty", 0, resources.pendingWork)
        assertEquals("Debounce work should be empty", 0, resources.debounceWork)
    }

    fun testRecoverableStartFailureCleansUpAndConverges() = runBlocking {
        resources.failNextVfsStart = true
        stateProvider.target = ENABLED_TARGET

        val snapshot = controller.reconcileSaved("recoverable-failure").await()

        assertEquals("Controller should recover to running", ApiScanLifecycleState.RUNNING, snapshot.state)
        assertEquals("Only one manager should remain active", 1, resources.managerActive)
        assertEquals("Only one VFS listener should remain active", 1, resources.vfsActive)
        assertEquals("Only one VCS listener should remain active", 1, resources.vcsActive)
        assertEquals("Failed partial manager should be stopped", 1, resources.managerStops)
        assertEquals("Start should retry once", 2, resources.managerStarts)
    }

    private fun publishScanningChange(before: Boolean, after: Boolean) {
        project.messageBus.syncPublisher(FeatureStateEvents.TOPIC).featureStateChanged(
            FeatureStateChange(
                source = FeatureStateChangeSource.SETTINGS_APPLY,
                entries = listOf(
                    FeatureStateDelta(
                        id = CoreFeatureIds.API_SCANNING,
                        before = resolvedScanningState(before),
                        after = resolvedScanningState(after)
                    )
                )
            )
        )
    }

    private fun resolvedScanningState(enabled: Boolean): ResolvedFeatureState = ResolvedFeatureState(
        id = CoreFeatureIds.API_SCANNING,
        desiredEnabled = enabled,
        effectiveEnabled = enabled,
        reason = if (enabled) null else DisabledByUser
    )

    private class MutableStateProvider(
        var target: ApiScanFeatureTarget
    ) : ApiScanFeatureStateProvider {
        override fun currentTarget(): ApiScanFeatureTarget = target
    }

    private class FakeLifecycleResources : ApiScanLifecycleResources {
        var generation = 0L
        var managerActive = 0
        var vfsActive = 0
        var vcsActive = 0
        var managerStarts = 0
        var managerStops = 0
        var vfsStarts = 0
        var vcsStarts = 0
        var maxManagerActive = 0
        var initialFullRequests = 0
        var fullRequests = 0
        var incrementalRequests = 0
        var oneShotScans = 0
        var pendingWork = 0
        var debounceWork = 0
        var failNextVfsStart = false
        var oneShotResult: ApiScanResult = ApiScanResult.Success(1L, 0)
        var oneShotWork: CompletableDeferred<ApiScanResult>? = null

        override suspend fun startManager(triggerInitialScan: Boolean): Long {
            check(managerActive == 0) { "Manager session duplicated" }
            generation++
            managerActive = 1
            managerStarts++
            maxManagerActive = maxOf(maxManagerActive, managerActive)
            if (triggerInitialScan) initialFullRequests++
            return generation
        }

        override fun startVfs(generation: Long) {
            if (failNextVfsStart) {
                failNextVfsStart = false
                throw IllegalStateException("controlled VFS start failure")
            }
            check(vfsActive == 0) { "VFS subscription duplicated" }
            vfsActive = 1
            vfsStarts++
        }

        override fun startVcs(generation: Long) {
            check(vcsActive == 0) { "VCS subscription duplicated" }
            vcsActive = 1
            vcsStarts++
        }

        override fun stopVfs() {
            vfsActive = 0
            pendingWork = 0
            debounceWork = 0
        }

        override fun stopVcs() {
            vcsActive = 0
        }

        override suspend fun stopManager(): Long {
            if (managerActive > 0) managerStops++
            managerActive = 0
            pendingWork = 0
            generation++
            oneShotWork?.takeUnless { it.isCompleted }?.complete(
                ApiScanResult.Rejected(generation, ApiScanRejectionReason.SESSION_STOPPED)
            )
            return generation
        }

        override fun requestFull(generation: Long, source: String): Deferred<ApiScanResult> {
            fullRequests++
            return completed(ApiScanResult.Success(generation, 0))
        }

        override fun requestIncremental(
            generation: Long,
            filePaths: List<String>,
            source: String
        ): Deferred<ApiScanResult> {
            incrementalRequests++
            pendingWork = 0
            return completed(ApiScanResult.Success(generation, 0))
        }

        override fun runOneShotFull(source: String): Deferred<ApiScanResult> {
            oneShotScans++
            return oneShotWork ?: completed(oneShotResult)
        }

        private fun completed(result: ApiScanResult): Deferred<ApiScanResult> =
            CompletableDeferred<ApiScanResult>().apply { complete(result) }

        override fun stopImmediately() {
            managerActive = 0
            vfsActive = 0
            vcsActive = 0
            pendingWork = 0
            debounceWork = 0
        }
    }

    companion object {
        private val DISABLED_TARGET = ApiScanFeatureTarget(
            scanningEnabled = false,
            autoScanningEnabled = false,
            editorIntegrationEnabled = false
        )
        private val ENABLED_TARGET = ApiScanFeatureTarget(
            scanningEnabled = true,
            autoScanningEnabled = true,
            editorIntegrationEnabled = true
        )
    }
}

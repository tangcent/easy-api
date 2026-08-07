package com.itangcent.easyapi.core.cache.api

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ApiScanLifecycleControllerPropertyTest {

    // Property 5: Scan lifecycle resources converge to the latest applied effective state.
    @Test
    fun propertyContinuousResourcesConvergeToLastApply(): Unit = runBlocking {
        checkAll(iterations = 150, Arb.int()) { seed ->
            val random = Random(seed)
            val appliedStates = List(random.nextInt(1, 80)) { random.nextBoolean() }
            val resources = FakeLifecycleResources()
            val machine = ApiScanLifecycleMachine(resources)

            appliedStates.forEachIndexed { index, enabled ->
                if (resources.managerActive == 1) {
                    resources.pendingWork = random.nextInt(0, 4)
                    resources.debounceWork = random.nextInt(0, 2)
                }
                machine.reconcile(
                    target = ApiScanFeatureTarget(
                        scanningEnabled = enabled,
                        autoScanningEnabled = enabled,
                        editorIntegrationEnabled = enabled
                    ),
                    revision = index + 1L,
                    source = "property-$index"
                )
            }

            val expectedActive = if (appliedStates.last()) 1 else 0
            assertEquals(
                "VFS state should match the last apply for seed=$seed",
                expectedActive,
                resources.vfsActive
            )
            assertEquals(
                "VCS state should match the last apply for seed=$seed",
                expectedActive,
                resources.vcsActive
            )
            assertEquals(
                "Manager state should match the last apply for seed=$seed",
                expectedActive,
                resources.managerActive
            )
            assertTrue(
                "VFS should never have duplicate subscriptions for seed=$seed",
                resources.maxVfsActive <= 1
            )
            assertTrue(
                "VCS should never have duplicate subscriptions for seed=$seed",
                resources.maxVcsActive <= 1
            )
            assertTrue(
                "Manager should never have duplicate sessions for seed=$seed",
                resources.maxManagerActive <= 1
            )
            if (!appliedStates.last()) {
                assertEquals("Disabled state should clear pending work for seed=$seed", 0, resources.pendingWork)
                assertEquals("Disabled state should clear debounce work for seed=$seed", 0, resources.debounceWork)
            }
        }
    }

    private class FakeLifecycleResources : ApiScanLifecycleResources {
        var generation = 0L
        var vfsActive = 0
        var vcsActive = 0
        var managerActive = 0
        var maxVfsActive = 0
        var maxVcsActive = 0
        var maxManagerActive = 0
        var pendingWork = 0
        var debounceWork = 0

        override suspend fun startManager(triggerInitialScan: Boolean): Long {
            check(managerActive == 0) { "Manager session duplicated" }
            generation++
            managerActive = 1
            maxManagerActive = maxOf(maxManagerActive, managerActive)
            return generation
        }

        override fun startVfs(generation: Long) {
            check(vfsActive == 0) { "VFS subscription duplicated" }
            vfsActive = 1
            maxVfsActive = maxOf(maxVfsActive, vfsActive)
        }

        override fun startVcs(generation: Long) {
            check(vcsActive == 0) { "VCS subscription duplicated" }
            vcsActive = 1
            maxVcsActive = maxOf(maxVcsActive, vcsActive)
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
            managerActive = 0
            pendingWork = 0
            debounceWork = 0
            generation++
            return generation
        }

        override fun requestFull(generation: Long, source: String): Deferred<ApiScanResult> =
            completed(ApiScanResult.Success(generation, 0))

        override fun requestIncremental(
            generation: Long,
            filePaths: List<String>,
            source: String
        ): Deferred<ApiScanResult> {
            pendingWork = 0
            debounceWork = 0
            return completed(ApiScanResult.Success(generation, 0))
        }

        override fun runOneShotFull(source: String): Deferred<ApiScanResult> =
            completed(ApiScanResult.Success(++generation, 0))

        private fun completed(result: ApiScanResult): Deferred<ApiScanResult> =
            CompletableDeferred<ApiScanResult>().apply { complete(result) }

        override fun stopImmediately() {
            vfsActive = 0
            vcsActive = 0
            managerActive = 0
            pendingWork = 0
            debounceWork = 0
        }
    }
}

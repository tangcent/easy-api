package com.itangcent.easyapi.core.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureStateResolverTest {
    private val resolver = FeatureStateResolver()

    @Test
    fun testDisabledParentPreservesChildDesiredState() {
        val parent = testFeatureDescriptor("parent")
        val child = testFeatureDescriptor("child", dependencies = listOf(parent.id))
        val snapshot = featureSnapshotOf(parent, child)

        val disabled = resolver.resolve(snapshot, mapOf(parent.id to false, child.id to true))
        val childState = disabled.getValue(child.id)

        assertTrue("Child desired state should remain enabled", childState.desiredEnabled)
        assertFalse("Child effective state should be disabled", childState.effectiveEnabled)
        assertEquals(
            "Child should identify its disabled parent",
            DisabledByDependency(parent.id),
            childState.reason
        )

        val enabled = resolver.resolve(snapshot, mapOf(parent.id to true, child.id to true))
        assertTrue("Child should become effective when its parent is enabled", enabled.getValue(child.id).effectiveEnabled)
    }

    @Test
    fun testMissingDependencyHasDeterministicReason() {
        val missing = FeatureId("not-registered")
        val descriptor = testFeatureDescriptor("dependent", dependencies = listOf(missing))

        val state = resolver.resolve(listOf(descriptor), mapOf(descriptor.id to true)).getValue(descriptor.id)

        assertFalse("A feature with a missing dependency should not be effective", state.effectiveEnabled)
        assertEquals("Missing dependency should be exposed", MissingDependency(missing), state.reason)
    }

    @Test
    fun testCycleHasStableClosedPath() {
        val firstId = FeatureId("first")
        val secondId = FeatureId("second")
        val first = testFeatureDescriptor(firstId.value, dependencies = listOf(secondId))
        val second = testFeatureDescriptor(secondId.value, dependencies = listOf(firstId))

        val states = resolver.resolve(
            listOf(first, second),
            mapOf(firstId to true, secondId to true)
        )
        val expectedReason = DependencyCycle(listOf(firstId, secondId, firstId))

        assertEquals("First cycle member should expose the stable cycle path", expectedReason, states.getValue(firstId).reason)
        assertEquals("Second cycle member should expose the same cycle path", expectedReason, states.getValue(secondId).reason)
        assertFalse("First cycle member should be ineffective", states.getValue(firstId).effectiveEnabled)
        assertFalse("Second cycle member should be ineffective", states.getValue(secondId).effectiveEnabled)
    }

    @Test
    fun testUserDisabledStateTakesPrecedence() {
        val descriptor = testFeatureDescriptor("disabled")

        val state = resolver.resolve(listOf(descriptor), mapOf(descriptor.id to false)).getValue(descriptor.id)

        assertEquals("User-disabled state should retain desired false", false, state.desiredEnabled)
        assertEquals("User-disabled state should have a direct reason", DisabledByUser, state.reason)
    }
}

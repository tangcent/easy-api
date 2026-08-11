package com.itangcent.easyapi.core.feature

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FeatureStateResolverPropertyTest {

    @Test
    fun propertyDependenciesPreserveDesiredAndSuppressEffectiveState(): Unit = runBlocking {
        checkAll(iterations = 160, Arb.int()) { seed ->
            val random = Random(seed)
            val size = random.nextInt(2, 10)
            val ids = List(size) { FeatureId("node-$it") }
            val mode = seed and 3
            val desired = ids.associateWith { random.nextBoolean() }.toMutableMap()
            val dependencies = MutableList(size) { index ->
                if (index == 0 || random.nextBoolean()) {
                    emptyList()
                } else {
                    listOf(ids[random.nextInt(0, index)])
                }
            }

            when (mode) {
                1 -> {
                    dependencies[0] = listOf(FeatureId("missing-$seed"))
                    desired[ids[0]] = true
                }
                2 -> {
                    dependencies[0] = listOf(ids[1])
                    dependencies[1] = listOf(ids[0])
                    desired[ids[0]] = true
                    desired[ids[1]] = true
                }
                3 -> {
                    dependencies[0] = emptyList()
                    dependencies[1] = listOf(ids[0])
                    desired[ids[0]] = false
                    desired[ids[1]] = true
                }
            }

            val identities = ids.mapIndexed { index, id ->
                testFeatureDescriptor(id.value, dependencies = dependencies[index])
            }
            val states = FeatureStateResolver().resolve(identities, desired)

            ids.forEach { id ->
                val state = states.getValue(id)
                assertEquals("Desired state must be preserved for $id seed=$seed", desired.getValue(id), state.desiredEnabled)
                if (state.effectiveEnabled) {
                    assertTrue("Effective state requires desired state for $id seed=$seed", state.desiredEnabled)
                    assertTrue(
                        "Effective state requires every dependency for $id seed=$seed",
                        dependencies[ids.indexOf(id)].all { dependencyId ->
                            states[dependencyId]?.effectiveEnabled == true
                        }
                    )
                }
                if (!state.desiredEnabled) {
                    assertEquals("User-disabled reason should be stable for $id seed=$seed", DisabledByUser, state.reason)
                }
            }

            when (mode) {
                0 -> {
                    val expectedEffective = linkedMapOf<FeatureId, Boolean>()
                    ids.forEachIndexed { index, id ->
                        expectedEffective[id] = desired.getValue(id) &&
                            dependencies[index].all { dependencyId -> expectedEffective[dependencyId] == true }
                    }
                    assertEquals(
                        "Acyclic graph should follow the dependency equation for seed=$seed",
                        expectedEffective,
                        states.mapValues { it.value.effectiveEnabled }
                    )
                }
                1 -> {
                    assertFalse("Missing dependency should suppress effective state for seed=$seed", states.getValue(ids[0]).effectiveEnabled)
                    assertEquals(
                        "Missing dependency reason should be exact for seed=$seed",
                        MissingDependency(dependencies[0].single()),
                        states.getValue(ids[0]).reason
                    )
                }
                2 -> {
                    val reason = DependencyCycle(listOf(ids[0], ids[1], ids[0]))
                    assertEquals("First cycle reason should be deterministic for seed=$seed", reason, states.getValue(ids[0]).reason)
                    assertEquals("Second cycle reason should be deterministic for seed=$seed", reason, states.getValue(ids[1]).reason)
                }
                3 -> {
                    assertTrue("Child desired state should remain enabled for seed=$seed", states.getValue(ids[1]).desiredEnabled)
                    assertFalse("Disabled parent should suppress child for seed=$seed", states.getValue(ids[1]).effectiveEnabled)
                    assertEquals(
                        "Child should identify the disabled parent for seed=$seed",
                        DisabledByDependency(ids[0]),
                        states.getValue(ids[1]).reason
                    )
                }
            }
        }
    }
}

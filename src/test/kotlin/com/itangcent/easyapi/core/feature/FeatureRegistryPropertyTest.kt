package com.itangcent.easyapi.core.feature

import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.random.Random

class FeatureRegistryPropertyTest {

    @Test
    fun propertySnapshotRetainsFirstMetadataAndOnlyNonEmptyGroups(): Unit = runBlocking {
        checkAll(iterations = 150, Arb.int()) { seed ->
            val random = Random(seed)
            val groupPool = List(5) { index ->
                FeatureGroup("group-$index", "Group $index", random.nextInt(0, 4))
            }
            val emptyGroup = FeatureGroup("empty-$seed", "Empty", -1)
            val entries = List(random.nextInt(1, 12)) { sourceIndex ->
                val descriptors = List(random.nextInt(0, 5)) { descriptorIndex ->
                    val idIndex = random.nextInt(0, 7)
                    FeatureDescriptor(
                        id = FeatureId("feature-$idIndex"),
                        displayName = "Source $sourceIndex descriptor $descriptorIndex",
                        defaultEnabled = random.nextBoolean(),
                        group = groupPool[random.nextInt(groupPool.size)],
                        dependencyIds = emptyList(),
                        stateBridge = DirectBooleanStateBridge(
                            DirectBooleanSetting.entries[random.nextInt(DirectBooleanSetting.entries.size)]
                        ),
                        source = FeatureSource("source-$sourceIndex")
                    )
                }
                FeatureContributionEntry(
                    sourceId = "source-$sourceIndex",
                    contribution = FeatureContribution(
                        groups = groupPool.shuffled(random).take(random.nextInt(0, groupPool.size + 1)) + emptyGroup,
                        descriptors = descriptors
                    )
                )
            }
            val orderedDescriptors = entries.flatMap { it.contribution.descriptors }
            val expected = orderedDescriptors.distinctBy { it.id }
            var duplicateCount = 0

            val snapshot = FeatureRegistry.buildSnapshot(
                entries = entries,
                onDuplicate = { _, _, _ -> duplicateCount++ }
            )

            assertEquals(
                "First-wins descriptor order should be stable for seed=$seed",
                expected.map { it.id },
                snapshot.descriptors.map { it.id }
            )
            expected.zip(snapshot.descriptors).forEach { (expectedDescriptor, actualDescriptor) ->
                assertEquals("Display name should be preserved for seed=$seed", expectedDescriptor.displayName, actualDescriptor.displayName)
                assertEquals("Default should be preserved for seed=$seed", expectedDescriptor.defaultEnabled, actualDescriptor.defaultEnabled)
                assertEquals("Group should be preserved for seed=$seed", expectedDescriptor.group, actualDescriptor.group)
                assertEquals("Dependencies should be preserved for seed=$seed", expectedDescriptor.dependencyIds, actualDescriptor.dependencyIds)
                assertSame("Bridge should be preserved for seed=$seed", expectedDescriptor.stateBridge, actualDescriptor.stateBridge)
            }
            val expectedGroups = expected.map { it.group }
                .distinctBy { it.id }
                .withIndex()
                .sortedWith(compareBy<IndexedValue<FeatureGroup>> { it.value.order }.thenBy { it.index })
                .map { it.value }
            assertEquals("Only retained non-empty groups should remain for seed=$seed", expectedGroups, snapshot.groups)
            assertEquals(
                "Every ignored duplicate should be diagnosed for seed=$seed",
                orderedDescriptors.size - expected.size,
                duplicateCount
            )
        }
    }
}

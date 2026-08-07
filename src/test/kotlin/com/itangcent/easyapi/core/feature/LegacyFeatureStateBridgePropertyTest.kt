package com.itangcent.easyapi.core.feature

import com.itangcent.easyapi.core.settings.module.GeneralSettings
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LegacyFeatureStateBridgePropertyTest {

    @Test
    fun propertyRoundTripNormalizesKnownAndPreservesUnknownEntries(): Unit = runBlocking {
        checkAll(iterations = 150, Arb.int()) { seed ->
            val random = Random(seed)
            val group = LegacyOverrideArrayGroup.entries[random.nextInt(LegacyOverrideArrayGroup.entries.size)]
            val knownCount = random.nextInt(1, 8)
            val knownIds = List(knownCount) { "known-$it" }
            val defaults = knownIds.associateWith { random.nextBoolean() }
            val candidates = knownIds + List(4) { "unknown-$it" }
            val originalEnabled = List(random.nextInt(0, 20)) { candidates[random.nextInt(candidates.size)] } +
                listOf("unknown-fixed", "unknown-fixed")
            val originalDisabled = List(random.nextInt(0, 20)) { candidates[random.nextInt(candidates.size)] } +
                listOf("unknown-disabled-fixed", "unknown-disabled-fixed")
            val settings = GeneralSettings(
                enabledChannels = arrayOf("channel-sentinel"),
                disabledChannels = arrayOf("channel-disabled-sentinel"),
                enabledFieldFormatChannels = arrayOf("format-sentinel"),
                disabledFieldFormatChannels = arrayOf("format-disabled-sentinel"),
                enabledFrameworks = arrayOf("framework-sentinel"),
                disabledFrameworks = arrayOf("framework-disabled-sentinel")
            )
            group.write(settings, originalEnabled.toTypedArray(), originalDisabled.toTypedArray())
            val unrelatedBefore = settings.deepCopy()
            val descriptors = knownIds.map { rawId ->
                testFeatureDescriptor(
                    id = "${group.name.lowercase()}/$rawId",
                    defaultEnabled = defaults.getValue(rawId),
                    bridge = LegacyOverrideArrayStateBridge(group, rawId)
                )
            }
            val snapshot = featureSnapshotOf(*descriptors.toTypedArray())
            val transaction = FeatureSettingsTransaction(snapshot, settings)
            val requested = knownIds.associateWith { random.nextBoolean() }.toMutableMap()
            if (knownIds.all { rawId ->
                    requested.getValue(rawId) == transaction.desiredState(
                        descriptors.first { descriptor ->
                            (descriptor.stateBridge as LegacyOverrideArrayStateBridge).rawLegacyId == rawId
                        }.id
                    )
                }
            ) {
                requested[knownIds.first()] = !requested.getValue(knownIds.first())
            }
            descriptors.forEach { descriptor ->
                val rawId = (descriptor.stateBridge as LegacyOverrideArrayStateBridge).rawLegacyId
                transaction.setDesiredState(descriptor.id, requested.getValue(rawId))
            }

            transaction.commit(settings)

            val actualEnabled = group.readEnabled(settings).toList()
            val actualDisabled = group.readDisabled(settings).toList()
            val knownSet = knownIds.toSet()
            assertEquals(
                "Unknown enabled entries should be preserved for seed=$seed",
                originalEnabled.filter { it !in knownSet },
                actualEnabled.filter { it !in knownSet }
            )
            assertEquals(
                "Unknown disabled entries should be preserved for seed=$seed",
                originalDisabled.filter { it !in knownSet },
                actualDisabled.filter { it !in knownSet }
            )

            descriptors.forEach { descriptor ->
                val bridge = descriptor.stateBridge as LegacyOverrideArrayStateBridge
                val rawId = bridge.rawLegacyId
                val desired = requested.getValue(rawId)
                val defaultEnabled = defaults.getValue(rawId)
                assertEquals(
                    "Reloaded state should match requested state for rawId=$rawId seed=$seed",
                    desired,
                    bridge.readDesired(settings, defaultEnabled)
                )
                val enabledCount = actualEnabled.count { it == rawId }
                val disabledCount = actualDisabled.count { it == rawId }
                assertTrue(
                    "Known id should occur at most once across arrays for rawId=$rawId seed=$seed",
                    enabledCount + disabledCount <= 1
                )
                assertEquals(
                    "Enabled override should exist only for default-off deviations for rawId=$rawId seed=$seed",
                    if (desired && !defaultEnabled) 1 else 0,
                    enabledCount
                )
                assertEquals(
                    "Disabled override should exist only for default-on deviations for rawId=$rawId seed=$seed",
                    if (!desired && defaultEnabled) 1 else 0,
                    disabledCount
                )
            }

            LegacyOverrideArrayGroup.entries.filter { it != group }.forEach { unrelatedGroup ->
                assertEquals(
                    "Unrelated enabled group should remain unchanged for seed=$seed",
                    unrelatedGroup.readEnabled(unrelatedBefore).toList(),
                    unrelatedGroup.readEnabled(settings).toList()
                )
                assertEquals(
                    "Unrelated disabled group should remain unchanged for seed=$seed",
                    unrelatedGroup.readDisabled(unrelatedBefore).toList(),
                    unrelatedGroup.readDisabled(settings).toList()
                )
            }
        }
    }
}

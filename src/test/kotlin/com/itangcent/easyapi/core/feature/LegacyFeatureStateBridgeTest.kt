package com.itangcent.easyapi.core.feature

import com.itangcent.easyapi.core.settings.module.GeneralSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyFeatureStateBridgeTest {

    @Test
    fun testReadUsesExplicitEnabledBeforeDisabledAndDefault() {
        val bridge = LegacyOverrideArrayStateBridge(LegacyOverrideArrayGroup.CHANNELS, "known")

        assertTrue(
            "Default-on feature should be enabled without an override",
            bridge.readDesired(GeneralSettings(), true)
        )
        assertFalse(
            "Default-on feature should honor a disabled override",
            bridge.readDesired(GeneralSettings(disabledChannels = arrayOf("known")), true)
        )
        assertFalse(
            "Default-off feature should remain disabled without an override",
            bridge.readDesired(GeneralSettings(), false)
        )
        assertTrue(
            "Explicit enabled should win when an id appears in both arrays",
            bridge.readDesired(
                GeneralSettings(
                    enabledChannels = arrayOf("known"),
                    disabledChannels = arrayOf("known")
                ),
                false
            )
        )
    }

    @Test
    fun testRelatedCommitPreservesUnknownEntriesAndNormalizesKnownEntries() {
        val defaultOn = testFeatureDescriptor(
            id = "channel/on",
            defaultEnabled = true,
            bridge = LegacyOverrideArrayStateBridge(LegacyOverrideArrayGroup.CHANNELS, "on")
        )
        val defaultOff = testFeatureDescriptor(
            id = "channel/off",
            defaultEnabled = false,
            bridge = LegacyOverrideArrayStateBridge(LegacyOverrideArrayGroup.CHANNELS, "off")
        )
        val snapshot = featureSnapshotOf(defaultOn, defaultOff)
        val settings = GeneralSettings(
            enabledChannels = arrayOf("unknown-e", "unknown-e", "on", "on", "off", "off"),
            disabledChannels = arrayOf("unknown-d", "on", "off", "unknown-d")
        )
        val transaction = FeatureSettingsTransaction(snapshot, settings)

        transaction.setDesiredState(defaultOn.id, false)
        transaction.commit(settings)

        assertEquals(
            "Unknown enabled entries should keep order and duplicates",
            listOf("unknown-e", "unknown-e", "off"),
            settings.enabledChannels.toList()
        )
        assertEquals(
            "Unknown disabled entries should keep order and duplicates",
            listOf("unknown-d", "unknown-d", "on"),
            settings.disabledChannels.toList()
        )
        assertFalse(
            "Default-on feature should reload as disabled",
            defaultOn.stateBridge.readDesired(settings, defaultOn.defaultEnabled)
        )
        assertTrue(
            "Default-off feature should reload as enabled",
            defaultOff.stateBridge.readDesired(settings, defaultOff.defaultEnabled)
        )
    }

    @Test
    fun testUnmodifiedLegacyGroupKeepsOriginalArrayInstances() {
        val core = testFeatureDescriptor(
            id = "core",
            bridge = DirectBooleanStateBridge(DirectBooleanSetting.API_SCAN_ENABLED)
        )
        val channel = testFeatureDescriptor(
            id = "channel/known",
            bridge = LegacyOverrideArrayStateBridge(LegacyOverrideArrayGroup.CHANNELS, "known")
        )
        val snapshot = featureSnapshotOf(core, channel)
        val enabled = arrayOf("unknown", "known", "known")
        val disabled = arrayOf("known", "other")
        val settings = GeneralSettings(enabledChannels = enabled, disabledChannels = disabled)
        val transaction = FeatureSettingsTransaction(snapshot, settings)

        transaction.setDesiredState(core.id, false)
        transaction.commit(settings)

        assertSame("Unmodified enabled array should not be replaced", enabled, settings.enabledChannels)
        assertSame("Unmodified disabled array should not be replaced", disabled, settings.disabledChannels)
    }

    @Test
    fun testMixedKnownAndUnknownEntriesArePreservedAcrossAllLegacyGroups() {
        val descriptors = listOf(
            testFeatureDescriptor(
                id = "channel/known-channel",
                bridge = LegacyOverrideArrayStateBridge(LegacyOverrideArrayGroup.CHANNELS, "known-channel")
            ),
            testFeatureDescriptor(
                id = "field-format/known-format",
                bridge = LegacyOverrideArrayStateBridge(
                    LegacyOverrideArrayGroup.FIELD_FORMAT_CHANNELS,
                    "known-format"
                )
            ),
            testFeatureDescriptor(
                id = "framework/known-framework",
                bridge = LegacyOverrideArrayStateBridge(LegacyOverrideArrayGroup.FRAMEWORKS, "known-framework")
            )
        )
        val settings = GeneralSettings(
            enabledChannels = arrayOf("unknown-channel"),
            disabledFieldFormatChannels = arrayOf("unknown-format"),
            enabledFrameworks = arrayOf("unknown-framework")
        )
        val transaction = FeatureSettingsTransaction(
            featureSnapshotOf(*descriptors.toTypedArray()),
            settings
        )
        descriptors.forEach { descriptor -> transaction.setDesiredState(descriptor.id, false) }

        transaction.commit(settings)

        assertEquals(listOf("unknown-channel"), settings.enabledChannels.toList())
        assertEquals(listOf("known-channel"), settings.disabledChannels.toList())
        assertEquals(
            listOf("unknown-format", "known-format"),
            settings.disabledFieldFormatChannels.toList()
        )
        assertEquals(listOf("unknown-framework"), settings.enabledFrameworks.toList())
        assertEquals(listOf("known-framework"), settings.disabledFrameworks.toList())
    }
}

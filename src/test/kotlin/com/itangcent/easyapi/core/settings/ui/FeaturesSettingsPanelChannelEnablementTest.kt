package com.itangcent.easyapi.core.settings.ui

import com.itangcent.easyapi.core.feature.FeatureId
import com.itangcent.easyapi.core.feature.FeatureRegistry
import com.itangcent.easyapi.core.feature.FeatureSettingsTransaction
import com.itangcent.easyapi.core.feature.LegacyFeatureContributor
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class FeaturesSettingsPanelChannelEnablementTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testRegistryDrivenChannelControlsUseLegacyDesiredState() {
        val snapshot = FeatureRegistry.getInstance(project).snapshot()
        val settings = GeneralSettings(
            enabledChannels = arrayOf("http-client"),
            disabledChannels = arrayOf("markdown")
        )
        val transaction = FeatureSettingsTransaction(snapshot, settings)

        runSettingsUiTestOnEdt {
            val panel = FeatureSettingsPanel(snapshot)
            panel.bindTransaction(transaction)

            assertTrue(
                "Export Channels should be rendered from retained descriptors",
                panel.renderedGroupTitlesForTest().contains(LegacyFeatureContributor.EXPORT_CHANNELS_GROUP.displayName)
            )
            assertEquals(false, panel.desiredStateForTest(FeatureId("channel/markdown")))
            assertEquals(true, panel.desiredStateForTest(FeatureId("channel/http-client")))
        }
    }

    fun testChannelCommitPreservesUnknownEntries() {
        val snapshot = FeatureRegistry.getInstance(project).snapshot()
        val settings = GeneralSettings(
            enabledChannels = arrayOf("unknown-enabled", "unknown-enabled"),
            disabledChannels = arrayOf("unknown-disabled")
        )
        val transaction = FeatureSettingsTransaction(snapshot, settings)

        runSettingsUiTestOnEdt {
            val panel = FeatureSettingsPanel(snapshot)
            panel.bindTransaction(transaction)
            panel.setDesiredStateForTest(FeatureId("channel/markdown"), false)
        }
        transaction.commit(settings)

        assertEquals(
            "Unknown enabled entries should retain order and duplicates",
            listOf("unknown-enabled", "unknown-enabled"),
            settings.enabledChannels.toList()
        )
        assertEquals(
            "Unknown disabled entries should be preserved before the known override",
            listOf("unknown-disabled", "markdown"),
            settings.disabledChannels.toList()
        )
    }
}

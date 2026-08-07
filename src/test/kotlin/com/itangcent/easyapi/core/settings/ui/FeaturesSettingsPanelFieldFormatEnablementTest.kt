package com.itangcent.easyapi.core.settings.ui

import com.itangcent.easyapi.core.feature.FeatureId
import com.itangcent.easyapi.core.feature.FeatureRegistry
import com.itangcent.easyapi.core.feature.FeatureSettingsTransaction
import com.itangcent.easyapi.core.feature.LegacyFeatureContributor
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class FeaturesSettingsPanelFieldFormatEnablementTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testRegistryDrivenFieldFormatControlsRenderProductionMetadata() {
        val snapshot = FeatureRegistry.getInstance(project).snapshot()
        val transaction = FeatureSettingsTransaction(snapshot, GeneralSettings())

        runSettingsUiTestOnEdt {
            val panel = FeatureSettingsPanel(snapshot)
            panel.bindTransaction(transaction)

            assertTrue(
                "Field Format Channels should be rendered from retained descriptors",
                panel.renderedGroupTitlesForTest()
                    .contains(LegacyFeatureContributor.FIELD_FORMAT_CHANNELS_GROUP.displayName)
            )
            listOf("json", "json5", "properties", "yaml").forEach { id ->
                assertEquals(
                    "Production format '$id' should retain its default Desired state",
                    true,
                    panel.desiredStateForTest(FeatureId("field-format/$id"))
                )
            }
        }
    }

    fun testFieldFormatCommitUsesRawLegacyIdAndPreservesUnknownEntry() {
        val snapshot = FeatureRegistry.getInstance(project).snapshot()
        val settings = GeneralSettings(
            enabledFieldFormatChannels = arrayOf("unknown-enabled"),
            disabledFieldFormatChannels = arrayOf("unknown-disabled")
        )
        val transaction = FeatureSettingsTransaction(snapshot, settings)

        runSettingsUiTestOnEdt {
            val panel = FeatureSettingsPanel(snapshot)
            panel.bindTransaction(transaction)
            panel.setDesiredStateForTest(FeatureId("field-format/json"), false)
        }
        transaction.commit(settings)

        assertEquals(listOf("unknown-enabled"), settings.enabledFieldFormatChannels.toList())
        assertEquals(
            listOf("unknown-disabled", "json"),
            settings.disabledFieldFormatChannels.toList()
        )
        assertFalse(
            "Namespaced ids must not leak into legacy arrays",
            settings.disabledFieldFormatChannels.contains("field-format/json")
        )
    }
}

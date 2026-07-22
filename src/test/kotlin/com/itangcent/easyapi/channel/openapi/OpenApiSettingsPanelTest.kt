package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.ui.SettingsPanel
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlin.reflect.full.memberProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Tests for [OpenApiSettingsPanel].
 *
 * The panel now exposes ONLY the
 * default `outputFormat` combo with three options (`JSON` / `YAML` /
 * `Always Ask`, default `Always Ask`). The v1 envelope fields
 * (`infoTitle` / `infoVersion` / `infoDescription` / `serverUrl`) were
 * removed from `OpenApiSettings` — those values vary per project and
 * belong in rule scripts.
 *
 * Verifies the panel:
 *  - extends `SettingsPanel<Settings>` (typed against the
 *    `Settings` base so the configurable's `ChannelPanelEntry.panel` cast works
 *    without a per-channel `Settings` subtype);
 *  - reads from / writes to [SettingBinder] (never touches state components);
 *  - round-trips the persisted `outputFormat` field;
 *  - reports `isModified` correctly after a UI change.
 *
 * Uses [EasyApiLightCodeInsightFixtureTestCase] so the
 * [com.itangcent.easyapi.core.settings.DefaultSettingBinder] is registered in
 * `setUp` (the panel depends on it for reads/writes).
 */
class OpenApiSettingsPanelTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var panel: OpenApiSettingsPanel

    override fun setUp() {
        super.setUp()
        panel = OpenApiSettingsPanel(project)
    }

    fun testComponentIsNotNull() {
        assertNotNull(panel.component)
    }

    fun testExtendsSettingsPanelOfSettings() {
        // Typed as SettingsPanel<Settings> (NOT SettingsPanel<OpenApiSettings>)
        // so the configurable's ChannelPanelEntry.panel cast works.
        assertTrue(
            "OpenApiSettingsPanel should be assignable to SettingsPanel<Settings>",
            panel is SettingsPanel<Settings>
        )
    }

    fun testResetFromNullDoesNotThrow() {
        panel.resetFrom(null)
    }

    fun testResetFromDefaultSettingsPopulatesUIWithDefaults() {
        // outputFormat="ALWAYS_ASK" → "Always Ask" in UI.
        panel.resetFrom(null)

        // The raw stored value is "ALWAYS_ASK" (the OpenApiSettings default);
        // the panel maps it to the UI display string "Always Ask".
        val s = SettingBinder.getInstance(project).read(OpenApiSettings::class)
        assertEquals("ALWAYS_ASK", s.outputFormat)
        assertEquals("Always Ask", panel.outputFormatText())
    }

    fun testSettingsHasOnlyOutputFormatProperty() {
        // The v1 envelope fields (infoTitle / infoVersion / infoDescription /
        // serverUrl) were removed. Reflection-level guard so
        // accidental re-introduction is caught by this test.
        val propertyNames = OpenApiSettings::class.memberProperties.map { it.name }
        assertEquals(
            "OpenApiSettings should have only the outputFormat property, got: $propertyNames",
            listOf("outputFormat"),
            propertyNames,
        )
    }

    fun testResetFromPopulatesUIFieldsFromSettings() {
        val binder = SettingBinder.getInstance(project)
        val settings = binder.read(OpenApiSettings::class).apply {
            outputFormat = "YAML"
        }
        binder.save(settings)

        panel.resetFrom(settings)
        assertEquals("YAML", panel.outputFormatText())
    }

    fun testResetFromMapsAlwaysAskStoredValueToUiString() {
        val binder = SettingBinder.getInstance(project)
        val settings = binder.read(OpenApiSettings::class).apply {
            outputFormat = "ALWAYS_ASK"
        }
        binder.save(settings)

        panel.resetFrom(settings)
        assertEquals("Always Ask", panel.outputFormatText())
    }

    fun testIsModifiedReturnsFalseAfterResetFrom() {
        // After resetFrom, the UI fields match the persisted settings — isModified=false.
        val s = SettingBinder.getInstance(project).read(OpenApiSettings::class)
        panel.resetFrom(s)
        assertFalse(panel.isModified(s))
    }

    fun testIsModifiedReturnsTrueWhenFormatChanged() {
        val s = SettingBinder.getInstance(project).read(OpenApiSettings::class)
        panel.resetFrom(s)

        panel.setOutputFormat("YAML")
        assertTrue(panel.isModified(s))
    }

    fun testIsModifiedReturnsTrueWhenAlwaysAskSelected() {
        val s = SettingBinder.getInstance(project).read(OpenApiSettings::class).apply {
            outputFormat = "JSON"
        }
        SettingBinder.getInstance(project).save(s)
        panel.resetFrom(s)

        panel.setOutputFormat("Always Ask")
        assertTrue(panel.isModified(s))
    }

    fun testApplyToPersistsOutputFormat() {
        panel.setOutputFormat("YAML")

        panel.applyTo(OpenApiSettings())

        val stored = SettingBinder.getInstance(project).read(OpenApiSettings::class)
        assertEquals("YAML", stored.outputFormat)
    }

    fun testApplyToPersistsAlwaysAskFormat() {
        panel.setOutputFormat("Always Ask")

        panel.applyTo(OpenApiSettings())

        val stored = SettingBinder.getInstance(project).read(OpenApiSettings::class)
        assertEquals("ALWAYS_ASK", stored.outputFormat)
    }

    fun testRoundTripResetApplyIsModified() {
        // Set UI → applyTo → read fresh → resetFrom → isModified=false.
        panel.setOutputFormat("YAML")

        panel.applyTo(OpenApiSettings())

        val fresh = SettingBinder.getInstance(project).read(OpenApiSettings::class)
        panel.resetFrom(fresh)
        assertFalse("After apply+reset, panel should match persisted state", panel.isModified(fresh))
    }

    fun testRoundTripAllThreeFormats() {
        // Round-trip each of the three format options through apply/reset.
        for (stored in listOf("JSON", "YAML", "ALWAYS_ASK")) {
            panel.setOutputFormat(when (stored) {
                "JSON" -> "JSON"
                "YAML" -> "YAML"
                else -> "Always Ask"
            })
            panel.applyTo(OpenApiSettings())

            val fresh = SettingBinder.getInstance(project).read(OpenApiSettings::class)
            assertEquals("Round-trip failed for $stored", stored, fresh.outputFormat)

            panel.resetFrom(fresh)
            assertFalse("isModified should be false after reset for $stored", panel.isModified(fresh))
        }
    }
}

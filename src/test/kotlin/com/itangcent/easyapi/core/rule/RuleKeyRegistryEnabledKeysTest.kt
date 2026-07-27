package com.itangcent.easyapi.core.rule

import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.internal.PluginInfo.PLUGIN_ID
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * IDE-fixture tests for [RuleKeyRegistry.enabledKeys] — the
 * enablement-aware view that post-filters [RuleKeyRegistry.allKeys] by
 * channel/framework enabled state (design C4a + AC-S4).
 *
 * Registers a stub `"postman"` channel via the `channel` extension point so
 * the channel-prefix check on general `postman.*` keys (declared in
 * [RuleKeys]) can be exercised end-to-end. The stub channel contributes no
 * rule keys of its own — the `postman.*` keys remain general-sourced, and
 * the prefix check is what filters them.
 */
class RuleKeyRegistryEnabledKeysTest : EasyApiLightCodeInsightFixtureTestCase() {

    /**
     * Minimal [Channel] stub with id `"postman"` so the channel-prefix
     * check in [RuleKeyRegistry.isEnabledSource] can resolve the owner.
     * `enabledByDefault = true` mirrors the real PostmanChannel.
     */
    private class StubPostmanChannel : Channel {
        override val id: String = "postman"
        override val displayName: String = "Postman (stub)"
        override val enabledByDefault: Boolean = true
        override suspend fun export(context: ExportContext): ExportResult =
            ExportResult.Success(0, "")
    }

    override fun setUp() {
        super.setUp()
        // Register the stub postman channel via the project-scoped EP.
        project.extensionArea
            .getExtensionPoint<Channel>("$PLUGIN_ID.channel")
            .registerExtension(StubPostmanChannel(), testRootDisposable)
        // Default: no explicit preferences → postman enabled by default.
        SettingBinder.getInstance(project).save(GeneralSettings())
    }

    // --- AC-S4: postman.* filtered when Postman disabled ---

    fun testEnabledKeys_includesPostmanKeys_whenPostmanEnabled() {
        val registry = RuleKeyRegistry.getInstance(project)
        val enabledNames = registry.enabledKeys().map { it.key.name }.toSet()
        assertTrue(
            "postman.test should be present when Postman is enabled",
            "postman.test" in enabledNames
        )
        assertTrue(
            "postman.prerequest should be present when Postman is enabled",
            "postman.prerequest" in enabledNames
        )
    }

    fun testEnabledKeys_omitsPostmanKeys_whenPostmanDisabled() {
        SettingBinder.getInstance(project).save(
            GeneralSettings(disabledChannels = arrayOf("postman"))
        )
        val registry = RuleKeyRegistry.getInstance(project)
        val enabledNames = registry.enabledKeys().map { it.key.name }.toSet()
        assertFalse(
            "postman.test should be absent when Postman is disabled (AC-S4)",
            "postman.test" in enabledNames
        )
        assertFalse(
            "postman.prerequest should be absent when Postman is disabled",
            "postman.prerequest" in enabledNames
        )
    }

    fun testAllKeys_stillIncludesPostmanKeys_whenPostmanDisabled() {
        // Regression guard: allKeys() is unfiltered so the Settings UI can
        // still browse and re-enable disabled-source keys (design D8).
        SettingBinder.getInstance(project).save(
            GeneralSettings(disabledChannels = arrayOf("postman"))
        )
        val registry = RuleKeyRegistry.getInstance(project)
        val allNames = registry.allKeys().map { it.key.name }.toSet()
        assertTrue(
            "allKeys() must still include postman.test when Postman is disabled",
            "postman.test" in allNames
        )
    }

    // --- General/implicit keys without channel prefix always present ---

    fun testEnabledKeys_alwaysIncludesGeneralKeysWithoutChannelPrefix() {
        SettingBinder.getInstance(project).save(
            GeneralSettings(disabledChannels = arrayOf("postman"))
        )
        val registry = RuleKeyRegistry.getInstance(project)
        val enabledNames = registry.enabledKeys().map { it.key.name }.toSet()
        assertTrue("api.name should always be present", "api.name" in enabledNames)
        assertTrue("field.ignore should always be present", "field.ignore" in enabledNames)
        assertTrue("method.doc should always be present", "method.doc" in enabledNames)
    }

    fun testEnabledKeys_alwaysIncludesImplicitKeysWithoutChannelPrefix() {
        SettingBinder.getInstance(project).save(
            GeneralSettings(disabledChannels = arrayOf("postman"))
        )
        val registry = RuleKeyRegistry.getInstance(project)
        val enabledNames = registry.enabledKeys().map { it.key.name }.toSet()
        assertTrue("max.deep should always be present", "max.deep" in enabledNames)
        assertTrue("max.elements should always be present", "max.elements" in enabledNames)
    }

    // --- enabledKeys is a subset of allKeys ---

    fun testEnabledKeys_isSubsetOfAllKeys() {
        SettingBinder.getInstance(project).save(
            GeneralSettings(disabledChannels = arrayOf("postman"))
        )
        val registry = RuleKeyRegistry.getInstance(project)
        val allNames = registry.allKeys().map { it.key.name }.toSet()
        val enabledNames = registry.enabledKeys().map { it.key.name }.toSet()
        assertTrue(
            "enabledKeys must be a subset of allKeys",
            allNames.containsAll(enabledNames)
        )
    }
}

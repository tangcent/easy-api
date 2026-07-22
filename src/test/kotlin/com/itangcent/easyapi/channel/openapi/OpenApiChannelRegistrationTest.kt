package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.channel.spi.ChannelRegistry
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Integration smoke test verifying that [OpenApiChannel] is registered in
 * `plugin.xml` and discoverable via [ChannelRegistry].
 *
 * Unlike [com.itangcent.easyapi.channel.dummy.DummyChannelTest] which
 * programmatically registers a test-only channel via the EP, this test
 * asserts the production `plugin.xml` registration is live — so a missing
 * `<channel implementation="...OpenApiChannel"/>` line shows up as a failed
 * test, not a silent runtime miss.
 *
 * Uses [EasyApiLightCodeInsightFixtureTestCase] so the plugin.xml-declared
 * EPs are registered by the IntelliJ test framework's bootstrap.
 */
class OpenApiChannelRegistrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testOpenApiChannelIsRegisteredViaPluginXml() {
        val channel = ChannelRegistry.getInstance(project).getChannel("openapi")
        assertNotNull(
            "OpenApiChannel should be registered via plugin.xml and discoverable via ChannelRegistry",
            channel
        )
        assertEquals("openapi", channel?.id)
        assertEquals("OpenAPI (Beta)", channel?.displayName)
    }

    fun testOpenApiChannelAppearsInAllChannelsList() {
        val channels = ChannelRegistry.getInstance(project).allChannels()
        val openApi = channels.find { it is OpenApiChannel }
        assertNotNull(
            "OpenApiChannel should appear in ChannelRegistry.allChannels() (plugin.xml registration)",
            openApi
        )
    }

    fun testOpenApiChannelExposedAsAction() {
        // getActionChannels() filters by isEnabled; since OpenApiChannel is
        // disabled by default, verify the channel's action properties directly
        // via the unfiltered getChannel() path.
        val channel = ChannelRegistry.getInstance(project).getChannel("openapi")
        assertNotNull(channel)
        assertTrue(
            "OpenApiChannel should have exposeAsAction=true",
            channel!!.exposeAsAction
        )
        assertEquals("Export to OpenAPI", channel.actionText)
    }

    fun testOpenApiChannelAvailableForSettings() {
        // channelsForSettings() filters by isEnabled; since OpenApiChannel is
        // disabled by default, verify the channel contributes a settings type
        // directly via the unfiltered getChannel() path.
        val channel = ChannelRegistry.getInstance(project).getChannel("openapi")
        assertNotNull(channel)
        assertNotNull(
            "OpenApiChannel should contribute a settings type (settings tab)",
            channel!!.settingsType
        )
    }

    fun testOpenApiChannelDisabledByDefault() {
        val channel = ChannelRegistry.getInstance(project).getChannel("openapi")
        assertNotNull(channel)
        assertFalse(
            "OpenApiChannel should be disabled by default (opt-in channel)",
            ChannelRegistry.getInstance(project).isEnabled(channel!!)
        )
    }

    fun testOpenApiChannelIsAvailableForHttpEndpoints() {
        // Even with no registered endpoints, isAvailableFor returns true for an empty list.
        val channel = ChannelRegistry.getInstance(project).getChannel("openapi")
        assertNotNull(channel)
        assertTrue(
            "OpenApiChannel should be available for empty endpoint list",
            channel!!.isAvailableFor(emptyList())
        )
    }

    fun testOpenApiChannelCreatesOptionsPanel() {
        val channel = ChannelRegistry.getInstance(project).getChannel("openapi")
        assertNotNull(channel)
        val panel = channel!!.createOptionsPanel(project)
        assertNotNull(
            "OpenApiChannel.createOptionsPanel should return a non-null panel",
            panel
        )
        assertTrue(
            "OpenApiChannel.createOptionsPanel should return an OpenApiOptionsPanel",
            panel is OpenApiOptionsPanel
        )
    }

    fun testOpenApiChannelCreatesSettingsPanel() {
        val channel = ChannelRegistry.getInstance(project).getChannel("openapi")
        assertNotNull(channel)
        val panel = channel!!.createSettingsPanel(project)
        assertNotNull(
            "OpenApiChannel.createSettingsPanel should return a non-null panel",
            panel
        )
        assertTrue(
            "OpenApiChannel.createSettingsPanel should return an OpenApiSettingsPanel",
            panel is OpenApiSettingsPanel
        )
    }

    fun testOpenApiChannelRuleKeysAreRegistered() {
        val channel = ChannelRegistry.getInstance(project).getChannel("openapi")
        assertNotNull(channel)
        val keys = channel!!.ruleKeys()
        assertFalse(
            "OpenApiChannel should contribute OPENAPI_HOST and OPENAPI_FORMAT_AFTER rule keys",
            keys.isEmpty()
        )
    }
}

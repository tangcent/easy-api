package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class HoppscotchSettingsPanelPlatformTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var panel: HoppscotchSettingsPanel

    override fun setUp() {
        super.setUp()
        panel = HoppscotchSettingsPanel(project)
    }

    fun testResetFromDefaultSettings() {
        val settings = com.itangcent.easyapi.settings.Settings()
        panel.resetFrom(settings)
        assertFalse("Panel should not be modified after reset with defaults", panel.isModified(settings))
    }

    fun testResetFromCustomSettings() {
        val settings = com.itangcent.easyapi.settings.Settings().apply {
            hoppscotchToken = "my-token-12345678"
            hoppscotchServerUrl = "https://custom.hoppscotch.io"
            hoppscotchBackendUrl = "http://localhost:3170/v1"
        }
        panel.resetFrom(settings)
        assertFalse("Panel should not be modified after reset with custom values", panel.isModified(settings))
    }

    fun testApplyToDefaultSettings() {
        val settings = com.itangcent.easyapi.settings.Settings()
        panel.resetFrom(settings)

        val target = com.itangcent.easyapi.settings.Settings()
        panel.applyTo(target)

        // Default server URL
        assertNotNull(target.hoppscotchServerUrl)
    }

    fun testApplyToCustomSettings() {
        val settings = com.itangcent.easyapi.settings.Settings().apply {
            hoppscotchServerUrl = "https://custom.hoppscotch.io"
            hoppscotchBackendUrl = "http://localhost:3170/v1"
        }
        panel.resetFrom(settings)

        val target = com.itangcent.easyapi.settings.Settings()
        panel.applyTo(target)

        assertEquals("https://custom.hoppscotch.io", target.hoppscotchServerUrl)
        assertEquals("http://localhost:3170/v1", target.hoppscotchBackendUrl)
    }

    fun testIsModifiedNullSettings() {
        assertFalse(panel.isModified(null))
    }

    fun testResetFromNullSettings() {
        panel.resetFrom(null)
        // Should not throw
    }

    fun testComponentNotNull() {
        assertNotNull(panel.component)
    }

    fun testResetFromWithToken() {
        val settings = com.itangcent.easyapi.settings.Settings().apply {
            hoppscotchToken = "test-token-12345678"
        }
        panel.resetFrom(settings)
        // Should not throw
    }

    fun testResetFromWithBlankServerUrl() {
        val settings = com.itangcent.easyapi.settings.Settings().apply {
            hoppscotchServerUrl = ""
        }
        panel.resetFrom(settings)
        // Should not throw
    }
}

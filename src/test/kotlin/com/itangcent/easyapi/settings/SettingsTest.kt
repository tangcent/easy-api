package com.itangcent.easyapi.settings

import com.itangcent.easyapi.settings.state.ApplicationSettingsSupport
import com.itangcent.easyapi.settings.state.ProjectSettingsSupport
import org.junit.Assert.*
import org.junit.Test

class SettingsTest {

    @Test
    fun testDefaultSettings() {
        val settings = Settings()
        assertFalse("feignEnable should default to false", settings.feignEnable)
        assertTrue("jaxrsEnable should default to true", settings.jaxrsEnable)
        assertFalse("actuatorEnable should default to false", settings.actuatorEnable)
        assertTrue("grpcEnable should default to true", settings.grpcEnable)
        assertTrue("swaggerEnable should default to true", settings.swaggerEnable)
        assertEquals("httpTimeOut should default to 5", 5, settings.httpTimeOut)
        assertEquals("outputCharset should default to UTF-8", "UTF-8", settings.outputCharset)
    }

    @Test
    fun testSettingsCopyToApplicationSettings() {
        val source = Settings(feignEnable = true, httpTimeOut = 10)
        val target = Settings()
        (source as ApplicationSettingsSupport).copyTo(target)
        assertTrue("feignEnable should be copied", target.feignEnable)
        assertEquals("httpTimeOut should be copied", 10, target.httpTimeOut)
    }

    @Test
    fun testSettingsCopyToProjectSettings() {
        val source = Settings(postmanWorkspace = "workspace-123", postmanExportMode = "UPDATE")
        val target = Settings()
        (source as ProjectSettingsSupport).copyTo(target)
        assertEquals("postmanWorkspace should be copied", "workspace-123", target.postmanWorkspace)
        assertEquals("postmanExportMode should be copied", "UPDATE", target.postmanExportMode)
    }

    @Test
    fun testSettingsModification() {
        val settings = Settings()
        settings.feignEnable = true
        settings.httpTimeOut = 30
        settings.postmanToken = "test-token"
        assertTrue("feignEnable should be modified", settings.feignEnable)
        assertEquals("httpTimeOut should be modified", 30, settings.httpTimeOut)
        assertEquals("postmanToken should be modified", "test-token", settings.postmanToken)
    }
}

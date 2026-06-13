package com.itangcent.easyapi.settings.ui

import com.itangcent.easyapi.settings.Settings
import org.junit.Assert.*
import org.junit.Test

class HoppscotchSettingsPanelLogicTest {

    // --- Settings Hoppscotch fields ---

    @Test
    fun testSettings_hoppscotchDefaults() {
        val settings = Settings()
        assertNull(settings.hoppscotchToken)
        assertEquals("https://hoppscotch.io", settings.hoppscotchServerUrl)
        assertNull(settings.hoppscotchBackendUrl)
        assertNull(settings.hoppscotchRefreshToken)
    }

    @Test
    fun testSettings_hoppscotchCustomValues() {
        val settings = Settings(
            hoppscotchToken = "my-token",
            hoppscotchServerUrl = "https://custom.hoppscotch.io",
            hoppscotchBackendUrl = "http://localhost:3170/v1",
            hoppscotchRefreshToken = "refresh-token"
        )
        assertEquals("my-token", settings.hoppscotchToken)
        assertEquals("https://custom.hoppscotch.io", settings.hoppscotchServerUrl)
        assertEquals("http://localhost:3170/v1", settings.hoppscotchBackendUrl)
        assertEquals("refresh-token", settings.hoppscotchRefreshToken)
    }

    @Test
    fun testSettings_hoppscotchEquality() {
        val s1 = Settings(
            hoppscotchToken = "token",
            hoppscotchServerUrl = "https://hoppscotch.io",
            hoppscotchBackendUrl = "http://localhost:3170"
        )
        val s2 = Settings(
            hoppscotchToken = "token",
            hoppscotchServerUrl = "https://hoppscotch.io",
            hoppscotchBackendUrl = "http://localhost:3170"
        )
        assertEquals(s1, s2)
        assertEquals(s1.hashCode(), s2.hashCode())
    }

    @Test
    fun testSettings_hoppscotchInequality() {
        val s1 = Settings(hoppscotchToken = "token1")
        val s2 = Settings(hoppscotchToken = "token2")
        assertNotEquals(s1, s2)
    }

    @Test
    fun testSettings_hoppscotchServerUrlInequality() {
        val s1 = Settings(hoppscotchServerUrl = "https://a.com")
        val s2 = Settings(hoppscotchServerUrl = "https://b.com")
        assertNotEquals(s1, s2)
    }

    @Test
    fun testSettings_hoppscotchBackendUrlInequality() {
        val s1 = Settings(hoppscotchBackendUrl = "http://a:3170")
        val s2 = Settings(hoppscotchBackendUrl = "http://b:3170")
        assertNotEquals(s1, s2)
    }

    @Test
    fun testSettings_hoppscotchRefreshTokenInequality() {
        val s1 = Settings(hoppscotchRefreshToken = "rt1")
        val s2 = Settings(hoppscotchRefreshToken = "rt2")
        assertNotEquals(s1, s2)
    }

    // --- HoppscotchSettingsPanel logic (without Project) ---
    // Note: HoppscotchSettingsPanel requires a Project for login operations.
    // We test the settings data flow and logic separately.

    @Test
    fun testSettings_hoppscotchCopy() {
        val s1 = Settings(hoppscotchToken = "token1", hoppscotchServerUrl = "https://a.com")
        val s2 = s1.copy(hoppscotchToken = "token2")
        assertEquals("token2", s2.hoppscotchToken)
        assertEquals("https://a.com", s2.hoppscotchServerUrl)
        assertEquals("token1", s1.hoppscotchToken) // original unchanged
    }

    @Test
    fun testSettings_hoppscotchNullToken() {
        val settings = Settings(hoppscotchToken = null)
        assertNull(settings.hoppscotchToken)
    }

    @Test
    fun testSettings_hoppscotchBlankServerUrl() {
        val settings = Settings(hoppscotchServerUrl = "")
        assertEquals("", settings.hoppscotchServerUrl)
    }

    @Test
    fun testSettings_hoppscotchNullBackendUrl() {
        val settings = Settings(hoppscotchBackendUrl = null)
        assertNull(settings.hoppscotchBackendUrl)
    }
}

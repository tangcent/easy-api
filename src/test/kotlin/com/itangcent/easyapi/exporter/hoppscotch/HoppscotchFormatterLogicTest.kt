package com.itangcent.easyapi.exporter.hoppscotch

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for HoppscotchFormatter companion methods and pure logic.
 *
 * The main `format()` method requires a Project and RuleEngine, so it's
 * tested via integration tests. Here we test the companion/utility methods.
 */
class HoppscotchFormatterLogicTest {

    // ==================== parsePath tests ====================

    @Test
    fun `parsePath splits simple path`() {
        val parts = HoppscotchFormatter.parsePath("/api/users")
        assertEquals(listOf("api", "users"), parts)
    }

    @Test
    fun `parsePath splits path with trailing slash`() {
        val parts = HoppscotchFormatter.parsePath("/api/users/")
        assertEquals(listOf("api", "users"), parts)
    }

    @Test
    fun `parsePath splits path without leading slash`() {
        val parts = HoppscotchFormatter.parsePath("api/users")
        assertEquals(listOf("api", "users"), parts)
    }

    @Test
    fun `parsePath splits empty path`() {
        val parts = HoppscotchFormatter.parsePath("")
        assertEquals(listOf(""), parts)
    }

    @Test
    fun `parsePath splits single segment path`() {
        val parts = HoppscotchFormatter.parsePath("/users")
        assertEquals(listOf("users"), parts)
    }

    @Test
    fun `parsePath splits deeply nested path`() {
        val parts = HoppscotchFormatter.parsePath("/api/v1/users/{id}/details")
        assertEquals(listOf("api", "v1", "users", "{id}", "details"), parts)
    }

    @Test
    fun `parsePath splits root path`() {
        val parts = HoppscotchFormatter.parsePath("/")
        assertEquals(listOf(""), parts)
    }

    @Test
    fun `parsePath splits path with double slashes`() {
        val parts = HoppscotchFormatter.parsePath("/api//users")
        assertEquals(listOf("api", "", "users"), parts)
    }

    // ==================== HoppscotchFormatOptions tests ====================

    @Test
    fun `HoppscotchFormatOptions default values`() {
        val options = HoppscotchFormatOptions()
        assertEquals("https://<<host>>", options.defaultHost)
        assertTrue(options.appendTimestamp)
    }

    @Test
    fun `HoppscotchFormatOptions custom values`() {
        val options = HoppscotchFormatOptions(
            defaultHost = "https://api.example.com",
            appendTimestamp = false
        )
        assertEquals("https://api.example.com", options.defaultHost)
        assertFalse(options.appendTimestamp)
    }

    // ==================== HoppscotchAuthException tests ====================

    @Test
    fun `HoppscotchAuthException is an Exception with message`() {
        val exception = HoppscotchAuthException("Token expired")
        assertTrue(exception is Exception)
        assertEquals("Token expired", exception.message)
    }

    // ==================== MagicLinkSendResult tests ====================

    @Test
    fun `MagicLinkSendResult success case`() {
        val result = MagicLinkSendResult(success = true, deviceIdentifier = "dev-1")
        assertTrue(result.success)
        assertEquals("dev-1", result.deviceIdentifier)
        assertNull(result.errorMessage)
    }

    @Test
    fun `MagicLinkSendResult failure case`() {
        val result = MagicLinkSendResult(success = false, errorMessage = "Failed")
        assertFalse(result.success)
        assertNull(result.deviceIdentifier)
        assertEquals("Failed", result.errorMessage)
    }

    // ==================== MagicLinkVerifyResult tests ====================

    @Test
    fun `MagicLinkVerifyResult success case`() {
        val result = MagicLinkVerifyResult(
            success = true,
            accessToken = "access-123",
            refreshToken = "refresh-456"
        )
        assertTrue(result.success)
        assertEquals("access-123", result.accessToken)
        assertEquals("refresh-456", result.refreshToken)
        assertNull(result.errorMessage)
    }

    @Test
    fun `MagicLinkVerifyResult failure case`() {
        val result = MagicLinkVerifyResult(success = false, errorMessage = "Expired")
        assertFalse(result.success)
        assertNull(result.accessToken)
        assertEquals("Expired", result.errorMessage)
    }

    // ==================== FirebaseConfig tests ====================

    @Test
    fun `FirebaseConfig with all fields`() {
        val config = FirebaseConfig(
            apiKey = "key-123",
            projectId = "project-1",
            authDomain = "example.firebaseapp.com"
        )
        assertEquals("key-123", config.apiKey)
        assertEquals("project-1", config.projectId)
        assertEquals("example.firebaseapp.com", config.authDomain)
    }

    @Test
    fun `FirebaseConfig with null authDomain`() {
        val config = FirebaseConfig(
            apiKey = "key-123",
            projectId = "project-1",
            authDomain = null
        )
        assertNull(config.authDomain)
    }

    @Test
    fun `FirebaseConfig copy works`() {
        val config = FirebaseConfig(apiKey = "key-123", projectId = "project-1", authDomain = "example.com")
        val copy = config.copy(apiKey = "new-key")
        assertEquals("new-key", copy.apiKey)
        assertEquals("project-1", copy.projectId)
        assertEquals("example.com", copy.authDomain)
    }

    // ==================== HoppTeam tests ====================

    @Test
    fun `HoppTeam data class`() {
        val team = HoppTeam(id = "team-1", name = "My Team")
        assertEquals("team-1", team.id)
        assertEquals("My Team", team.name)
    }

    @Test
    fun `HoppTeam copy`() {
        val team = HoppTeam(id = "team-1", name = "My Team")
        val copy = team.copy(name = "Updated Team")
        assertEquals("team-1", copy.id)
        assertEquals("Updated Team", copy.name)
    }

    // ==================== HoppCollectionInfo tests ====================

    @Test
    fun `HoppCollectionInfo data class`() {
        val info = HoppCollectionInfo(id = "col-1", name = "My Collection")
        assertEquals("col-1", info.id)
        assertEquals("My Collection", info.name)
    }

    // ==================== HoppUploadResult tests ====================

    @Test
    fun `HoppUploadResult success with collectionId`() {
        val result = HoppUploadResult(success = true, message = "OK", collectionId = "col-1")
        assertTrue(result.success)
        assertEquals("OK", result.message)
        assertEquals("col-1", result.collectionId)
    }

    @Test
    fun `HoppUploadResult failure`() {
        val result = HoppUploadResult(success = false, message = "Upload failed")
        assertFalse(result.success)
        assertEquals("Upload failed", result.message)
        assertNull(result.collectionId)
    }

    @Test
    fun `HoppUploadResult default values`() {
        val result = HoppUploadResult(success = true)
        assertTrue(result.success)
        assertNull(result.message)
        assertNull(result.collectionId)
    }

    // ==================== HoppscotchAuthService.AuthProviderCheckResult tests ====================

    @Test
    fun `AuthProviderCheckResult has all expected values`() {
        val values = HoppscotchAuthService.AuthProviderCheckResult.values()
        assertEquals(4, values.size)
        assertTrue(values.contains(HoppscotchAuthService.AuthProviderCheckResult.SUPPORTED))
        assertTrue(values.contains(HoppscotchAuthService.AuthProviderCheckResult.EMAIL_NOT_ENABLED))
        assertTrue(values.contains(HoppscotchAuthService.AuthProviderCheckResult.NOT_SUPPORTED))
        assertTrue(values.contains(HoppscotchAuthService.AuthProviderCheckResult.CLOUD))
    }
}

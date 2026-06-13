package com.itangcent.easyapi.exporter.hoppscotch

import com.itangcent.easyapi.settings.Settings
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for HoppscotchAuthService pure logic methods.
 *
 * Methods like isLoggedIn, getServerUrl don't require IntelliJ platform services
 * and can be tested directly. Private methods are tested via reflection.
 *
 * Note: AuthProviderCheckResult is internal so it cannot be tested from this package.
 */
class HoppscotchAuthServicePureLogicTest {

    private val authService = HoppscotchAuthService()

    // ==================== isLoggedIn tests ====================

    @Test
    fun `isLoggedIn returns true when token is present`() {
        val settings = Settings(hoppscotchToken = "valid-token")
        assertTrue(authService.isLoggedIn(settings))
    }

    @Test
    fun `isLoggedIn returns false when token is null`() {
        val settings = Settings(hoppscotchToken = null)
        assertFalse(authService.isLoggedIn(settings))
    }

    @Test
    fun `isLoggedIn returns false when token is blank`() {
        val settings = Settings(hoppscotchToken = "   ")
        assertFalse(authService.isLoggedIn(settings))
    }

    @Test
    fun `isLoggedIn returns false when token is empty`() {
        val settings = Settings(hoppscotchToken = "")
        assertFalse(authService.isLoggedIn(settings))
    }

    // ==================== getServerUrl tests ====================

    @Test
    fun `getServerUrl returns configured URL when set`() {
        val settings = Settings(hoppscotchServerUrl = "https://custom.hoppscotch.example")
        assertEquals("https://custom.hoppscotch.example", authService.getServerUrl(settings))
    }

    @Test
    fun `getServerUrl returns default when null`() {
        val settings = Settings(hoppscotchServerUrl = null)
        assertEquals("https://hoppscotch.io", authService.getServerUrl(settings))
    }

    @Test
    fun `getServerUrl returns default when blank`() {
        val settings = Settings(hoppscotchServerUrl = "   ")
        assertEquals("https://hoppscotch.io", authService.getServerUrl(settings))
    }

    @Test
    fun `getServerUrl returns default when empty`() {
        val settings = Settings(hoppscotchServerUrl = "")
        assertEquals("https://hoppscotch.io", authService.getServerUrl(settings))
    }

    // ==================== extractOobCodeFromUrl via reflection ====================

    @Test
    fun `extractOobCodeFromUrl extracts oobCode parameter`() {
        val method = HoppscotchAuthService::class.java.getDeclaredMethod(
            "extractOobCodeFromUrl", String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(authService, "https://hoppscotch.io/enter?oobCode=ABC123&mode=signIn") as String?
        assertEquals("ABC123", result)
    }

    @Test
    fun `extractOobCodeFromUrl returns null when no oobCode`() {
        val method = HoppscotchAuthService::class.java.getDeclaredMethod(
            "extractOobCodeFromUrl", String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(authService, "https://hoppscotch.io/enter?mode=signIn") as String?
        assertNull(result)
    }

    @Test
    fun `extractOobCodeFromUrl handles oobCode as second parameter`() {
        val method = HoppscotchAuthService::class.java.getDeclaredMethod(
            "extractOobCodeFromUrl", String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(authService, "https://hoppscotch.io/enter?mode=signIn&oobCode=XYZ789") as String?
        assertEquals("XYZ789", result)
    }

    // ==================== extractTokenFromUrl via reflection ====================

    @Test
    fun `extractTokenFromUrl extracts token parameter`() {
        val method = HoppscotchAuthService::class.java.getDeclaredMethod(
            "extractTokenFromUrl", String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(authService, "https://example.com/enter?token=MYTOKEN123&email=test@test.com") as String?
        assertEquals("MYTOKEN123", result)
    }

    @Test
    fun `extractTokenFromUrl returns null when no token`() {
        val method = HoppscotchAuthService::class.java.getDeclaredMethod(
            "extractTokenFromUrl", String::class.java
        )
        method.isAccessible = true
        val result = method.invoke(authService, "https://example.com/enter?email=test@test.com") as String?
        assertNull(result)
    }

    // ==================== isCloudInstance ====================

    @Test
    fun `isCloudInstance returns true for hoppscotch io`() {
        assertTrue(authService.isCloudInstance("https://hoppscotch.io"))
    }

    @Test
    fun `isCloudInstance returns true for subdomain of hoppscotch io`() {
        assertTrue(authService.isCloudInstance("https://app.hoppscotch.io"))
    }

    @Test
    fun `isCloudInstance returns false for self-hosted`() {
        assertFalse(authService.isCloudInstance("https://custom.example.com"))
    }

    @Test
    fun `isCloudInstance returns false for localhost`() {
        assertFalse(authService.isCloudInstance("http://localhost:3000"))
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

    @Test
    fun `MagicLinkSendResult copy`() {
        val result = MagicLinkSendResult(success = true, deviceIdentifier = "dev-1")
        val copy = result.copy(errorMessage = "New error")
        assertTrue(copy.success)
        assertEquals("dev-1", copy.deviceIdentifier)
        assertEquals("New error", copy.errorMessage)
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

    @Test
    fun `MagicLinkVerifyResult copy`() {
        val result = MagicLinkVerifyResult(success = true, accessToken = "at", refreshToken = "rt")
        val copy = result.copy(accessToken = "new-at")
        assertEquals("new-at", copy.accessToken)
        assertEquals("rt", copy.refreshToken)
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
        val config = FirebaseConfig(apiKey = "key-123", projectId = "project-1", authDomain = null)
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

    @Test
    fun `FirebaseConfig equals and hashCode`() {
        val config1 = FirebaseConfig(apiKey = "key", projectId = "proj", authDomain = "domain")
        val config2 = FirebaseConfig(apiKey = "key", projectId = "proj", authDomain = "domain")
        assertEquals(config1, config2)
        assertEquals(config1.hashCode(), config2.hashCode())
    }

    // ==================== HoppscotchAuthException tests ====================

    @Test
    fun `HoppscotchAuthException is an Exception with message`() {
        val exception = HoppscotchAuthException("Token expired")
        assertTrue(exception is Exception)
        assertEquals("Token expired", exception.message)
    }
}

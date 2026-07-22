package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.HttpMetadata
import com.intellij.psi.PsiMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [OperationIdResolver].
 *
 * Covers operationId resolution order: `sourceMethod.name` →
 * `{method}_{path_slug}` slug fallback, and numeric `-N` suffix on
 * collision + `warn` log. The warn-log side effect is verified via a
 * returned [ResolutionResult] data class (the simpler alternative to
 * capturing an `IdeaLog` test fake).
 */
class OperationIdResolverTest {

    // ─── sourceMethod.name takes precedence ────────────────────────

    @Test
    fun sourceMethodNameUsedWhenAvailableAndUnique() {
        val meta = httpMetadata("/users/{id}", HttpMethod.GET)
        val sourceMethod = mockMethod("getUser")
        val used = mutableSetOf<String>()

        val result = OperationIdResolver.resolve(meta, sourceMethod, used)

        assertEquals("getUser", result.operationId)
        assertFalse("first use must not flag a collision", result.collisionDetected)
        assertTrue("resolved id must be registered in `used`", "getUser" in used)
    }

    // ─── slug fallback when sourceMethod is null ───────────────────

    @Test
    fun slugFallbackUsedWhenSourceMethodIsNull() {
        val meta = httpMetadata("/users/{id}", HttpMethod.GET)
        val used = mutableSetOf<String>()

        val result = OperationIdResolver.resolve(meta, sourceMethod = null, used = used)

        // path `/users/{id}` → slug `users_id` (after collapsing {/}/}/ to _,
        // trimming, lowercasing). Final: `get_users_id`.
        assertEquals("get_users_id", result.operationId)
        assertFalse(result.collisionDetected)
        assertTrue("get_users_id" in used)
    }

    @Test
    fun slugFallbackForPostMethod() {
        val meta = httpMetadata("/users", HttpMethod.POST)
        val used = mutableSetOf<String>()

        val result = OperationIdResolver.resolve(meta, sourceMethod = null, used = used)

        assertEquals("post_users", result.operationId)
        assertFalse(result.collisionDetected)
    }

    @Test
    fun slugFallbackForRootPath() {
        val meta = httpMetadata("/", HttpMethod.GET)
        val used = mutableSetOf<String>()

        val result = OperationIdResolver.resolve(meta, sourceMethod = null, used = used)

        // Root path produces an empty path slug after trim; the method name
        // alone is the operationId.
        assertEquals("get", result.operationId)
        assertFalse(result.collisionDetected)
    }

    @Test
    fun slugFallbackCollapsesConsecutiveSeparators() {
        // `/users/{id}/` → trailing slash collapses to nothing; the slug is
        // still `users_id` (no trailing/double underscore).
        val meta = httpMetadata("/users/{id}/", HttpMethod.GET)
        val used = mutableSetOf<String>()

        val result = OperationIdResolver.resolve(meta, sourceMethod = null, used = used)

        assertEquals("get_users_id", result.operationId)
    }

    // ─── numeric -N suffix on collision ────────────────────────────

    @Test
    fun numericSuffixOnSecondAndThirdCollision() {
        val meta = httpMetadata("/users/{id}", HttpMethod.GET)
        val sourceMethod = mockMethod("getUser")
        val used = mutableSetOf<String>()

        val first = OperationIdResolver.resolve(meta, sourceMethod, used)
        val second = OperationIdResolver.resolve(meta, sourceMethod, used)
        val third = OperationIdResolver.resolve(meta, sourceMethod, used)

        assertEquals("getUser", first.operationId)
        assertFalse(first.collisionDetected)

        assertEquals("getUser-2", second.operationId)
        assertTrue("second use must flag a collision (warn-log side effect)", second.collisionDetected)

        assertEquals("getUser-3", third.operationId)
        assertTrue("third use must flag a collision", third.collisionDetected)

        assertTrue("getUser" in used)
        assertTrue("getUser-2" in used)
        assertTrue("getUser-3" in used)
    }

    @Test
    fun collisionAcrossSameSlugFromDifferentMethodPaths() {
        // Two endpoints with no sourceMethod, both GET /users/{id} — same
        // slug fallback → second gets -2 suffix.
        val meta1 = httpMetadata("/users/{id}", HttpMethod.GET)
        val meta2 = httpMetadata("/users/{id}", HttpMethod.GET)
        val used = mutableSetOf<String>()

        val first = OperationIdResolver.resolve(meta1, sourceMethod = null, used = used)
        val second = OperationIdResolver.resolve(meta2, sourceMethod = null, used = used)

        assertEquals("get_users_id", first.operationId)
        assertFalse(first.collisionDetected)

        assertEquals("get_users_id-2", second.operationId)
        assertTrue(second.collisionDetected)
    }

    @Test
    fun collisionWhenSourceMethodNameClashesAcrossPaths() {
        // Same sourceMethod name on different paths — second gets -2 suffix.
        val meta1 = httpMetadata("/users/{id}", HttpMethod.GET)
        val meta2 = httpMetadata("/orders/{id}", HttpMethod.GET)
        val sourceMethod = mockMethod("getItem")
        val used = mutableSetOf<String>()

        val first = OperationIdResolver.resolve(meta1, sourceMethod, used)
        val second = OperationIdResolver.resolve(meta2, sourceMethod, used)

        assertEquals("getItem", first.operationId)
        assertFalse(first.collisionDetected)

        assertEquals("getItem-2", second.operationId)
        assertTrue(second.collisionDetected)
    }

    @Test
    fun collisionSuffixDoesNotConflictWithPreExistingDashNEntry() {
        // Pre-populate `used` with `getUser-2` so a fresh first call to
        // `getUser` succeeds, but the second collision has to skip past
        // `getUser-2` and pick `getUser-3`.
        val meta = httpMetadata("/users/{id}", HttpMethod.GET)
        val sourceMethod = mockMethod("getUser")
        val used = mutableSetOf("getUser-2")

        val first = OperationIdResolver.resolve(meta, sourceMethod, used)
        val second = OperationIdResolver.resolve(meta, sourceMethod, used)

        // `getUser` was not in `used` (only `getUser-2` was), so first wins.
        assertEquals("getUser", first.operationId)
        assertFalse(first.collisionDetected)

        // Second call: `getUser` is now in `used`. The next free slot is
        // `getUser-2`? No — `getUser-2` is also taken. Must skip to `-3`.
        assertEquals("getUser-3", second.operationId)
        assertTrue(second.collisionDetected)
    }

    @Test
    fun slugFallbackCollisionWithPreExistingEntry() {
        val meta = httpMetadata("/users/{id}", HttpMethod.GET)
        val used = mutableSetOf("get_users_id")

        val result = OperationIdResolver.resolve(meta, sourceMethod = null, used = used)

        assertEquals("get_users_id-2", result.operationId)
        assertTrue(result.collisionDetected)
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private fun httpMetadata(path: String, method: HttpMethod): HttpMetadata =
        HttpMetadata(path = path, method = method)

    private fun mockMethod(name: String): PsiMethod {
        val method = mock<PsiMethod>()
        whenever(method.name).thenReturn(name)
        return method
    }
}

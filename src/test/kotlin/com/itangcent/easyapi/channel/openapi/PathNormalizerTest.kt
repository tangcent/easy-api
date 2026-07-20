package com.itangcent.easyapi.channel.openapi

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [PathNormalizer].
 *
 * Covers:
 * - Spring colon form `/users/:id` → `/users/{id}`
 * - Spring regex form `/users/{id:\\d+}` → `/users/{id}` (regex stripped)
 * - Multi-char regex `/users/{id:[a-z]+}` → `/users/{id}`
 * - Already-correct passthrough `/users/{id}`
 * - No-params passthrough `/users`
 * - Malformed input `/users/{unclosed` → `null` (triggers skip-with-warn upstream)
 */
class PathNormalizerTest {

    @Test
    fun normalizesSpringColonForm() {
        assertEquals("/users/{id}", PathNormalizer.normalize("/users/:id"))
    }

    @Test
    fun normalizesSpringColonFormAtStart() {
        assertEquals("/{id}/details", PathNormalizer.normalize("/:id/details"))
    }

    @Test
    fun normalizesMultipleColonForm() {
        assertEquals("/users/{id}/posts/{postId}", PathNormalizer.normalize("/users/:id/posts/:postId"))
    }

    @Test
    fun stripsDigitRegexFromBraceForm() {
        assertEquals("/users/{id}", PathNormalizer.normalize("/users/{id:\\d+}"))
    }

    @Test
    fun stripsMultiCharRegexFromBraceForm() {
        assertEquals("/users/{id}", PathNormalizer.normalize("/users/{id:[a-z]+}"))
    }

    @Test
    fun stripsComplexRegexFromBraceForm() {
        assertEquals("/users/{id}", PathNormalizer.normalize("/users/{id:[0-9a-fA-F]{8,}}"))
    }

    @Test
    fun preservesAlreadyCorrectPath() {
        assertEquals("/users/{id}", PathNormalizer.normalize("/users/{id}"))
    }

    @Test
    fun preservesAlreadyCorrectMultiParamPath() {
        assertEquals(
            "/users/{userId}/posts/{postId}",
            PathNormalizer.normalize("/users/{userId}/posts/{postId}"),
        )
    }

    @Test
    fun preservesNoParamPath() {
        assertEquals("/users", PathNormalizer.normalize("/users"))
    }

    @Test
    fun preservesRootPath() {
        assertEquals("/", PathNormalizer.normalize("/"))
    }

    @Test
    fun returnsNullForUnclosedBrace() {
        assertNull(PathNormalizer.normalize("/users/{unclosed"))
    }

    @Test
    fun returnsNullForEmptyBrace() {
        assertNull(PathNormalizer.normalize("/users/{}"))
    }

    @Test
    fun returnsNullForUnmatchedClosingBrace() {
        assertNull(PathNormalizer.normalize("/users/id}"))
    }

    @Test
    fun returnsNullForSpacesInPath() {
        assertNull(PathNormalizer.normalize("/users/{id }"))
    }

    @Test
    fun normalizesColonThenAppliesGrammarCheck() {
        // After :id → {id} normalization, the path must still be a valid
        // OAS Path Template — no leftover invalid characters.
        assertEquals("/users/{id}", PathNormalizer.normalize("/users/:id"))
    }

    @Test
    fun doesNotTouchCurlyBracesInQuery() {
        // OAS Path Templates carry no query string — if the user supplied one
        // with braces, it's malformed for OAS purposes and we skip it.
        assertNull(PathNormalizer.normalize("/users?filter={x}"))
    }
}

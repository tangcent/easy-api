package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.core.rule.EventRuleMode
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.RuleKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain-JUnit tests for [OpenApiRuleKeys].
 *
 * Pins the contract:
 *  - `RuleKey.collectFrom(OpenApiRuleKeys)` returns exactly six keys:
 *    `openapi.host`, `openapi.server.url`, `openapi.info.title`,
 *    `openapi.info.version`, `openapi.info.description` (all string keys),
 *    plus `openapi.format.after` (event key, `EventRuleMode.THROW_IN_ERROR`).
 *  - No overlap with [RuleKeys] (the shared core rule keys) — channel-specific
 *    keys must live in the channel package to keep the DAG clean.
 *
 * Mirrors the [com.itangcent.easyapi.channel.hoppscotch.HoppscotchRuleKeys]
 * pattern (file at `channel/hoppscotch/HoppscotchRuleKeys.kt` is the reference).
 *
 * The four document-metadata keys
 * (`openapi.info.title`, `openapi.info.version`, `openapi.info.description`,
 * `openapi.server.url`) are evaluated against `ApiEndpoint.sourceClass`
 * inside `OpenApiChannel.export` — see [OpenApiRuleEvaluationTest].
 */
class OpenApiRuleKeysTest {

    @Test
    fun `collectFrom returns exactly six keys`() {
        val keys = RuleKey.collectFrom(OpenApiRuleKeys)
        assertEquals(6, keys.size)
    }

    @Test
    fun `openapi host key is a string key with the expected name`() {
        val keys = RuleKey.collectFrom(OpenApiRuleKeys)
        val hostKey = keys.firstOrNull { it.name == "openapi.host" }
        assertTrue(
            "Expected a key named 'openapi.host', got: ${keys.map { it.name }}",
            hostKey != null
        )
        assertTrue(
            "openapi.host should be a StringKey, got: ${hostKey?.javaClass?.name}",
            hostKey is RuleKey.StringKey
        )
    }

    @Test
    fun `openapi format after key is an event key with THROW_IN_ERROR mode`() {
        val keys = RuleKey.collectFrom(OpenApiRuleKeys)
        val formatAfterKey = keys.firstOrNull { it.name == "openapi.format.after" }
        assertTrue(
            "Expected a key named 'openapi.format.after', got: ${keys.map { it.name }}",
            formatAfterKey != null
        )
        assertTrue(
            "openapi.format.after should be an EventKey, got: ${formatAfterKey?.javaClass?.name}",
            formatAfterKey is RuleKey.EventKey
        )
        assertEquals(
            "openapi.format.after should use THROW_IN_ERROR mode",
            EventRuleMode.THROW_IN_ERROR,
            (formatAfterKey as RuleKey.EventKey).eventMode
        )
    }

    @Test
    fun `openapi rule keys are disjoint from core RuleKeys`() {
        // Channel-specific keys must not collide with the shared core keys
        // (DAG compliance — channel packages own their keys, core.rule owns
        // the shared ones). A collision would silently override the wrong
        // behavior in the rule engine.
        val openApiKeyNames = RuleKey.collectFrom(OpenApiRuleKeys).flatMap { it.allNames }.toSet()
        val coreKeyNames = RuleKey.collectFrom(RuleKeys).flatMap { it.allNames }.toSet()

        val intersection = openApiKeyNames.intersect(coreKeyNames)
        assertTrue(
            "OpenApi rule keys must not overlap with core RuleKeys; overlap: $intersection",
            intersection.isEmpty()
        )
    }

    @Test
    fun `openapi host key is directly accessible as a property`() {
        // Pin the property name — Channel.ruleKeys() uses RuleKey.collectFrom,
        // but tests and the channel layer also reference the constant directly
        // for readability. Renaming the property would break those call sites.
        assertEquals("openapi.host", OpenApiRuleKeys.OPENAPI_HOST.name)
        assertTrue(OpenApiRuleKeys.OPENAPI_HOST is RuleKey.StringKey)
    }

    @Test
    fun `openapi format after key is directly accessible as a property`() {
        assertEquals("openapi.format.after", OpenApiRuleKeys.OPENAPI_FORMAT_AFTER.name)
        assertTrue(OpenApiRuleKeys.OPENAPI_FORMAT_AFTER is RuleKey.EventKey)
        assertEquals(
            EventRuleMode.THROW_IN_ERROR,
            OpenApiRuleKeys.OPENAPI_FORMAT_AFTER.eventMode
        )
    }

    // ─── document-metadata rule keys ───────

    @Test
    fun `openapi server url key is a string key with the expected name`() {
        assertEquals("openapi.server.url", OpenApiRuleKeys.OPENAPI_SERVER_URL.name)
        assertTrue(OpenApiRuleKeys.OPENAPI_SERVER_URL is RuleKey.StringKey)
    }

    @Test
    fun `openapi info title key is a string key with the expected name`() {
        assertEquals("openapi.info.title", OpenApiRuleKeys.OPENAPI_INFO_TITLE.name)
        assertTrue(OpenApiRuleKeys.OPENAPI_INFO_TITLE is RuleKey.StringKey)
    }

    @Test
    fun `openapi info version key is a string key with the expected name`() {
        assertEquals("openapi.info.version", OpenApiRuleKeys.OPENAPI_INFO_VERSION.name)
        assertTrue(OpenApiRuleKeys.OPENAPI_INFO_VERSION is RuleKey.StringKey)
    }

    @Test
    fun `openapi info description key is a string key with the expected name`() {
        assertEquals("openapi.info.description", OpenApiRuleKeys.OPENAPI_INFO_DESCRIPTION.name)
        assertTrue(OpenApiRuleKeys.OPENAPI_INFO_DESCRIPTION is RuleKey.StringKey)
    }

    @Test
    fun `all six openapi rule keys are present in collectFrom`() {
        val names = RuleKey.collectFrom(OpenApiRuleKeys).map { it.name }.toSet()
        assertTrue("openapi.host missing in $names", "openapi.host" in names)
        assertTrue("openapi.server.url missing in $names", "openapi.server.url" in names)
        assertTrue("openapi.info.title missing in $names", "openapi.info.title" in names)
        assertTrue("openapi.info.version missing in $names", "openapi.info.version" in names)
        assertTrue("openapi.info.description missing in $names", "openapi.info.description" in names)
        assertTrue("openapi.format.after missing in $names", "openapi.format.after" in names)
    }
}

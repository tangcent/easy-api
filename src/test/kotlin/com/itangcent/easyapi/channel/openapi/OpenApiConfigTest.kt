package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.channel.spi.ChannelConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.memberProperties

/**
 * Plain-JUnit tests for [OpenApiConfig], [OpenApiOutputFormat], and
 * [OpenApiConfig.parseOutputFormat].
 *
 * Pins the contract that `OpenApiConfig` carries **only**
 * [OpenApiConfig.outputFormat] (default `ALWAYS_ASK`). The v1 `infoTitle` /
 * `infoVersion` / `infoDescription` / `serverUrl` fields were removed — those
 * values vary per project and belong in rule scripts.
 *
 * The v1 `OpenApiSettings.asOpenApiConfig` extension function was removed
 * (no fields to merge from settings — settings only carries `outputFormat`).
 */
class OpenApiConfigTest {

    @Test
    fun `default outputFormat is ALWAYS_ASK`() {
        // ALWAYS_ASK is the default — the user is prompted for
        // JSON / YAML at export time.
        val cfg = OpenApiConfig()
        assertEquals(OpenApiOutputFormat.ALWAYS_ASK, cfg.outputFormat)
    }

    @Test
    fun `outputFormat enum has JSON, YAML, and ALWAYS_ASK in declaration order`() {
        // Pin the enum shape — the settings layer stores the name as a string
        // ("JSON" / "YAML" / "ALWAYS_ASK"), so adding or reordering entries
        // would silently break persisted settings.
        val values = OpenApiOutputFormat.values().toList()
        assertEquals(
            listOf(OpenApiOutputFormat.JSON, OpenApiOutputFormat.YAML, OpenApiOutputFormat.ALWAYS_ASK),
            values,
        )
    }

    @Test
    fun `config is a ChannelConfig subtype`() {
        // Compile-time check that OpenApiConfig extends ChannelConfig — this
        // test exists so a refactor that breaks the inheritance is caught by
        // the test suite, not by a runtime ClassCastException in Channel.export.
        val cfg: ChannelConfig = OpenApiConfig()
        assertEquals(OpenApiOutputFormat.ALWAYS_ASK, (cfg as OpenApiConfig).outputFormat)
    }

    @Test
    fun `config has only outputFormat property`() {
        // Removed infoTitle / infoVersion / infoDescription /
        // serverUrl. The data class should have exactly one declared
        // property — outputFormat.
        val properties = OpenApiConfig::class.memberProperties.map { it.name }
        assertEquals(listOf("outputFormat"), properties)
    }

    @Test
    fun `constructed config round-trips outputFormat`() {
        val cfg = OpenApiConfig(outputFormat = OpenApiOutputFormat.YAML)
        assertEquals(OpenApiOutputFormat.YAML, cfg.outputFormat)
    }

    // ─── parseOutputFormat — case-insensitive parsing + fallback ─────────

    @Test
    fun `parseOutputFormat parses JSON case-insensitively`() {
        assertEquals(OpenApiOutputFormat.JSON, OpenApiConfig.parseOutputFormat("JSON"))
        assertEquals(OpenApiOutputFormat.JSON, OpenApiConfig.parseOutputFormat("json"))
        assertEquals(OpenApiOutputFormat.JSON, OpenApiConfig.parseOutputFormat("Json"))
    }

    @Test
    fun `parseOutputFormat parses YAML case-insensitively`() {
        assertEquals(OpenApiOutputFormat.YAML, OpenApiConfig.parseOutputFormat("YAML"))
        assertEquals(OpenApiOutputFormat.YAML, OpenApiConfig.parseOutputFormat("yaml"))
        assertEquals(OpenApiOutputFormat.YAML, OpenApiConfig.parseOutputFormat("Yaml"))
    }

    @Test
    fun `parseOutputFormat parses ALWAYS_ASK case-insensitively`() {
        // ALWAYS_ASK is the default value; it must round-trip
        // through the string form too.
        assertEquals(OpenApiOutputFormat.ALWAYS_ASK, OpenApiConfig.parseOutputFormat("ALWAYS_ASK"))
        assertEquals(OpenApiOutputFormat.ALWAYS_ASK, OpenApiConfig.parseOutputFormat("always_ask"))
        assertEquals(OpenApiOutputFormat.ALWAYS_ASK, OpenApiConfig.parseOutputFormat("Always_Ask"))
    }

    @Test
    fun `parseOutputFormat falls back to JSON for unrecognized values`() {
        // Matches the historical behavior: a corrupted or future-format name
        // must not crash the export; it defaults to JSON and emits a warn-level
        // log.
        assertEquals(OpenApiOutputFormat.JSON, OpenApiConfig.parseOutputFormat("BINARY"))
        assertEquals(OpenApiOutputFormat.JSON, OpenApiConfig.parseOutputFormat(""))
        assertEquals(OpenApiOutputFormat.JSON, OpenApiConfig.parseOutputFormat("garbage"))
    }

    @Test
    fun `config has no infoTitle infoVersion infoDescription serverUrl properties`() {
        // Explicit assertion that the removed fields do NOT
        // reappear — protects against an accidental re-add.
        val propertyNames = OpenApiConfig::class.memberProperties.map { it.name }
        assertTrue("infoTitle should be removed", "infoTitle" !in propertyNames)
        assertTrue("infoVersion should be removed", "infoVersion" !in propertyNames)
        assertTrue("infoDescription should be removed", "infoDescription" !in propertyNames)
        assertTrue("serverUrl should be removed", "serverUrl" !in propertyNames)
        assertNull("Optional envelope fields no longer exist", OpenApiConfig::class.memberProperties.find { it.name == "infoTitle" })
    }
}

package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.core.settings.Scope
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.StorageScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

/**
 * Plain-JUnit tests for [OpenApiSettings].
 *
 * Pins the contract that `OpenApiSettings` carries **only**
 * [OpenApiSettings.outputFormat] (default `"ALWAYS_ASK"`). The v1 `infoTitle`
 * / `infoVersion` / `infoDescription` / `serverUrl` fields were removed —
 * those values vary per project and belong in rule scripts.
 *
 * Pins the contract:
 *  - Defaults: `outputFormat="ALWAYS_ASK"`.
 *  - The only field carries `@StorageScope(Scope.APPLICATION)` — this is the
 *    regression test for a misannotated field silently not persisting
 *    (the SettingBinder only round-trips fields annotated with @StorageScope).
 *  - The field is `var` (not `val`) — the SettingBinder mutates the instance
 *    in place when loading from persistent state.
 *  - The class implements [Settings] (marker interface for modular settings).
 *
 * `outputFormat` is stored as a String (not the [OpenApiOutputFormat] enum)
 * because the unified settings state serializes to primitives; the channel
 * layer parses the string back to the enum at use time.
 */
class OpenApiSettingsTest {

    @Test
    fun `default outputFormat is ALWAYS_ASK string`() {
        // ALWAYS_ASK is the default. Stored as String — the
        // enum conversion happens in the channel layer.
        assertEquals("ALWAYS_ASK", OpenApiSettings().outputFormat)
    }

    @Test
    fun `settings implements Settings marker interface`() {
        // Compile-time + runtime check — SettingBinder.read<OpenApiSettings>
        // requires the marker interface.
        val s: Settings = OpenApiSettings()
        assertNotNull(s)
    }

    @Test
    fun `every field is annotated with StorageScope APPLICATION`() {
        // Regression test: if a new field is added without @StorageScope, the
        // SettingBinder silently skips it during round-trip persistence.
        val fields = OpenApiSettings::class.memberProperties.toList()
        // Sanity: confirm we're actually inspecting a non-trivial class with
        // exactly one field now.
        assertEquals(1, fields.size)

        fields.forEach { prop ->
            val annotation = prop.findAnnotation<StorageScope>()
            assertNotNull(
                "Property '${prop.name}' is missing @StorageScope — SettingBinder will not persist it",
                annotation
            )
            assertEquals(
                "Property '${prop.name}' should be APPLICATION-scoped",
                Scope.APPLICATION,
                annotation?.value
            )
        }
    }

    @Test
    fun `every field is var not val`() {
        // SettingBinder mutates the instance in place when loading from
        // persistent state — val fields would throw at reflection time.
        // Kotlin reflection surfaces `var` as `KMutableProperty1` (a subtype
        // of `KProperty1`); `val` is `KProperty1` only.
        val fields = OpenApiSettings::class.memberProperties.toList()
        fields.forEach { prop ->
            assertTrue(
                "Property '${prop.name}' must be `var` (SettingBinder mutates it), got: ${prop::class.simpleName}",
                prop is KMutableProperty1<*, *>
            )
        }
    }

    @Test
    fun `constructed settings round-trips outputFormat`() {
        val s = OpenApiSettings(outputFormat = "YAML")
        assertEquals("YAML", s.outputFormat)
    }

    @Test
    fun `fields are mutable`() {
        // SettingBinder.load(instance) writes to the instance — verify a
        // hand-written mutation actually changes the value, so we know the
        // `var` declaration is real (not just an annotation artifact).
        val s = OpenApiSettings()
        s.outputFormat = "YAML"
        assertEquals("YAML", s.outputFormat)
        s.outputFormat = "ALWAYS_ASK"
        assertEquals("ALWAYS_ASK", s.outputFormat)
    }

    @Test
    fun `settings has only outputFormat property`() {
        // Removed infoTitle / infoVersion / infoDescription /
        // serverUrl. The data class should have exactly one declared
        // property — outputFormat.
        val propertyNames = OpenApiSettings::class.memberProperties.map { it.name }
        assertEquals(listOf("outputFormat"), propertyNames)
    }

    @Test
    fun `settings has no infoTitle infoVersion infoDescription serverUrl properties`() {
        // Explicit assertion that the removed fields do NOT
        // reappear — protects against an accidental re-add.
        val propertyNames = OpenApiSettings::class.memberProperties.map { it.name }
        assertTrue("infoTitle should be removed", "infoTitle" !in propertyNames)
        assertTrue("infoVersion should be removed", "infoVersion" !in propertyNames)
        assertTrue("infoDescription should be removed", "infoDescription" !in propertyNames)
        assertTrue("serverUrl should be removed", "serverUrl" !in propertyNames)
    }
}

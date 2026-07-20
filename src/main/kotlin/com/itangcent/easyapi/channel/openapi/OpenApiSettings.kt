package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.core.settings.Scope
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.StorageScope

/**
 * Persistent defaults for the OpenAPI channel.
 *
 * Stored at APPLICATION scope via the unified
 * [com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState] —
 * no per-module state class or `plugin.xml` registration required.
 *
 * Read/written via [com.itangcent.easyapi.core.settings.SettingBinder]:
 * ```kotlin
 * val binder = SettingBinder.getInstance(project)
 * val settings = binder.read<OpenApiSettings>()
 * settings.outputFormat = "YAML"
 * binder.save(settings)
 * ```
 *
 * ## Refactor
 *
 * The v1 `infoTitle` / `infoVersion` / `infoDescription` / `serverUrl`
 * fields were removed — those values vary per project and belong in rule
 * scripts. The settings layer now persists **only** the default
 * [outputFormat] (default `"ALWAYS_ASK"`).
 *
 * ## Field semantics
 *
 * - [outputFormat] — stored as a String (`"JSON"` / `"YAML"` / `"ALWAYS_ASK"`)
 *   because the unified settings state serializes to primitives; the channel
 *   layer parses it back to [OpenApiOutputFormat] at use time via
 *   [OpenApiConfig.parseOutputFormat].
 *
 * All fields are `var` (not `val`) because the SettingBinder mutates the
 * instance in place when loading from persistent state. All fields carry
 * `@StorageScope(Scope.APPLICATION)` — fields without the annotation are
 * silently skipped by the binder.
 *
 * @see OpenApiConfig for the per-export counterpart (in-memory only).
 */
data class OpenApiSettings(
    @StorageScope(Scope.APPLICATION) var outputFormat: String = "ALWAYS_ASK",
) : Settings

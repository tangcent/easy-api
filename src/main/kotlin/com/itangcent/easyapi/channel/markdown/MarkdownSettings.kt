package com.itangcent.easyapi.channel.markdown

import com.itangcent.easyapi.core.settings.Scope
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.StorageScope

/**
 * Persistent settings for the Markdown channel.
 *
 * Stores the template sources that are too complex for the per-export
 * [MarkdownOptionsPanel]: a local template file path, a remote template URL,
 * and an inline template content override. These are resolved by
 * [com.itangcent.easyapi.channel.markdown.template.MarkdownTemplateResolver]
 * at export time via config keys, falling through the same precedence chain.
 *
 * All fields are `var` with `@StorageScope(Scope.APPLICATION)` because the
 * [com.itangcent.easyapi.core.settings.SettingBinder] mutates the instance
 * in place when loading from persistent state. Fields without the annotation
 * are silently skipped by the binder.
 *
 * @property templateFile Path to a local template file (e.g. `/path/to/template.md.tpl`).
 *  Defaults to empty string (no file override).
 * @property templateUrl http(s) URL to a remote template. Defaults to empty string.
 * @property templateInlineContent Inline template content (overrides file/url when non-blank).
 *  Defaults to empty string.
 * @property templateLanguage BCP-47 locale tag for a bundled language template.
 *  Defaults to "en" (the bundled default template).
 *
 * @see MarkdownSettingsPanel for the settings UI
 * @see MarkdownConfig for the per-export counterpart
 */
data class MarkdownSettings(
    @StorageScope(Scope.APPLICATION) var templateFile: String = "",
    @StorageScope(Scope.APPLICATION) var templateUrl: String = "",
    @StorageScope(Scope.APPLICATION) var templateInlineContent: String = "",
    @StorageScope(Scope.APPLICATION) var templateLanguage: String = "en",
) : Settings

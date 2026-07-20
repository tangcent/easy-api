package com.itangcent.easyapi.channel.openapi

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.FormBuilder
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.core.settings.ui.SettingsPanel
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Persistent settings panel for the OpenAPI channel.
 *
 * The v1 envelope fields (`infoTitle` / `infoVersion` / `infoDescription` /
 * `serverUrl`) were removed — those values vary per project and belong in
 * rule scripts. The settings panel now exposes ONLY the default
 * [outputFormat] combo with three options: `JSON`, `YAML`, `Always Ask`
 * (default).
 *
 * Edits the [OpenApiSettings] module via [SettingBinder] — never touching
 * state components directly. Mirrors
 * [com.itangcent.easyapi.channel.curl.CurlSettingsPanel] and
 * [com.itangcent.easyapi.channel.hoppscotch.HoppscotchSettingsPanel].
 *
 * ## `SettingsPanel<Settings>` typing
 *
 * Typed against the [Settings] base (NOT `SettingsPanel<OpenApiSettings>`) so
 * the configurable's `ChannelPanelEntry.panel` unchecked cast at
 * `EasyApiSettingsConfigurable` works for every channel without per-channel
 * `Settings` subtype awareness. The panel still reads/writes its own
 * [OpenApiSettings] module internally via [SettingBinder].
 *
 * @param project the IntelliJ project context
 * @see OpenApiSettings for the persisted module
 * @see OpenApiOptionsPanel for the per-export counterpart
 */
class OpenApiSettingsPanel(private val project: Project) : SettingsPanel<Settings> {

    /**
     * The three UI strings shown in the combo. The mapping to/from the
     * persistent [OpenApiSettings.outputFormat] string is via
     * [toUiFormat] / [fromUiFormat].
     */
    private val outputFormatCombo = ComboBox(arrayOf("JSON", "YAML", "Always Ask"))

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Default Output Format:", outputFormatCombo)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(settings: Settings?) {
        // Always read fresh from the binder — the passed-in `settings` is the
        // shared `Settings` umbrella, not an OpenApiSettings
        // instance. Mirrors CurlSettingsPanel.resetFrom.
        val s = project.settings<OpenApiSettings>()
        outputFormatCombo.selectedItem = toUiFormat(s.outputFormat)
    }

    override fun applyTo(settings: Settings) {
        // Persist via SettingBinder.update so the unified state component is
        // the single source of truth (mirrors CurlSettingsPanel.applyTo).
        SettingBinder.getInstance(project).update(OpenApiSettings::class) {
            outputFormat = fromUiFormat((outputFormatCombo.selectedItem as? String) ?: "Always Ask")
        }
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = project.settings<OpenApiSettings>()
        val uiFormat = (outputFormatCombo.selectedItem as? String) ?: "Always Ask"
        return uiFormat != toUiFormat(s.outputFormat)
    }

    // --- Test-visible accessors (mirror the CurlSettingsPanel internal-accessor pattern) ---

    internal fun outputFormatText(): String =
        (outputFormatCombo.selectedItem as? String) ?: "Always Ask"

    internal fun setOutputFormat(value: String) {
        outputFormatCombo.selectedItem = value
    }

    private fun toUiFormat(stored: String): String = when (stored.uppercase()) {
        "JSON" -> "JSON"
        "YAML" -> "YAML"
        "ALWAYS_ASK" -> "Always Ask"
        else -> "Always Ask"
    }

    private fun fromUiFormat(ui: String): String = when (ui) {
        "JSON" -> "JSON"
        "YAML" -> "YAML"
        "Always Ask" -> "ALWAYS_ASK"
        else -> "ALWAYS_ASK"
    }
}

package com.itangcent.easyapi.core.settings.ui

import com.intellij.openapi.project.Project

/**
 * General contract for components that can contribute a persistent settings
 * panel to the EasyApi settings dialog.
 *
 * Implemented by [com.itangcent.easyapi.channel.spi.Channel],
 * [com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer], and
 * [com.itangcent.easyapi.format.spi.FieldFormatChannel] so any of them can
 * optionally provide a dedicated settings tab.
 *
 * Panels are self-contained: they read/write their own modules via
 * [com.itangcent.easyapi.core.settings.SettingBinder] internally, so the
 * return type uses star projection ([SettingsPanel<*>?]).
 *
 * @see SettingsPanel
 */
interface SettingsPanelProvider {

    /**
     * Creates the persistent settings panel (the dedicated tab in Settings).
     * Return `null` if this provider has no settings UI.
     *
     * @param project the current IntelliJ project
     * @return the settings panel, or `null` if no configuration UI is needed
     */
    fun createSettingsPanel(project: Project): SettingsPanel<*>? = null
}

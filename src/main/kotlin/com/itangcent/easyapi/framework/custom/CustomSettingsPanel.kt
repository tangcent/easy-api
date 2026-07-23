package com.itangcent.easyapi.framework.custom

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.core.settings.ui.SettingsPanel
import com.itangcent.easyapi.core.settings.update
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings panel for the Custom framework.
 *
 * Self-contained: reads/writes the [CustomSettings] module via
 * [com.itangcent.easyapi.core.settings.SettingBinder] internally (mirroring the
 * [com.itangcent.easyapi.channel.hoppscotch.HoppscotchSettingsPanel] pattern),
 * so the [Settings] arg passed to [resetFrom] / [applyTo] / [isModified] is
 * ignored — the panel's own [Project] reference is used.
 *
 * Contributed to the EasyApi settings dialog via
 * [CustomApiRecognizer.createSettingsPanel] (the framework's
 * [com.itangcent.easyapi.core.settings.ui.SettingsPanelProvider] hook).
 */
class CustomSettingsPanel(private val project: Project) : SettingsPanel<Settings> {

    private val enableLineMarkerCheckbox = JBCheckBox(
        "Enable line marker on Custom API classes",
        false
    ).apply {
        toolTipText = buildString {
            append("Show a gutter icon on classes recognized by the Custom framework. ")
            append("Off by default — the rule-driven `custom.class.is.api` check is not cheap, ")
            append("so the line marker is opt-in to avoid surprising matches.")
        }
    }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addComponent(enableLineMarkerCheckbox)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(settings: Settings?) {
        // Settings arg ignored — read from SettingBinder directly (self-contained pattern).
        enableLineMarkerCheckbox.isSelected = project.settings<CustomSettings>().enableLineMarker
    }

    override fun applyTo(settings: Settings) {
        // Settings arg ignored — write to SettingBinder directly (self-contained pattern).
        SettingBinder.getInstance(project).update(CustomSettings::class) {
            enableLineMarker = enableLineMarkerCheckbox.isSelected
        }
    }

    override fun isModified(settings: Settings?): Boolean {
        // Settings arg ignored — read from SettingBinder directly (self-contained pattern).
        return enableLineMarkerCheckbox.isSelected != project.settings<CustomSettings>().enableLineMarker
    }
}

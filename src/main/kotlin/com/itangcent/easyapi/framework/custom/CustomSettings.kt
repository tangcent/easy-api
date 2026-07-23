package com.itangcent.easyapi.framework.custom

import com.itangcent.easyapi.core.settings.Scope
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.StorageScope

/**
 * Custom-framework-specific settings.
 *
 * All fields are APPLICATION scope, persisted via the unified
 * [com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState].
 *
 * @see CustomSettingsPanel for the settings-tab UI
 * @see CustomApiRecognizer.matchesClass for the line-marker gate
 */
data class CustomSettings(
    /**
     * Whether to expose a line-marker fast-path for Custom API classes.
     *
     * `false` by default — the rule-driven `custom.class.is.api` check is not
     * cheap, and surprising users with gutter icons on classes that just happen
     * to match a custom rule would be a regression. When the user opts in
     * (Settings → Custom → "Enable line marker"), [CustomApiRecognizer.matchesClass]
     * returns `true` and the line-marker provider can claim Custom API classes
     * without consulting the rule engine (per `ApiClassRecognizer.matchesClass`
     * contract).
     */
    @StorageScope(Scope.APPLICATION) var enableLineMarker: Boolean = false
) : Settings

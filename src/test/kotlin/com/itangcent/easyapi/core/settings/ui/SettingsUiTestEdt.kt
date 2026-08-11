package com.itangcent.easyapi.core.settings.ui

import javax.swing.SwingUtilities

internal fun runSettingsUiTestOnEdt(action: () -> Unit) {
    if (SwingUtilities.isEventDispatchThread()) {
        action()
    } else {
        SwingUtilities.invokeAndWait { action() }
    }
}

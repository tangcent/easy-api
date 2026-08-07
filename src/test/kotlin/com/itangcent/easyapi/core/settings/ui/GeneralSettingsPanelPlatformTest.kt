package com.itangcent.easyapi.core.settings.ui

import com.intellij.ui.components.JBCheckBox
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import java.awt.Component
import java.awt.Container
import javax.swing.JComponent
import javax.swing.border.TitledBorder

class GeneralSettingsPanelPlatformTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var panel: GeneralSettingsPanel

    override fun setUp() {
        super.setUp()
        runSettingsUiTestOnEdt {
            panel = GeneralSettingsPanel(project)
        }
    }

    fun testGeneralSectionsContainNotificationsButNoFeatureControls() {
        runSettingsUiTestOnEdt {
            val titles = titledSections(panel.component)
            assertTrue("Notifications section should be present", titles.contains("Notifications"))
            assertTrue("Output section should remain present", titles.contains("Output"))
            assertTrue("Diagnostics section should remain present", titles.contains("Diagnostics"))
            assertTrue("Cache Management section should remain present", titles.contains("Cache Management"))
            assertTrue("Repositories section should remain present", titles.contains("Repositories"))
            assertFalse("Scanning should no longer be a General section", titles.contains("Scanning"))
            assertFalse("Editor should no longer be a General section", titles.contains("Editor"))

            val notificationPanel = findTitledSection(panel.component, "Notifications")
            val checkboxes = descendants(notificationPanel).filterIsInstance<JBCheckBox>().toList()
            assertEquals("Notifications should contain only switchNotice", 1, checkboxes.size)
            assertEquals("Show notification on settings switch", checkboxes.single().text)
        }
    }

    fun testApplyUpdatesGeneralFieldsWithoutWritingFeatureBooleans() {
        val target = GeneralSettings(
            apiScanEnabled = false,
            autoScanEnabled = true,
            concurrentScanEnabled = true,
            gutterIconEnabled = false
        )
        runSettingsUiTestOnEdt {
            panel.resetFrom(
                GeneralSettings(
                    apiScanEnabled = true,
                    autoScanEnabled = false,
                    concurrentScanEnabled = false,
                    gutterIconEnabled = true,
                    switchNotice = false,
                    logLevel = 40,
                    outputCharset = "GBK"
                )
            )
            panel.applyTo(target)
        }

        assertFalse("API scanning should remain untouched", target.apiScanEnabled)
        assertTrue("Automatic scanning should remain untouched", target.autoScanEnabled)
        assertTrue("Concurrent scanning should remain untouched", target.concurrentScanEnabled)
        assertFalse("Editor integration should remain untouched", target.gutterIconEnabled)
        assertFalse("switchNotice should still use its existing field", target.switchNotice)
        assertEquals(40, target.logLevel)
        assertEquals("GBK", target.outputCharset)
    }

    fun testIsModifiedIgnoresFeatureBooleanDifferences() {
        val displayed = GeneralSettings()
        val onlyFeaturesDiffer = displayed.copy(
            apiScanEnabled = false,
            autoScanEnabled = false,
            concurrentScanEnabled = true,
            gutterIconEnabled = false
        )
        runSettingsUiTestOnEdt {
            panel.resetFrom(displayed)
            assertFalse(
                "General modification checks should ignore feature booleans",
                panel.isModified(onlyFeaturesDiffer)
            )
        }
    }

    private fun titledSections(root: Component): List<String> = descendants(root).mapNotNull { component ->
        ((component as? JComponent)?.border as? TitledBorder)?.title
    }.toList()

    private fun findTitledSection(root: Component, title: String): JComponent =
        descendants(root).filterIsInstance<JComponent>().first { component ->
            (component.border as? TitledBorder)?.title == title
        }

    private fun descendants(root: Component): Sequence<Component> = sequence {
        yield(root)
        if (root is Container) {
            root.components.forEach { child -> yieldAll(descendants(child)) }
        }
    }
}

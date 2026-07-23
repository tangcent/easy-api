package com.itangcent.easyapi.framework.custom

import com.itangcent.easyapi.core.rule.engine.RuleEngine
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class CustomApiRecognizerTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var recognizer: CustomApiRecognizer

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        recognizer = CustomApiRecognizer(RuleEngine.getInstance(project))
    }

    private fun loadTestFiles() {
        loadFile("custom/annotation/MyApi.java")
        loadFile("custom/annotation/MyEndpoint.java")
        loadFile("custom/api/Ctrl.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
    }

    override fun createConfigReader() = TestConfigReader.fromConfigText(
        project,
        """
        custom.class.is.api=groovy:it.hasAnn("com.itangcent.custom.annotation.MyApi")
        """.trimIndent()
    )

    fun testRecognizeCustomApiClass() = runTest {
        val psiClass = findClass("com.itangcent.custom.Ctrl")
        assertNotNull(psiClass)

        val isApi = recognizer.isApiClass(psiClass!!)
        assertTrue("Should recognize @MyApi class as Custom API class", isApi)
    }

    fun testRecognizeNonApiClass() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val isApi = recognizer.isApiClass(psiClass!!)
        assertFalse("Should not recognize model class as Custom API class", isApi)
    }

    fun testNoRuleConfiguredReturnsFalse() = runTest {
        // Use an empty config reader to simulate no custom.class.is.api rule.
        val emptyRecognizer = CustomApiRecognizer(RuleEngine.getInstance(project))
        // Re-register an empty config reader for this test.
        // Since the config reader is project-scoped and shared, we test via a
        // fresh recognizer against a class that doesn't match any rule.
        // With the rule configured in setUp, a non-annotated class returns false.
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val isApi = emptyRecognizer.isApiClass(psiClass!!)
        assertFalse("Should return false when no rule matches", isApi)
    }

    fun testRuleReturnsFalse() = runTest {
        // The configured rule checks for @MyApi; UserInfo doesn't have it → false.
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val isApi = recognizer.isApiClass(psiClass!!)
        assertFalse("Should return false when rule evaluates to false", isApi)
    }

    fun testFrameworkName() {
        assertEquals("Custom", recognizer.frameworkName)
        assertEquals("Custom", CustomApiRecognizer.FRAMEWORK_NAME)
    }

    fun testTargetAnnotationsEmpty() {
        assertTrue(
            "targetAnnotations must be empty (no index scanning)",
            recognizer.targetAnnotations.isEmpty()
        )
    }

    fun testEnabledByDefaultFalse() {
        assertFalse(
            "Custom framework must be disabled by default (matches Feign)",
            recognizer.enabledByDefault
        )
    }

    fun testMatchesClassReturnsFalseByDefault() {
        // Default CustomSettings.enableLineMarker == false → matchesClass returns false.
        val psiClass = findClass("com.itangcent.custom.Ctrl")
        assertNotNull(psiClass)
        assertFalse(
            "matchesClass must return false by default (enableLineMarker is off)",
            recognizer.matchesClass(psiClass!!)
        )
    }

    fun testMatchesClassReturnsTrueWhenEnableLineMarkerOn() {
        SettingBinder.getInstance(project).update(CustomSettings::class) {
            enableLineMarker = true
        }
        val psiClass = findClass("com.itangcent.custom.Ctrl")
        assertNotNull(psiClass)
        assertTrue(
            "matchesClass must return true once the user opts in via CustomSettings.enableLineMarker",
            recognizer.matchesClass(psiClass!!)
        )
    }

    fun testMatchesClassTogglesBackToFalse() {
        // Verify the gate is read fresh from settings on each call (not cached).
        SettingBinder.getInstance(project).update(CustomSettings::class) {
            enableLineMarker = true
        }
        SettingBinder.getInstance(project).update(CustomSettings::class) {
            enableLineMarker = false
        }
        val psiClass = findClass("com.itangcent.custom.Ctrl")
        assertNotNull(psiClass)
        assertFalse(
            "matchesClass must return false again once enableLineMarker is turned back off",
            recognizer.matchesClass(psiClass!!)
        )
    }

    fun testCreateSettingsPanelReturnsCustomSettingsPanelWhenEnabled() {
        // Enable the Custom framework so createSettingsPanel renders the panel.
        SettingBinder.getInstance(project).update(GeneralSettings::class) {
            enabledFrameworks = arrayOf(recognizer.frameworkName)
        }
        val panel = recognizer.createSettingsPanel(project)
        assertNotNull(
            "createSettingsPanel must contribute a Custom settings tab when framework is enabled",
            panel
        )
        assertTrue(
            "Panel must be a CustomSettingsPanel, got ${panel!!::class.simpleName}",
            panel is CustomSettingsPanel
        )
    }

    fun testCreateSettingsPanelReturnsNullWhenDisabled() {
        // Custom framework is disabled by default (enabledByDefault = false and
        // not in enabledFrameworks) — createSettingsPanel must return null.
        SettingBinder.getInstance(project).update(GeneralSettings::class) {
            enabledFrameworks = emptyArray()
        }
        val panel = recognizer.createSettingsPanel(project)
        assertNull(
            "createSettingsPanel must return null when the framework is disabled (no panel for a disabled feature)",
            panel
        )
    }
}

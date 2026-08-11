package com.itangcent.easyapi.core.settings.ui

import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.core.feature.CoreFeatureIds
import com.itangcent.easyapi.core.feature.FeatureStateChange
import com.itangcent.easyapi.core.feature.FeatureStateChangeSource
import com.itangcent.easyapi.core.feature.FeatureStateEvents
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlin.reflect.KClass

class EasyApiSettingsConfigurableTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var originalBinder: SettingBinder
    private lateinit var originalGeneral: GeneralSettings
    private lateinit var recordingBinder: RecordingSettingBinder
    private lateinit var configurable: EasyApiSettingsConfigurable

    override fun setUp() {
        super.setUp()
        originalBinder = SettingBinder.getInstance(project)
        originalGeneral = copyGeneral(originalBinder.read(GeneralSettings::class))
        recordingBinder = RecordingSettingBinder(originalBinder, GeneralSettings())
        project.registerServiceInstance(SettingBinder::class.java, recordingBinder)
        configurable = EasyApiSettingsConfigurable(project)
    }

    override fun tearDown() {
        try {
            originalBinder.save(copyGeneral(originalGeneral))
            project.registerServiceInstance(SettingBinder::class.java, originalBinder)
        } finally {
            super.tearDown()
        }
    }

    fun testMainAndDynamicSettingsTabsRemainAvailable() {
        runSettingsUiTestOnEdt { configurable.createComponent() }
        val tabs = configurable.tabsForTest()

        assertEquals("General", tabs.first())
        assertEquals("Features", tabs[1])
        assertTrue("HTTP tab should remain available", tabs.contains("HTTP"))
        assertTrue("Postman dynamic settings tab should remain available", tabs.contains("Postman"))
        assertTrue("AI tab should remain available", tabs.contains("AI"))
        assertFalse("Rules remains a child configurable", tabs.contains("Rules"))
    }

    fun testApplySavesGeneralOnceAndPublishesOneTypedChange() {
        val changes = captureFeatureChanges()
        runSettingsUiTestOnEdt {
            configurable.createComponent()
            configurable.setFeatureDesiredStateForTest(CoreFeatureIds.API_SCANNING, false)
        }
        recordingBinder.generalSaveCount = 0

        runSettingsUiTestOnEdt { configurable.apply() }

        assertEquals("GeneralSettings should be saved exactly once", 1, recordingBinder.generalSaveCount)
        assertFalse("The feature draft should be persisted", recordingBinder.persistedGeneral().apiScanEnabled)
        assertEquals("One typed change should be published", 1, changes.size)
        assertEquals(FeatureStateChangeSource.SETTINGS_APPLY, changes.single().source)
        assertTrue(
            "The change should contain the scanning feature",
            changes.single().entries.any { it.id == CoreFeatureIds.API_SCANNING }
        )
        assertFalse(
            "A successful apply should replace the modified feature transaction",
            configurable.featureTransactionModifiedForTest()
        )
    }

    fun testDisposeDiscardsDraftWithoutSavingOrPublishing() {
        val changes = captureFeatureChanges()
        runSettingsUiTestOnEdt {
            configurable.createComponent()
            configurable.setFeatureDesiredStateForTest(CoreFeatureIds.API_SCANNING, false)
        }
        recordingBinder.generalSaveCount = 0

        runSettingsUiTestOnEdt { configurable.disposeUIResources() }

        assertEquals("Cancel should not save GeneralSettings", 0, recordingBinder.generalSaveCount)
        assertTrue("Cancel should leave persisted scanning enabled", recordingBinder.persistedGeneral().apiScanEnabled)
        assertTrue("Cancel should not publish a feature change", changes.isEmpty())
    }

    fun testFailedGeneralSaveDoesNotPublish() {
        val changes = captureFeatureChanges()
        runSettingsUiTestOnEdt {
            configurable.createComponent()
            configurable.setFeatureDesiredStateForTest(CoreFeatureIds.API_SCANNING, false)
        }
        recordingBinder.generalSaveCount = 0
        recordingBinder.failGeneralSave = true

        var failure: Throwable? = null
        try {
            runSettingsUiTestOnEdt { configurable.apply() }
        } catch (throwable: Throwable) {
            failure = throwable
        }

        assertNotNull("The configured GeneralSettings save should fail", failure)
        assertEquals(1, recordingBinder.generalSaveCount)
        assertTrue("A failed save should not replace persisted state", recordingBinder.persistedGeneral().apiScanEnabled)
        assertTrue("A failed save should not publish a feature change", changes.isEmpty())
    }

    fun testApplyPreservesUnknownLegacyArrayEntries() {
        recordingBinder.replaceGeneral(
            GeneralSettings(
                enabledChannels = arrayOf("unknown-enabled"),
                disabledChannels = arrayOf("unknown-disabled")
            )
        )
        runSettingsUiTestOnEdt {
            configurable.createComponent()
            configurable.setChannelCheckboxForTest("markdown", false)
            configurable.apply()
        }

        val persisted = recordingBinder.persistedGeneral()
        assertEquals(listOf("unknown-enabled"), persisted.enabledChannels.toList())
        assertEquals(listOf("unknown-disabled", "markdown"), persisted.disabledChannels.toList())
    }

    private fun captureFeatureChanges(): MutableList<FeatureStateChange> {
        val changes = mutableListOf<FeatureStateChange>()
        project.messageBus.connect(testRootDisposable).subscribe(
            FeatureStateEvents.TOPIC,
            FeatureStateEvents { change -> changes += change }
        )
        return changes
    }

    private class RecordingSettingBinder(
        private val delegate: SettingBinder,
        initialGeneral: GeneralSettings
    ) : SettingBinder {
        private var general = copyGeneral(initialGeneral)
        var generalSaveCount: Int = 0
        var failGeneralSave: Boolean = false

        @Suppress("UNCHECKED_CAST")
        override fun <T : Settings> read(type: KClass<T>): T =
            if (type == GeneralSettings::class) copyGeneral(general) as T else delegate.read(type)

        override fun <T : Settings> save(settings: T) {
            if (settings is GeneralSettings) {
                generalSaveCount++
                if (failGeneralSave) {
                    throw IllegalStateException("GeneralSettings save failed")
                }
                general = copyGeneral(settings)
                delegate.save(copyGeneral(settings))
            } else {
                delegate.save(settings)
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Settings> tryRead(type: KClass<T>): T? =
            if (type == GeneralSettings::class) copyGeneral(general) as T else delegate.tryRead(type)

        fun replaceGeneral(settings: GeneralSettings) {
            general = copyGeneral(settings)
        }

        fun persistedGeneral(): GeneralSettings = copyGeneral(general)
    }

    companion object {
        private fun copyGeneral(settings: GeneralSettings): GeneralSettings = settings.copy(
            enabledChannels = settings.enabledChannels.copyOf(),
            disabledChannels = settings.disabledChannels.copyOf(),
            enabledFieldFormatChannels = settings.enabledFieldFormatChannels.copyOf(),
            disabledFieldFormatChannels = settings.disabledFieldFormatChannels.copyOf(),
            enabledFrameworks = settings.enabledFrameworks.copyOf(),
            disabledFrameworks = settings.disabledFrameworks.copyOf()
        )
    }
}

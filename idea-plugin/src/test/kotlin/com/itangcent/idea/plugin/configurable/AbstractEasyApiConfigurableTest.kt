package com.itangcent.idea.plugin.configurable

import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.xml.ApplicationSettingsSupport
import com.itangcent.idea.plugin.settings.xml.ProjectSettingsSupport
import com.itangcent.intellij.context.ActionContext
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.utils.WaitHelper
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import javax.swing.JComponent
import kotlin.test.*

/**
 * Test case of [AbstractEasyApiConfigurableTest]
 */
internal class AbstractEasyApiConfigurableTest {

    @Test
    fun testAbstractEasyApiConfigurable() {
        val easyApiConfigurable = FakeEasyApiConfigurable()
        try {
            assertNotNull(easyApiConfigurable.createComponent())
            val easyApiSettingGUI = easyApiConfigurable.getGUI()

            easyApiSettingGUI.waitInit()
            assertFalse(easyApiConfigurable.isModified)
            assertTrue(easyApiSettingGUI.onCreateCalled())

            easyApiSettingGUI.updateSettings { it.postmanToken = "PMAK-6874651effa99211247d3a19-7612h98a1de283e67961119c8e592a1289" }
            assertTrue(easyApiConfigurable.isModified)
            assertEquals("PMAK-6874651effa99211247d3a19-7612h98a1de283e67961119c8e592a1289", easyApiSettingGUI.getSettings().postmanToken)
            assertNull(easyApiConfigurable.getSettings().postmanToken)

            easyApiConfigurable.reset()
            assertFalse(easyApiConfigurable.isModified)
            assertNull(easyApiSettingGUI.getSettings().postmanToken)
            assertNull(easyApiConfigurable.getSettings().postmanToken)

            easyApiSettingGUI.updateSettings { it.postmanToken = "PMAK-6874651effa99211247d3a19-7612h98a1de283e67961119c8e592a1289" }
            assertTrue(easyApiConfigurable.isModified)
            assertEquals("PMAK-6874651effa99211247d3a19-7612h98a1de283e67961119c8e592a1289", easyApiSettingGUI.getSettings().postmanToken)
            assertNull(easyApiConfigurable.getSettings().postmanToken)

            easyApiConfigurable.apply()
            assertFalse(easyApiConfigurable.isModified)
            assertEquals("PMAK-6874651effa99211247d3a19-7612h98a1de283e67961119c8e592a1289", easyApiSettingGUI.getSettings().postmanToken)
            assertEquals("PMAK-6874651effa99211247d3a19-7612h98a1de283e67961119c8e592a1289", easyApiConfigurable.getSettings().postmanToken)
        } finally {
            easyApiConfigurable.disposeUIResources()
        }
    }
}

class FakeEasyApiConfigurable : AbstractEasyApiConfigurable(mock()) {
    private val setting: Settings = Settings()

    private val fakeEasyApiSettingGUI = FakeEasyApiSettingGUI()

    fun getGUI(): FakeEasyApiSettingGUI {
        return fakeEasyApiSettingGUI
    }

    fun getSettings(): Settings {
        return setting
    }

    override fun createGUI(): EasyApiSettingGUI {
        return fakeEasyApiSettingGUI
    }

    override fun getDisplayName(): String {
        return "FakeConfigurable"
    }

    override fun getId(): String {
        return "easyapi.FakeConfigurable"
    }

    override fun afterBuildActionContext(builder: ActionContext.ActionContextBuilder) {
        super.afterBuildActionContext(builder)
        builder.bind(SettingBinder::class) { it.toInstance(SettingBinderAdaptor(setting)) }
    }
}

class FakeEasyApiSettingGUI : AbstractEasyApiSettingGUI() {

    private var onCreateCalled = false

    fun updateSettings(action: (Settings) -> Unit) {
        this.settingsInstance?.let { action(it) }
    }

    override fun readSettings(settings: Settings, from: Settings) {
        from.copyTo(settings as ProjectSettingsSupport)
        from.copyTo(settings as ApplicationSettingsSupport)
    }

    override fun getRootPanel(): JComponent {
        return mock()
    }

    fun waitInit() {
        WaitHelper.waitUtil(10000) {
            this.settingsInstance != null
        }
    }

    override fun onCreate() {
        this.onCreateCalled = true
    }

    fun onCreateCalled(): Boolean {
        return this.onCreateCalled
    }
}
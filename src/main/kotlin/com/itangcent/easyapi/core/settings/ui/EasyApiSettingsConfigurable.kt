package com.itangcent.easyapi.core.settings.ui

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.itangcent.easyapi.channel.spi.ChannelRegistry
import com.itangcent.easyapi.core.export.recognizer.CompositeApiClassRecognizer
import com.itangcent.easyapi.core.feature.FeatureId
import com.itangcent.easyapi.core.feature.FeatureRegistry
import com.itangcent.easyapi.core.feature.FeatureSettingsTransaction
import com.itangcent.easyapi.core.feature.publishFeatureStateChange
import com.itangcent.easyapi.core.ide.action.ChannelQuickActionGroup
import com.itangcent.easyapi.format.spi.FieldFormatActionGroup
import com.itangcent.easyapi.format.spi.FieldFormatChannelRegistry
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.module.AiSettings
import com.itangcent.easyapi.core.settings.module.EnvironmentSettings
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.module.HttpSettings
import com.itangcent.easyapi.framework.grpc.GrpcSettings
import com.itangcent.easyapi.core.settings.module.ParsingOutputSettings
import com.itangcent.easyapi.core.settings.module.RuleFileSettings
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTabbedPane
import kotlin.reflect.KClass

class EasyApiSettingsConfigurable(private val project: com.intellij.openapi.project.Project) : Configurable {
    private var panel: JPanel? = null
    private var tabs: JTabbedPane? = null

    private val generalPanel = GeneralSettingsPanel(project)
    private val featureSnapshot = FeatureRegistry.getInstance(project).snapshot()
    private val featuresPanel = FeatureSettingsPanel(featureSnapshot)
    private var featureTransaction: FeatureSettingsTransaction? = null
    private val httpPanel = HttpSettingsPanel()
    private val parsingOutputPanel = ParsingOutputSettingsPanel()
    private val extensionPanel = ExtensionConfigPanel()
    private val aiPanel = AiSettingsPanel()
    private val environmentPanel = EnvironmentSettingsPanel(project)

    // Channel panels (including Postman) are dynamically contributed via the
    // Channel EP. Each panel is paired with the [Channel.settingsType] it owns
    // so apply/reset/isModified can read and persist the correct module via
    // [SettingBinder]. Panels whose channel declares no settingsType are treated
    // as self-contained (their applyTo/resetFrom are no-ops).
    private val channelPanels = mutableListOf<ChannelPanelEntry>()

    // Framework panels are dynamically contributed via the apiClassRecognizer EP
    // (each [ApiClassRecognizer] that returns a non-null [SettingsPanelProvider.createSettingsPanel]
    // gets a tab). Framework panels are self-contained — they read/write their
    // own modules via [SettingBinder] internally (mirroring the Hoppscotch panel
    // pattern), so the [Channel.settingsType] hint is not needed here.
    private val frameworkPanels = mutableListOf<SettingsPanel<Settings>>()

    // Field-format panels are dynamically contributed via the fieldFormatChannel EP
    // (each [FieldFormatChannel] that returns a non-null [SettingsPanelProvider.createSettingsPanel]
    // gets a tab). Same self-contained pattern as framework panels.
    private val formatPanels = mutableListOf<SettingsPanel<Settings>>()

    /** No-op module for self-contained panels whose applyTo is a no-op. */
    private val noopModule = object : Settings {}

    /** A channel settings panel paired with its owning [Channel.settingsType]. */
    private data class ChannelPanelEntry(
        val panel: SettingsPanel<Settings>,
        val settingsType: KClass<out Settings>?
    )

    companion object {
        private var initialTab: String? = null

        fun selectTab(tabName: String) {
            initialTab = tabName
        }

        const val TAB_GENERAL = "General"
        const val TAB_FEATURES = "Features"
        const val TAB_POSTMAN = "Postman"
        const val TAB_HTTP = "HTTP"
        const val TAB_PARSING_OUTPUT = "Parsing & Output"
        const val TAB_EXTENSIONS = "Extensions"
        const val TAB_RULES = "Rules"
        const val TAB_AI = "AI"
        const val TAB_GRPC = "GRPC"
        const val TAB_ENVIRONMENT = "Environments"
    }

    /**
     * Returns the display name for the settings dialog.
     */
    override fun getDisplayName(): String = "EasyApi"

    /**
     * Creates the settings UI component with tabbed panels.
     */
    override fun createComponent(): JComponent {
        if (panel == null) {
            panel = JPanel(BorderLayout())
            channelPanels.clear()
            frameworkPanels.clear()
            formatPanels.clear()
            tabs = JTabbedPane().also { t ->
                t.addTab(TAB_GENERAL, wrapNorth(generalPanel.component))
                t.addTab(TAB_FEATURES, wrapNorth(featuresPanel.component))
                t.addTab(TAB_HTTP, wrapNorth(httpPanel.component))
                t.addTab(TAB_PARSING_OUTPUT, wrapNorth(parsingOutputPanel.component))
                t.addTab(TAB_EXTENSIONS, extensionPanel.component)
                t.addTab(TAB_AI, wrapNorth(aiPanel.component))
                t.addTab(TAB_ENVIRONMENT, environmentPanel.component)

                // Dynamically add a tab for each registered channel (sorted by settingsTabOrder).
                // Postman (settingsTabOrder=20) appears before the default (100).
                ChannelRegistry.getInstance(project).channelsForSettings().forEach { channel ->
                    channel.createSettingsPanel(project)?.let { pnl ->
                        val name = channel.id.replaceFirstChar { it.uppercase() }
                        t.addTab(name, wrapNorth(pnl.component))
                        @Suppress("UNCHECKED_CAST")
                        channelPanels.add(
                            ChannelPanelEntry(pnl as SettingsPanel<Settings>, channel.settingsType)
                        )
                    }
                }

                // Dynamically add a tab for each framework recognizer that
                // contributes a settings panel (e.g. the Custom framework's
                // `enableLineMarker` toggle). Framework panels are self-contained.
                CompositeApiClassRecognizer.getInstance(project).allRecognizers().forEach { recognizer ->
                    recognizer.createSettingsPanel(project)?.let { pnl ->
                        val name = recognizer.frameworkName.replaceFirstChar { it.uppercase() }
                        t.addTab(name, wrapNorth(pnl.component))
                        @Suppress("UNCHECKED_CAST")
                        frameworkPanels.add(pnl as SettingsPanel<Settings>)
                    }
                }

                // Dynamically add a tab for each field-format channel that
                // contributes a settings panel. Same self-contained pattern.
                FieldFormatChannelRegistry.getInstance(project).allChannels().forEach { format ->
                    format.createSettingsPanel(project)?.let { pnl ->
                        val name = format.id.replaceFirstChar { it.uppercase() }
                        t.addTab(name, wrapNorth(pnl.component))
                        @Suppress("UNCHECKED_CAST")
                        formatPanels.add(pnl as SettingsPanel<Settings>)
                    }
                }
            }
            panel!!.add(tabs, BorderLayout.CENTER)
        }
        reset()
        selectInitialTab()
        return panel!!
    }

    private fun selectInitialTab() {
        val tabName = initialTab
        if (tabName != null && tabs != null) {
            for (i in 0 until tabs!!.tabCount) {
                if (tabs!!.getTitleAt(i) == tabName) {
                    tabs!!.selectedIndex = i
                    break
                }
            }
            initialTab = null
        }
    }

    /**
     * Wraps a form panel so it stays at the top-left and doesn't stretch
     * across very wide windows.
     */
    private fun wrapNorth(component: JComponent): JComponent {
        component.maximumSize = java.awt.Dimension(600, component.maximumSize.height)
        val row = javax.swing.Box.createHorizontalBox().apply {
            add(component)
            add(javax.swing.Box.createHorizontalGlue())
        }
        return JPanel(BorderLayout()).apply {
            add(row, BorderLayout.NORTH)
        }
    }

    /**
     * Checks if any settings have been modified.
     *
     * Each module-typed panel is checked against its own module via
     * [SettingBinder]. Mixed-scope panels also check their
     * cross-module fields.
     */
    override fun isModified(): Boolean {
        val binder = SettingBinder.getInstance(project)
        val general = binder.read(GeneralSettings::class)
        val grpc = binder.read(GrpcSettings::class)
        val parsingOutput = binder.read(ParsingOutputSettings::class)
        val environment = binder.read(EnvironmentSettings::class)

        return generalPanel.isModified(general) ||
            (featureTransaction?.isModified() == true) ||
            generalPanel.isRepositoriesModified(grpc) ||
            httpPanel.isModified(binder.read(HttpSettings::class)) ||
            parsingOutputPanel.isModified(parsingOutput) ||
            extensionPanel.isModified(binder.read(RuleFileSettings::class)) ||
            aiPanel.isModified(binder.read(AiSettings::class)) ||
            environmentPanel.isModified(environment) ||
            channelPanels.any { entry -> isChannelModified(binder, entry) } ||
            frameworkPanels.any { it.isModified(null) } ||
            formatPanels.any { it.isModified(null) }
    }

    /**
     * Applies all changes from the UI panels to settings.
     *
     * Each module-typed panel applies to its own module via
     * [SettingBinder]. Mixed-scope panels also apply their
     * cross-module fields. All modified modules are then persisted.
     */
    override fun apply() {
        val binder = SettingBinder.getInstance(project)

        val general = binder.read(GeneralSettings::class)
        generalPanel.applyTo(general)
        val activeFeatureTransaction = featureTransaction
            ?: FeatureSettingsTransaction(featureSnapshot, general).also {
                featureTransaction = it
                featuresPanel.bindTransaction(it)
            }
        val featureChange = activeFeatureTransaction.commit(general)

        val grpc = binder.read(GrpcSettings::class)
        generalPanel.applyRepositoriesTo(grpc)

        val parsingOutput = binder.read(ParsingOutputSettings::class)
        parsingOutputPanel.applyTo(parsingOutput)

        val environment = binder.read(EnvironmentSettings::class)
        environmentPanel.applyTo(environment)

        val http = binder.read(HttpSettings::class)
        httpPanel.applyTo(http)

        val ruleFile = binder.read(RuleFileSettings::class)
        extensionPanel.applyTo(ruleFile)

        val ai = binder.read(AiSettings::class)
        aiPanel.applyTo(ai)

        // Dynamic panels retain their existing module-specific save behavior.
        channelPanels.forEach { entry -> applyChannel(binder, entry) }
        frameworkPanels.forEach { it.applyTo(noopModule) }
        formatPanels.forEach { it.applyTo(noopModule) }

        binder.save(grpc)
        binder.save(parsingOutput)
        binder.save(environment)
        binder.save(http)
        binder.save(ruleFile)
        binder.save(ai)
        binder.save(general)

        FeatureSettingsTransaction(featureSnapshot, general).also {
            featureTransaction = it
            featuresPanel.bindTransaction(it)
        }
        featureChange?.let(project::publishFeatureStateChange)

        // Refresh quick actions after the persisted feature state is visible.
        ChannelQuickActionGroup.refreshActions(project)
        FieldFormatActionGroup.refreshActions(project)
    }

    /**
     * Reads the channel's typed module, applies the panel's UI state to it,
     * and persists it. Channels without a [Channel.settingsType] are treated
     * as self-contained (their applyTo is a no-op) and receive [noopModule].
     */
    @Suppress("UNCHECKED_CAST")
    private fun applyChannel(binder: SettingBinder, entry: ChannelPanelEntry) {
        val type = entry.settingsType
        if (type == null) {
            entry.panel.applyTo(noopModule)
        } else {
            val module = binder.read(type as KClass<Settings>)
            entry.panel.applyTo(module)
            binder.save(module)
        }
    }

    /**
     * Checks whether a channel panel's UI state differs from its persisted module.
     * Channels without a [Channel.settingsType] are self-contained (no state).
     */
    @Suppress("UNCHECKED_CAST")
    private fun isChannelModified(binder: SettingBinder, entry: ChannelPanelEntry): Boolean {
        val type = entry.settingsType ?: return entry.panel.isModified(null)
        return entry.panel.isModified(binder.read(type as KClass<Settings>))
    }

    /**
     * Resets all UI panels to the current settings values.
     *
     * Each module-typed panel resets from its own module via
     * [SettingBinder]. Mixed-scope panels also reset their
     * cross-module fields.
     */
    override fun reset() {
        val binder = SettingBinder.getInstance(project)

        val general = binder.read(GeneralSettings::class)
        generalPanel.resetFrom(general)
        FeatureSettingsTransaction(featureSnapshot, general).also {
            featureTransaction = it
            featuresPanel.bindTransaction(it)
        }
        generalPanel.resetRepositoriesFrom(binder.read(GrpcSettings::class))

        val parsingOutput = binder.read(ParsingOutputSettings::class)
        parsingOutputPanel.resetFrom(parsingOutput)

        val environment = binder.read(EnvironmentSettings::class)
        environmentPanel.resetFrom(environment)

        httpPanel.resetFrom(binder.read(HttpSettings::class))
        extensionPanel.resetFrom(binder.read(RuleFileSettings::class))
        aiPanel.resetFrom(binder.read(AiSettings::class))

        @Suppress("UNCHECKED_CAST")
        channelPanels.forEach { entry ->
            val type = entry.settingsType
            if (type == null) {
                entry.panel.resetFrom(null)
            } else {
                entry.panel.resetFrom(binder.read(type as KClass<Settings>))
            }
        }
        // Framework and format panels: self-contained — they read their own
        // state via SettingBinder internally (resetFrom(null) is the trigger).
        frameworkPanels.forEach { it.resetFrom(null) }
        formatPanels.forEach { it.resetFrom(null) }
    }

    override fun disposeUIResources() {
        featureTransaction = null
        panel = null
        tabs = null
    }

    /** Test-only: returns the titles of all tabs in the settings dialog. */
    internal fun tabsForTest(): List<String> {
        val t = tabs ?: return emptyList()
        return (0 until t.tabCount).map { t.getTitleAt(it) }
    }

    internal fun featureDesiredStateForTest(id: FeatureId): Boolean? =
        featuresPanel.desiredStateForTest(id)

    internal fun setFeatureDesiredStateForTest(id: FeatureId, selected: Boolean) {
        featuresPanel.setDesiredStateForTest(id, selected)
    }

    internal fun featureControlEnabledForTest(id: FeatureId): Boolean? =
        featuresPanel.isControlEnabledForTest(id)

    internal fun featureDependencyTextForTest(id: FeatureId): String? =
        featuresPanel.dependencyTextForTest(id)

    internal fun featureTransactionModifiedForTest(): Boolean =
        featureTransaction?.isModified() == true

    /** Test-only compatibility seam for export-channel feature ids. */
    internal fun channelCheckboxStateForTest(channelId: String): Boolean? =
        featureDesiredStateForTest(FeatureId("channel/$channelId"))

    /** Test-only compatibility seam for export-channel feature ids. */
    internal fun setChannelCheckboxForTest(channelId: String, selected: Boolean) {
        setFeatureDesiredStateForTest(FeatureId("channel/$channelId"), selected)
    }

    /** Test-only compatibility seam for field-format feature ids. */
    internal fun fieldFormatCheckboxStateForTest(channelId: String): Boolean? =
        featureDesiredStateForTest(FeatureId("field-format/$channelId"))

    /** Test-only compatibility seam for field-format feature ids. */
    internal fun setFieldFormatCheckboxForTest(channelId: String, selected: Boolean) {
        setFeatureDesiredStateForTest(FeatureId("field-format/$channelId"), selected)
    }
}

abstract class BaseEasyApiChildConfigurable<T : Settings>(
    private val displayName: String,
    private val panelFactory: () -> SettingsPanel<T>
) : Configurable {
    private var panelContainer: JPanel? = null
    protected val panel: SettingsPanel<T> by lazy { panelFactory() }

    protected var project: com.intellij.openapi.project.Project? = null

    protected val modularBinder: SettingBinder? by lazy {
        project?.let { SettingBinder.getInstance(it) }
            ?: ProjectManager.getInstance().openProjects.firstOrNull()?.let { SettingBinder.getInstance(it) }
    }

    /** Reads the current settings for the panel's module type, or null if unavailable. */
    protected abstract fun readSettings(): T?

    /** Persists the given settings for the panel's module type. */
    protected abstract fun saveSettings(settings: T)

    override fun getDisplayName(): String = displayName

    override fun createComponent(): JComponent {
        if (panelContainer == null) {
            panelContainer = JPanel(BorderLayout())
            panelContainer!!.add(panel.component, BorderLayout.CENTER)
        }
        reset()
        return panelContainer!!
    }

    override fun isModified(): Boolean {
        val settings = readSettings() ?: return false
        return panel.isModified(settings)
    }

    override fun apply() {
        val settings = readSettings() ?: return
        panel.applyTo(settings)
        saveSettings(settings)
    }

    override fun reset() {
        panel.resetFrom(readSettings())
    }

    override fun disposeUIResources() {
        panelContainer = null
    }
}

class EasyApiRulesConfigurable(project: com.intellij.openapi.project.Project) :
    BaseEasyApiChildConfigurable<RuleFileSettings>("Rules", { RulesTabPanel(project) }) {
    init { this.project = project }

    private val rulesTabPanel: RulesTabPanel get() = panel as RulesTabPanel

    override fun readSettings(): RuleFileSettings? = modularBinder?.read(RuleFileSettings::class)
    override fun saveSettings(settings: RuleFileSettings) { modularBinder?.save(settings) }

    override fun reset() {
        super.reset()
        rulesTabPanel.resetAutoRuleFilesFrom(modularBinder?.read(EnvironmentSettings::class))
    }

    override fun apply() {
        super.apply()
        val envSettings = modularBinder?.read(EnvironmentSettings::class) ?: return
        rulesTabPanel.applyAutoRuleFilesTo(envSettings)
        modularBinder?.save(envSettings)
    }

    override fun isModified(): Boolean {
        if (super.isModified()) return true
        return rulesTabPanel.isAutoRuleFilesModified(modularBinder?.read(EnvironmentSettings::class))
    }
}

class EasyApiBackupConfigurable(project: com.intellij.openapi.project.Project) :
    BaseEasyApiChildConfigurable<Settings>("Backup", { BackupSettingsPanel(project) }) {
    init { this.project = project }
    override fun readSettings(): Settings? = object : Settings {}
    override fun saveSettings(settings: Settings) { /* no-op: self-contained panel */ }
}

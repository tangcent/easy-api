package com.itangcent.easyapi.core.settings.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.itangcent.easyapi.core.feature.DependencyCycle
import com.itangcent.easyapi.core.feature.DisabledByDependency
import com.itangcent.easyapi.core.feature.FeatureDisabledReason
import com.itangcent.easyapi.core.feature.FeatureId
import com.itangcent.easyapi.core.feature.FeatureRegistry
import com.itangcent.easyapi.core.feature.FeatureRegistrySnapshot
import com.itangcent.easyapi.core.feature.FeatureSettingsTransaction
import com.itangcent.easyapi.core.feature.FeatureStateIdentity
import com.itangcent.easyapi.core.feature.MissingDependency
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Registry-driven settings panel for feature enablement controls.
 *
 * One immutable snapshot supplies the groups, top-level descriptors, and nested
 * options rendered here. User edits stay in an isolated
 * [FeatureSettingsTransaction], so dependency resolution can disable dependent
 * controls without erasing their desired states. Legacy channels, formats, and
 * frameworks are represented through their existing settings-array bridges.
 */
class FeatureSettingsPanel(
    val registrySnapshot: FeatureRegistrySnapshot
) : SettingsPanel<GeneralSettings> {

    constructor(project: Project) : this(FeatureRegistry.getInstance(project).snapshot())

    private data class FeatureControl(
        val identity: FeatureStateIdentity,
        val checkBox: JBCheckBox,
        val reasonLabel: JBLabel
    )

    private val controlsById = linkedMapOf<FeatureId, FeatureControl>()
    private var transaction: FeatureSettingsTransaction? = null

    override val component: JComponent = FormBuilder.createFormBuilder().apply {
        registrySnapshot.groups.forEach { group ->
            val descriptors = registrySnapshot.descriptors.filter { it.group.id == group.id }
            if (descriptors.isNotEmpty()) {
                addComponent(
                    SettingsUiKit.titledPanel(
                        group.displayName,
                        JPanel().apply {
                            layout = BoxLayout(this, BoxLayout.Y_AXIS)
                            descriptors.forEach { add(createDescriptorComponent(it)) }
                        }
                    )
                )
            }
        }
        addComponentFillVertically(JPanel(), 0)
    }.panel

    private fun createDescriptorComponent(
        descriptor: com.itangcent.easyapi.core.feature.FeatureDescriptor
    ): JComponent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(createControl(descriptor))
        descriptor.nestedOptions.forEach { option ->
            add(
                JPanel(BorderLayout()).apply {
                    border = BorderFactory.createEmptyBorder(0, 20, 0, 0)
                    add(createControl(option), BorderLayout.CENTER)
                }
            )
        }
    }

    private fun createControl(identity: FeatureStateIdentity): JComponent {
        val checkBox = JBCheckBox(identity.displayName)
        // Tooltips live on the checkbox itself so hovering any part of the row
        // (including the label) shows the feature description.
        identity.description.takeIf { it.isNotBlank() }?.let { checkBox.toolTipText = it }
        val reasonLabel = JBLabel().apply {
            foreground = Color.GRAY
            border = BorderFactory.createEmptyBorder(0, 20, 2, 0)
            isVisible = false
        }
        controlsById[identity.id] = FeatureControl(identity, checkBox, reasonLabel)
        checkBox.addActionListener {
            transaction?.setDesiredState(identity.id, checkBox.isSelected)
            refreshControls()
        }
        // BorderLayout fills the full row width (its max size is unbounded, so the
        // parent BoxLayout.Y_AXIS does not center it) and left-aligns the NORTH
        // checkbox / CENTER reason label. This keeps top-level and nested controls
        // on the same left edge instead of the ragged CENTER_ALIGNMENT seen before.
        return JPanel(BorderLayout()).apply {
            add(checkBox, BorderLayout.NORTH)
            add(reasonLabel, BorderLayout.CENTER)
        }
    }

    /** Rebinds the controls to a fresh editing transaction. */
    fun bindTransaction(transaction: FeatureSettingsTransaction) {
        require(
            transaction.registrySnapshot.stateIdentities.map { it.id } ==
                registrySnapshot.stateIdentities.map { it.id }
        ) { "Feature transaction does not match the rendered registry snapshot" }
        this.transaction = transaction
        controlsById.forEach { (id, control) ->
            control.checkBox.isSelected = transaction.desiredState(id)
        }
        refreshControls()
    }

    private fun refreshControls() {
        val currentTransaction = transaction ?: return
        val resolved = currentTransaction.resolvedStates()
        controlsById.values.forEach { control ->
            val blockedDependency = control.identity.dependencyIds.firstOrNull { dependencyId ->
                resolved[dependencyId]?.effectiveEnabled != true
            }
            control.checkBox.isEnabled = blockedDependency == null
            val reason = if (blockedDependency == null) {
                null
            } else {
                dependencyReason(blockedDependency, resolved[control.identity.id]?.reason)
            }
            control.reasonLabel.text = reason.orEmpty()
            control.reasonLabel.isVisible = reason != null
        }
    }

    private fun dependencyReason(
        dependencyId: FeatureId,
        resolvedReason: FeatureDisabledReason?
    ): String = when (resolvedReason) {
        is MissingDependency -> "Requires unavailable feature '${resolvedReason.featureId.value}'."
        is DependencyCycle -> "Unavailable because feature dependencies contain a cycle."
        is DisabledByDependency -> requiresMessage(resolvedReason.featureId)
        else -> if (registrySnapshot.identity(dependencyId) == null) {
            "Requires unavailable feature '${dependencyId.value}'."
        } else {
            requiresMessage(dependencyId)
        }
    }

    private fun requiresMessage(dependencyId: FeatureId): String {
        val dependencyName = registrySnapshot.identity(dependencyId)?.displayName ?: dependencyId.value
        return "Requires $dependencyName to be enabled."
    }

    override fun resetFrom(settings: GeneralSettings?) {
        bindTransaction(FeatureSettingsTransaction(registrySnapshot, settings ?: GeneralSettings()))
    }

    override fun applyTo(settings: GeneralSettings) {
        transaction?.commit(settings)
    }

    override fun isModified(settings: GeneralSettings?): Boolean = transaction?.isModified() == true

    internal fun desiredStateForTest(id: FeatureId): Boolean? =
        controlsById[id]?.checkBox?.isSelected

    internal fun setDesiredStateForTest(id: FeatureId, enabled: Boolean) {
        val control = controlsById[id] ?: return
        control.checkBox.isSelected = enabled
        transaction?.setDesiredState(id, enabled)
        refreshControls()
    }

    internal fun isControlEnabledForTest(id: FeatureId): Boolean? =
        controlsById[id]?.checkBox?.isEnabled

    internal fun dependencyTextForTest(id: FeatureId): String? =
        controlsById[id]?.reasonLabel?.takeIf { it.isVisible }?.text

    internal fun toolTipTextForTest(id: FeatureId): String? =
        controlsById[id]?.checkBox?.toolTipText

    internal fun renderedGroupTitlesForTest(): List<String> =
        registrySnapshot.groups.mapNotNull { group ->
            group.displayName.takeIf { title ->
                registrySnapshot.descriptors.any { it.group.id == group.id && title.isNotEmpty() }
            }
        }
}

/** Source-compatible name retained for existing integrations. */
typealias FeaturesSettingsPanel = FeatureSettingsPanel

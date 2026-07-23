package com.itangcent.easyapi.framework.custom

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.engine.RuleEngine
import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.core.settings.ui.SettingsPanel
import com.itangcent.easyapi.framework.spi.FrameworkRegistry

/**
 * Rule-driven API class recognizer for the Custom framework.
 *
 * Recognition is purely rule-driven: [isApiClass] returns `true` iff the
 * `custom.class.is.api` rule evaluates to `true` for the class. There are
 * no hard-coded target annotations — [targetAnnotations] is empty, so the
 * framework does not participate in `AnnotatedElementsSearch` index
 * scanning (recognition happens at export time, not at index time).
 *
 * ## Enablement
 *
 * `enabledByDefault = false` (matching Feign). The effective enabled state
 * is resolved by [com.itangcent.easyapi.framework.spi.FrameworkRegistry],
 * overlaying the user's stored preference (Settings → Framework Support →
 * "custom"). When disabled, [com.itangcent.easyapi.core.export.recognizer.CompositeApiClassRecognizer]
 * filters this recognizer out of the active set, so [isApiClass] is never
 * called.
 *
 * ## Line-marker fast-path
 *
 * [matchesClass] is gated by [CustomSettings.enableLineMarker] (default
 * `false`). When the user opts in, the line-marker provider can use this
 * fast-path to claim Custom API classes without consulting the rule engine
 * (per [ApiClassRecognizer.matchesClass] contract). When off (the default),
 * Custom classes do NOT get a gutter icon — the rule-driven recognition is
 * not cheap, and the line marker fast-path is opt-in to avoid surprising
 * users with markers on classes that just happen to match a custom rule.
 *
 * ## Settings panel
 *
 * [createSettingsPanel] contributes the Custom framework's settings tab (the
 * `enableLineMarker` toggle). It returns `null` when the framework is disabled
 * in [FrameworkRegistry], so the user is never shown settings for a disabled
 * feature.
 *
 * @see CustomClassExporter
 * @see CustomRuleKeys
 * @see CustomSettings
 * @see com.itangcent.easyapi.framework.spi.FrameworkRegistry
 */
class CustomApiRecognizer(
    private val ruleEngine: RuleEngine? = null
) : ApiClassRecognizer {

    override val frameworkName: String = FRAMEWORK_NAME

    override val targetAnnotations: Set<String> = emptySet()

    override val enabledByDefault: Boolean = false

    /**
     * Contributes the 18 `custom.*` rule keys (13 extraction + 5 lifecycle) to
     * [com.itangcent.easyapi.core.rule.RuleKeyRegistry] so `list_rule_keys` /
     * `RuleProposalValidator` surface them.
     */
    override fun ruleKeys(): List<RuleKey<*>> = RuleKey.collectFrom(CustomRuleKeys)

    /**
     * Contributes the Custom framework's settings tab (the `enableLineMarker`
     * toggle) to the EasyApi settings dialog. Returns `null` when the framework
     * is disabled so no panel is rendered for a disabled feature.
     */
    override fun createSettingsPanel(project: Project): SettingsPanel<*>? {
        if (!FrameworkRegistry.getInstance(project).isEnabled(frameworkName)) return null
        return CustomSettingsPanel(project)
    }

    /**
     * Line-marker fast-path — gated by [CustomSettings.enableLineMarker].
     *
     * Per [ApiClassRecognizer.matchesClass] contract, this MUST NOT consult
     * the rule engine. Returning `true` claims the class for the line-marker
     * provider (which then proceeds to the more expensive per-method check
     * via `ApiIndex`/`isApiMethod`). Default `false` keeps the Custom
     * framework invisible to the line marker until the user opts in.
     */
    override fun matchesClass(psiClass: PsiClass): Boolean {
        return psiClass.project.settings<CustomSettings>().enableLineMarker
    }

    override suspend fun isApiClass(psiClass: PsiClass): Boolean {
        // FrameworkRegistry gate is enforced by CompositeApiClassRecognizer
        // (it filters the active set before calling isApiClass).
        //
        // Engine resolution: when ruleEngine == null (EP-registered instance,
        // no-arg constructor), resolve lazily from psiClass.project. The
        // exporter's internal instance is constructed with the engine injected
        // for testability and to avoid repeated getInstance lookups.
        val engine = ruleEngine ?: RuleEngine.getInstance(psiClass.project)
        return engine.evaluate(CustomRuleKeys.CUSTOM_CLASS_IS_API, psiClass)
    }

    companion object {
        /** Join key tying the recognizer to the exporter and FrameworkRegistry. */
        const val FRAMEWORK_NAME = "Custom"
    }
}

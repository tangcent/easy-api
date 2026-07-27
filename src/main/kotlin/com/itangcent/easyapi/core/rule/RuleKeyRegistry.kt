package com.itangcent.easyapi.core.rule

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.channel.spi.ChannelRegistry
import com.itangcent.easyapi.core.export.recognizer.CompositeApiClassRecognizer
import com.itangcent.easyapi.framework.spi.FrameworkRegistry

/**
 * Project-level registry of every rule key known to the plugin.
 *
 * Single source of truth that combines four sources:
 *
 * 1. **General/shared keys** — declared in [RuleKeys], reflected via [RuleKey.collectFrom].
 * 2. **Channel-specific keys** — contributed by each registered
 *    [com.itangcent.easyapi.channel.spi.Channel] via
 *    [com.itangcent.easyapi.channel.spi.Channel.ruleKeys]. The channel
 *    mix differs per repo (easy-api registers hoppscotch; easy-yapi registers
 *    yapi), so the registry's output reflects whichever channels are loaded.
 * 3. **Framework-specific keys** — contributed by each registered
 *    [com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer] via
 *    [com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer.ruleKeys]
 *    (e.g. the Custom framework's `custom.*` keys). Mirrors the channel source.
 * 4. **Implicit keys** — read by name via `configReader.getFirst("…")`
 *    somewhere in the codebase but not declared as a [RuleKey] constant.
 *    Enumerated in [IMPLICIT_KEY_NAMES] so [RuleProposalValidator] and
 *    [com.itangcent.easyapi.core.ai.tools.ListRuleKeysTool] surface them too.
 *
 * ## Sources consumed
 * - [RuleKeys] (general)
 * - [ChannelRegistry.allChannels] → [com.itangcent.easyapi.channel.spi.Channel.ruleKeys]
 * - [CompositeApiClassRecognizer.allRecognizers] → [com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer.ruleKeys]
 * - [IMPLICIT_KEY_NAMES] (static)
 *
 * ## Consumers
 * - [com.itangcent.easyapi.core.ai.tools.ListRuleKeysTool] — exposes the full
 *   catalog to the AI agent.
 * - [RuleProposalValidator] — rejects proposals that use unknown keys.
 *
 * ## Usage
 * ```kotlin
 * val registry = RuleKeyRegistry.getInstance(project)
 * val allKeys = registry.allKeys()              // List<RuleKeyInfo>
 * val knownNames = registry.allKeyNames()       // Set<String> for O(1) lookup
 * val info = registry.findKey("api.name")       // RuleKeyInfo?
 * ```
 */
@Service(Service.Level.PROJECT)
class RuleKeyRegistry(private val project: Project) {

    /**
     * A rule key plus the [source] that contributed it.
     *
     * @param key the underlying [RuleKey]
     * @param source `"general"` for [RuleKeys]; the channel id (e.g.
     *     `"hoppscotch"`, `"yapi"`) for channel-specific keys; the framework
     *     name (e.g. `"custom"`) for framework-specific keys; `"implicit"`
     *     for keys read by name only.
     */
    data class RuleKeyInfo(
        val key: RuleKey<*>,
        val source: String
    )

    /**
     * All known rule keys (general + channel + framework + implicit),
     * de-duplicated by primary name. General keys take precedence over
     * channel/framework/implicit keys with the same name (a channel or
     * framework should not re-declare a general key, but the guard prevents
     * confusing duplicates if one slips in).
     */
    fun allKeys(): List<RuleKeyInfo> {
        val channelKeys = ChannelRegistry.getInstance(project)
            .allChannels().map { it.id to it.ruleKeys() }
        val frameworkKeys = CompositeApiClassRecognizer.getInstance(project)
            .allRecognizers().map { it.frameworkName to it.ruleKeys() }
        return assembleKeys(channelKeys, frameworkKeys)
    }

    /**
     * The subset of [allKeys] whose owning channel/framework is enabled.
     *
     * This is the enablement-aware view consumed by the AI tooling
     * ([com.itangcent.easyapi.core.ai.tools.ListRuleKeysTool]). The Settings
     * UI continues to use [allKeys] so disabled-source keys remain browsable
     * and re-enableable (design D8 — enablement at the AI view layer, not the
     * registry).
     *
     * Resolution rules (design C4a + AC-S4 prefix check):
     * - **General/implicit keys**: enabled unless the key name belongs to a
     *   disabled channel via prefix matching (e.g. `postman.test` → Postman
     *   channel). The `postman.*` and `markdown.*` keys are declared in
     *   [RuleKeys] (general source) but semantically owned by their
     *   respective channels — the prefix check enforces AC-S4.
     * - **Channel-sourced keys** (`info.source` is a registered channel id):
     *   enabled iff the channel is enabled.
     * - **Framework-sourced keys** (`info.source` is a registered framework
     *   name): enabled iff the framework is enabled.
     * - **Unknown source kind**: always enabled (never filter).
     */
    fun enabledKeys(): List<RuleKeyInfo> {
        val channelRegistry = ChannelRegistry.getInstance(project)
        val allChannels = channelRegistry.allChannels()
        val enabledChannelIds = allChannels
            .filter { channelRegistry.isEnabled(it) }
            .map { it.id }
            .toSet()
        val allChannelIds = allChannels.map { it.id }.toSet()

        val allRecognizers = CompositeApiClassRecognizer.getInstance(project).allRecognizers()
        val allFrameworkIds = allRecognizers.map { it.frameworkName }.toSet()
        val enabledFrameworkIds = allRecognizers
            .filter { FrameworkRegistry.getInstance(project).isEnabled(it) }
            .map { it.frameworkName }
            .toSet()

        return allKeys().filter {
            isEnabledSource(
                it, enabledChannelIds, allChannelIds, enabledFrameworkIds, allFrameworkIds
            )
        }
    }

    /**
     * The set of every known rule key name (primary + aliases), for O(1)
     * validation lookup. Used by [RuleProposalValidator] to reject unknown keys.
     */
    fun allKeyNames(): Set<String> =
        allKeys().flatMap { it.key.allNames }.toSet()

    /** Finds the [RuleKeyInfo] for [name] (primary or alias), or `null`. */
    fun findKey(name: String): RuleKeyInfo? =
        allKeys().firstOrNull { name in it.key.allNames }

    companion object {
        private const val SOURCE_GENERAL = "general"
        private const val SOURCE_IMPLICIT = "implicit"

        /**
         * Rule keys read by name via `configReader.getFirst("…")` but not
         * declared as a `RuleKey` constant in [RuleKeys].
         *
         * Each entry documents the call site that reads it. Add new entries
         * here whenever a new `configReader.getFirst("fixed.name")` call is
         * introduced — this keeps the AI tooling and validator in sync without
         * forcing the call site to import [RuleKeys].
         *
         * Dynamic key scans (e.g. `MockRuleLoader` scanning `mock[...]` keys
         * by prefix) are NOT enumerated here — those are open-ended prefixes,
         * not fixed key names.
         */
        internal val IMPLICIT_KEYS: List<RuleKey<*>> = listOf(
            // DefaultPsiClassHelper.maxDeep() / maxElements()
            RuleKey.string("max.deep"),
            RuleKey.string("max.elements"),
            // MarkdownChannel — remote template fetcher tuning
            RuleKey.string("markdown.template.url.ttl.seconds"),
            RuleKey.string("markdown.template.url.max.bytes")
        )

        /**
         * Pure assembly of the rule-key catalog from four sources:
         * 1. [RuleKeys] (reflected via [RuleKey.collectFrom])
         * 2. [channelKeys] — pairs of `(channelId, keys)` from each registered channel
         * 3. [frameworkKeys] — pairs of `(frameworkName, keys)` from each registered framework
         * 4. [IMPLICIT_KEYS]
         *
         * Extracted from [allKeys] so it can be unit-tested without a real
         * IntelliJ [Project] (the only project dependencies are
         * [ChannelRegistry] and [CompositeApiClassRecognizer], which the
         * caller supplies via [channelKeys] / [frameworkKeys]).
         */
        internal fun assembleKeys(
            channelKeys: List<Pair<String, List<RuleKey<*>>>>,
            frameworkKeys: List<Pair<String, List<RuleKey<*>>>> = emptyList()
        ): List<RuleKeyInfo> {
            val seen = HashSet<String>()
            val result = mutableListOf<RuleKeyInfo>()

            RuleKey.collectFrom(RuleKeys).forEach { key ->
                if (seen.add(key.name)) result.add(RuleKeyInfo(key, SOURCE_GENERAL))
            }
            channelKeys.forEach { (source, keys) ->
                keys.forEach { key ->
                    if (seen.add(key.name)) result.add(RuleKeyInfo(key, source))
                }
            }
            frameworkKeys.forEach { (source, keys) ->
                keys.forEach { key ->
                    if (seen.add(key.name)) result.add(RuleKeyInfo(key, source))
                }
            }
            IMPLICIT_KEYS.forEach { key ->
                if (seen.add(key.name)) result.add(RuleKeyInfo(key, SOURCE_IMPLICIT))
            }
            return result
        }

        /**
         * Pure resolution rule for whether a [RuleKeyInfo] should be visible
         * to the AI agent (i.e. its owning channel/framework is enabled).
         * Extracted so unit tests can exercise the full truth table without a
         * real IntelliJ [Project] (mirrors [ChannelRegistry.resolveEnabled]
         * and [FrameworkRegistry.resolveEnabled]).
         *
         * Resolution order (design C4a + AC-S4 prefix check):
         * 1. **General/implicit keys** → enabled unless the key name belongs
         *    to a disabled channel via prefix matching (e.g. `"postman.test"`
         *    belongs to the `"postman"` channel). The `postman.*` keys are
         *    declared in [RuleKeys] (general source) but semantically owned
         *    by the Postman channel — the prefix check enforces AC-S4.
         * 2. **Channel-sourced keys** (`info.source` ∈ [allChannelIds]) →
         *    enabled iff `info.source` ∈ [enabledChannelIds].
         * 3. **Framework-sourced keys** (`info.source` ∈ [allFrameworkIds])
         *    → enabled iff `info.source` ∈ [enabledFrameworkIds].
         * 4. **Unknown source kind** → enabled (never filter).
         *
         * No format branch in v1 — there are no format-sourced keys today
         * (see design C4a note).
         *
         * @param info the rule key info to check
         * @param enabledChannelIds channel ids that are currently enabled
         * @param allChannelIds all registered channel ids (for prefix
         *     matching on general/implicit keys + source resolution)
         * @param enabledFrameworkIds framework names that are currently enabled
         * @param allFrameworkIds all registered framework names (for source
         *     resolution; uses the unfiltered `allRecognizers()` view so
         *     disabled frameworks are still resolvable)
         */
        internal fun isEnabledSource(
            info: RuleKeyInfo,
            enabledChannelIds: Set<String>,
            allChannelIds: Set<String>,
            enabledFrameworkIds: Set<String>,
            allFrameworkIds: Set<String>
        ): Boolean {
            val keyName = info.key.name

            // 1. General/implicit keys: check channel prefix (AC-S4)
            if (info.source == SOURCE_GENERAL || info.source == SOURCE_IMPLICIT) {
                for (channelId in allChannelIds) {
                    if (keyName.startsWith("$channelId.") && channelId !in enabledChannelIds) {
                        return false
                    }
                }
                return true
            }

            // 2. Channel-sourced key
            if (info.source in allChannelIds) {
                return info.source in enabledChannelIds
            }

            // 3. Framework-sourced key
            if (info.source in allFrameworkIds) {
                return info.source in enabledFrameworkIds
            }

            // 4. Unknown source kind: don't filter
            return true
        }

        fun getInstance(project: Project): RuleKeyRegistry = project.service()
    }
}

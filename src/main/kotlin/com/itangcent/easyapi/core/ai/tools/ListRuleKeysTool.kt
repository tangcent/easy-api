package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.agent.PromptCatalog
import com.itangcent.easyapi.core.rule.RuleKeyRegistry
import com.itangcent.easyapi.core.rule.context.RuleScriptContextCatalog
import com.itangcent.easyapi.core.util.json.GsonUtils

/**
 * Perception tool that lists all known rule keys.
 *
 * Delegates to [RuleKeyRegistry] so the catalog reflects the general/shared
 * [com.itangcent.easyapi.core.rule.RuleKeys] plus every registered channel's
 * channel-specific keys plus the implicit keys read by name via
 * `configReader.getFirst(…)`. Returns a JSON array of `{name, type, source}`
 * objects. Every result also includes a compact `scriptContext` summary with
 * the execution mode, possible `it` types, key-specific bindings, and the
 * `get_rule_context` detail-tool name. Call `get_rule_context(key=…)` before
 * writing a Groovy or Postman script; it returns the complete object method
 * and property directory.
 *
 * `source` is `"general"`, `"implicit"`, or a channel/framework id (e.g.
 * `"hoppscotch"`, `"yapi"`).
 *
 * Keys are de-duplicated by name by [RuleKeyRegistry] — general keys take
 * precedence over channel/implicit keys with the same name.
 *
 * The `RuleKeys.kt` source file groups keys by Kotlin comment sections; those
 * comments aren't visible to reflection, so this tool returns a flat list
 * (the `source` field is the closest thing to grouping).
 *
 * ## Catalog join (design C4 / task A5)
 *
 * For each key, the tool looks up [PromptCatalog.entry] in the `"rules"`
 * category by exact key name. When a per-key catalog file exists, two extra
 * fields are attached to the JSON object:
 * - `description` — the entry's `cue` (one-line "when to use").
 * - `detailPromptId` — the entry's `id` (fetch the full recipe via
 *   `get_rule_detail(key=…)`).
 *
 * Keys with no per-key catalog file still return `{name, type, source}`. The
 * catalog lookup is best-effort: a missing/empty/stale catalog leaves all keys
 * at the basic shape (no throw).
 */
class ListRuleKeysTool : AiTool {

    override val name: String = "list_rule_keys"

    override val description: String =
        "List all known EasyAPI rule keys. Returns a JSON array of " +
            "{name, type, source, scriptContext, description?, detailPromptId?}. " +
            "scriptContext is a compact summary; call get_rule_context(key=…) " +
            "for bindings and callable object APIs."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = emptyMap()

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val keys = RuleKeyRegistry.getInstance(ctx.project).enabledKeys().map { info ->
            val base = mutableMapOf<String, Any?>(
                "name" to info.key.name,
                "type" to info.key::class.simpleName,
                "source" to info.source
            )
            base["scriptContext"] = RuleScriptContextCatalog.describe(info.key, info.source).summary
            // Best-effort catalog join: attach description + detailPromptId
            // when a per-key recipe file exists. A missing/empty catalog
            // leaves the base shape unchanged (no throw).
            runCatching { PromptCatalog.entry("rules", info.key.name) }
                .getOrNull()
                ?.let { entry ->
                    base["description"] = entry.cue
                    base["detailPromptId"] = entry.id
                }
            base
        }
        return ToolResult.Text(GsonUtils.toJson(keys))
    }
}

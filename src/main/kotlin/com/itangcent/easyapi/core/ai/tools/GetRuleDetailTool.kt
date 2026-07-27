package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.agent.Ambient
import com.itangcent.easyapi.core.ai.agent.AmbientPerception
import com.itangcent.easyapi.core.ai.agent.PromptCatalog

/**
 * Perception tool that fetches per-key rule recipes from the catalog
 * (design C3 / task A4).
 *
 * Two access patterns:
 *
 * - **By key** — `get_rule_detail(key="postman.test")` returns the single
 *   per-key recipe. `key` takes precedence over any scope args; this is the
 *   pattern to use when the agent knows which key it is about to set.
 *
 * - **By scope** — `get_rule_detail(channel="postman")` returns the
 *   concatenated recipes of every rule file whose `CatalogScope` matches the
 *   supplied args **and** the ambient-enabled features. Use this when the
 *   agent wants a tour of what a channel/format/framework supports (e.g.
 *   before proposing a Postman workflow bundle).
 *
 * At least one of `key` / `channel` / `format` / `framework` is required;
 * calling with no args returns `Error`.
 *
 * An empty scope match (e.g. `channel="postman"` when Postman is disabled)
 * returns `Text("(no rule-detail files match the given scope)")` — not
 * `Error` — so the agent can react gracefully.
 */
class GetRuleDetailTool : AiTool {

    override val name: String = "get_rule_detail"

    override val description: String =
        "Fetch the full recipe for one rule key (by `key`) or every rule " +
            "file matching a scope (by `channel` / `format` / `framework`). " +
            "At least one argument is required. `key` takes precedence over " +
            "scope args. Returns Markdown."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "key" to mapOf(
                "type" to "string",
                "description" to "The rule key whose recipe to fetch " +
                    "(e.g. \"postman.test\"). Takes precedence over scope args."
            ),
            "channel" to mapOf(
                "type" to "string",
                "description" to "Scope query: concatenate recipes of every " +
                    "rule file scoped to this channel id (e.g. \"postman\")."
            ),
            "format" to mapOf(
                "type" to "string",
                "description" to "Scope query: concatenate recipes of every " +
                    "rule file scoped to this field-format id."
            ),
            "framework" to mapOf(
                "type" to "string",
                "description" to "Scope query: concatenate recipes of every " +
                    "rule file scoped to this framework id."
            )
        ),
        "required" to emptyList<String>()
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val key = args["key"] as? String
        val channel = args["channel"] as? String
        val format = args["format"] as? String
        val framework = args["framework"] as? String

        // 1. By-key lookup — key wins over scope args.
        if (!key.isNullOrBlank()) {
            val body = PromptCatalog.body("rules", key)
                ?: return ToolResult.Error("unknown rule key (no catalog file): $key")
            return ToolResult.Text(body)
        }

        // 2. Scope query — at least one scope arg is required.
        if (channel.isNullOrBlank() && format.isNullOrBlank() && framework.isNullOrBlank()) {
            return ToolResult.Error("provide at least one of key / channel / format / framework")
        }

        // The ambient-enabled features gate the scope match so a disabled
        // channel's recipes are not surfaced (mirrors the index message
        // filtering). Reuse the ambient cached on working memory by the agent
        // loop; fall back to a fresh capture when no turn has run yet (e.g.
        // ad-hoc tool unit tests that build their own ToolContext).
        val amb: Ambient = ctx.workingMemory.ambient
            ?: AmbientPerception.capture(ctx.project)
        val entries = PromptCatalog.listFor(
            "rules",
            activeChannels = amb.enabledChannels.toSet(),
            activeFormats = amb.enabledFormats.toSet(),
            activeFrameworks = amb.frameworkHints.toSet()
        ).filter { e ->
            // Apply the supplied scope args on top of the ambient-enabled
            // filter: a `channel=postman` query returns only postman-scoped
            // entries (and only when postman is enabled).
            (channel.isNullOrBlank() || e.scope.channel == channel) &&
                (format.isNullOrBlank() || e.scope.format == format) &&
                (framework.isNullOrBlank() || e.scope.framework == framework)
        }

        if (entries.isEmpty()) {
            return ToolResult.Text("(no rule-detail files match the given scope)")
        }
        // Concatenate bodies with a clear separator so the agent can
        // distinguish multiple recipes in one response.
        val sb = StringBuilder()
        for ((idx, entry) in entries.withIndex()) {
            if (idx > 0) sb.append("\n\n---\n\n")
            val body = PromptCatalog.body("rules", entry.id)
            if (body != null) {
                sb.append("# ").append(entry.title).append("\n\n")
                sb.append(body)
            }
        }
        return ToolResult.Text(sb.toString())
    }
}

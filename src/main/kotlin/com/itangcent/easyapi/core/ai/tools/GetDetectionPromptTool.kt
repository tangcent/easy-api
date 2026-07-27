package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.agent.PromptCatalog

/**
 * Perception tool that fetches the full detection recipe for one detection
 * family by `id` (design C3 / task A4).
 *
 * The Reactive path's seed prompt lists the available detection ids at
 * conversation start (enablement-aware — only entries whose `channel:` /
 * `format:` / `framework:` scope matches an enabled feature appear). The
 * agent fetches the full recipe on demand via this tool when the user's
 * request touches that family.
 *
 * Returns the markdown body of the catalog file, or `Error` when the id is
 * unknown. Unknown ids are not hard failures in the agent loop — the agent
 * may retry with a different id or fall back to `get_plugin_doc name="rule-guide"`.
 */
class GetDetectionPromptTool : AiTool {

    override val name: String = "get_detection_prompt"

    override val description: String =
        "Fetch the full detection recipe for one detection family by id " +
            "(e.g. \"static-auth\", \"auth-token-chaining\", " +
            "\"spring-filters-interceptors\"). Returns the recipe as Markdown. " +
            "Use this when the user's request touches a detection family " +
            "before proposing rules for it."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "id" to mapOf(
                "type" to "string",
                "description" to "The detection family id (one of the ids " +
                    "listed in the detection index at conversation start)."
            )
        ),
        "required" to listOf("id")
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val id = args["id"] as? String
        if (id.isNullOrBlank()) {
            return ToolResult.Error("missing required parameter: id")
        }
        val body = PromptCatalog.body("detection", id)
            ?: return ToolResult.Error("unknown detection id: $id")
        return ToolResult.Text(body)
    }
}

package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.rule.RuleKeyRegistry
import com.itangcent.easyapi.core.rule.context.RuleScriptContextCatalog
import com.itangcent.easyapi.core.util.json.GsonUtils

/**
 * Returns the executable script-context contract for one registered rule key.
 *
 * Unlike a prose recipe, this tool describes the actual runtime bindings,
 * available stages, object properties, and callable method signatures. It
 * resolves the key through [RuleKeyRegistry], so aliases are accepted and only
 * keys known to the current project/channel/framework mix are returned.
 */
class GetRuleContextTool : AiTool {

    override val name: String = "get_rule_context"

    override val description: String =
        "Return structured script context for one EasyAPI rule key: execution " +
            "stage(s), it/request/response/api/common bindings, and callable " +
            "object properties and methods. Use before authoring Groovy or Postman scripts."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "key" to mapOf(
                "type" to "string",
                "description" to "Known rule key or alias (for example, \"field.ignore\" or \"postman.test\")."
            )
        ),
        "required" to listOf("key")
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val keyName = (args["key"] as? String)?.trim()
            ?: return ToolResult.Error("missing required parameter: key")
        if (keyName.isEmpty()) return ToolResult.Error("missing required parameter: key")

        val keyInfo = RuleKeyRegistry.getInstance(ctx.project).findKey(keyName)
            ?: return ToolResult.Error("unknown rule key: $keyName")
        return ToolResult.Text(GsonUtils.toJson(RuleScriptContextCatalog.describe(keyInfo.key, keyInfo.source)))
    }
}

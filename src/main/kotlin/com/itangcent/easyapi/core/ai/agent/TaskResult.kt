package com.itangcent.easyapi.core.ai.agent

/**
 * Result a sub-agent reports back to the orchestrator for one [Task].
 *
 * Sub-agents stage a [TaskResult] via `report_findings` (see
 * [com.itangcent.easyapi.core.ai.tools.ReportFindingsTool]); the
 * orchestrator's `run_sub_agent` tool awaits it and serialises it back to the
 * orchestrator's LLM as a `ToolResult.Text`. After every task closes the
 * orchestrator merges the collected [TaskResult]s via
 * [mergeTaskResults] (concatenate-with-tags, design §3.9 / D3) and calls
 * `propose_rule_content` exactly once with the merged findings.
 *
 * @property detected `true` if the detection pattern was found in the
 *   project's PSI; `false` if the sub-agent searched and found nothing.
 *   Drives the orchestrator's [TaskStatus] decision: `COMPLETED` in both
 *   cases (the sub-agent ran successfully); `FAILED` only when the
 *   sub-agent errored (FR-3.5 / FR-3.6). "Nothing detected" is a valid
 *   finding, not a skip.
 * @property findings Free-form markdown the sub-agent produced — search
 *   evidence,located classes, why the pattern applies. Concatenated verbatim
 *   into the orchestrator's final `propose_rule_content` payload, tagged
 *   `source: detection:<task.id>`.
 * @property proposedRules Concrete rule proposals the sub-agent drafted
 *   from its findings. Empty when `detected=false`. Each entry is a
 *   [RuleProposal] keyed by rule key with a short preview.
 */
data class TaskResult(
    val detected: Boolean,
    val findings: String,
    val proposedRules: List<RuleProposal> = emptyList()
)

/**
 * One concrete rule proposal a sub-agent stages via [TaskResult].
 *
 * The orchestrator's merge (design §3.9) lists each proposed rule under its
 * detection's findings block as `- <key>: <preview>`. The full rule body
 * lives inside [findings]; this entry is the catalog-facing summary the
 * user sees in the merged `propose_rule_content` payload.
 *
 * @property key The rule key this proposal targets (e.g.
 *   `method.additional.header`). Matches a key surfaced by `list_rule_keys`.
 * @property preview Short human-readable preview of the proposed value
 *   (e.g. the JSON body or a one-liner). Truncated if long — the full value
 *   is in [TaskResult.findings].
 */
data class RuleProposal(
    val key: String,
    val preview: String
)

/**
 * Merge a list of `(Task, TaskResult)` pairs into the single string payload
 * the orchestrator passes to `propose_rule_content` (design §3.9 / D3).
 *
 * Concatenate-with-tags: each task whose `detected=true` contributes a
 * `## <title>` block tagged `source: detection:<id>` containing its findings
 * and a bulleted list of proposed rules. Non-detected tasks are filtered
 * out — they contribute nothing to the merged payload (their `COMPLETED`
 * status is already recorded in the task list). When every task is
 * non-detected, the merged string is empty and
 * `OrchestratorProposeRuleContentTool` stages no proposal (FR-2.5).
 *
 * No deduplication, no LLM round-trip. Revisit if concatenated output proves
 * noisy (D3 options b/c, deferred).
 */
fun mergeTaskResults(results: List<Pair<Task, TaskResult>>): String =
    results.filter { it.second.detected }
        .joinToString("\n\n") { (task, r) ->
            buildString {
                appendLine("## ${task.title}")
                appendLine("source: detection:${task.id}")
                appendLine()
                appendLine(r.findings.trim())
                if (r.proposedRules.isNotEmpty()) {
                    appendLine()
                    appendLine("Proposed rules:")
                    r.proposedRules.forEach { appendLine("- ${it.key}: ${it.preview}") }
                }
            }
        }

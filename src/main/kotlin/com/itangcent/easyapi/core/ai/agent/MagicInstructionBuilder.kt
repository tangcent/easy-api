package com.itangcent.easyapi.core.ai.agent

/**
 * Builds the two distinct instruction bodies used by Magic.
 *
 * Magic is not a single uniform flow: an empty rule file goes straight to
 * the detection-pass contract (Route A), while a non-empty file runs a
 * single Reactive review turn first and gates the user before optional
 * detections (Route B). The two flows need different instruction bodies,
 * each pure of side effect so it can be unit-tested in isolation.
 *
 * - [detectionInstruction] — the empty-file Magic body (Phase 3 final
 *   directive). Tells the agent a task list has already been seeded into
 *   working memory, forbids `create_task_list`, and directs it to walk
 *   `PENDING` tasks one at a time by calling `run_sub_agent(taskId=...)`
 *   for each — the sub-agent runs the detection recipe in an isolated
 *   context and reports findings back. `run_sub_agent` auto-records the
 *   task status (completed if the sub-agent ran successfully, failed on
 *   error) and ticks the checklist card, so the orchestrator does NOT
 *   need to call `update_task` for the status afterwards. After all
 *   tasks close, the orchestrator calls `propose_rule_content` once with
 *   the merged findings. The orchestrator never calls perception tools
 *   itself (FR-3.2/3.3).
 *
 *   Crucially, the seeded task ids (e.g. `detect_spring_filters_interceptors`)
 *   are rendered into this body as an explicit manifest — the orchestrator
 *   LLM has no other way to discover them (the `TaskList` lives only in
 *   working memory + the UI panel; it is never serialized into the LLM
 *   transcript by the agent loop).
 *
 * - [reviewInstruction] — the non-empty Magic Stage-1 body. A single
 *   Reactive review turn that directs the agent to review and improve the
 *   file. The file's current content is embedded in a fenced block. It
 *   contains no task-list directive, no `update_task`, and no detection
 *   language — the gate (Route B Stage 2) decides whether to enter the
 *   detection-pass contract.
 */
object MagicInstructionBuilder {

    /**
     * The instruction body for the empty-file Magic flow (Route A) —
     * Phase 3 final directive (design §3.8 / FR-3.8).
     *
     * Tells the agent a task list has been seeded with one `PENDING` task
     * per detection pattern; forbids `create_task_list`; directs it to
     * walk each `PENDING` task by calling `run_sub_agent(taskId=...)` —
     * the sub-agent perceives the PSI, decides whether the pattern is
     * present, and reports back via `report_findings`. `run_sub_agent`
     * **auto-records** the task status (`completed` if the sub-agent ran
     * successfully — whether or not it detected the pattern; `failed` on
     * error) and ticks the checklist card in the UI, so the orchestrator
     * does NOT need to call `update_task` for the status afterwards.
     * "Nothing detected" is a valid finding, not a skip. After every task
     * is closed, the orchestrator calls `propose_rule_content` **once** —
     * the tool automatically merges the collected sub-agent findings via
     * concatenate-with-tags (design §3.9 / D3), so the orchestrator does
     * NOT need to compose the merged content itself. If the merged
     * findings are empty (no pattern detected), the tool stages no
     * proposal and the turn ends without prompting the user.
     *
     * The orchestrator has **no perception tools** (FR-3.2) — it must not
     * call `find_classes_by_annotation`, `get_detection_prompt`, etc.
     * All perception happens inside sub-agents. The orchestrator only
     * coordinates: `run_sub_agent` → `update_task` → `propose_rule_content`.
     *
     * The seeded [taskList] is rendered into the body as an explicit
     * manifest of `(id, title)` pairs. This is the **only** channel by
     * which the orchestrator LLM learns the exact task ids — the
     * `TaskList` lives in `AgentMemory` and the UI panel but is never
     * serialized into the LLM transcript by `RuleAuthoringAgent.runTurn`.
     * Without the manifest the LLM would have to guess the ids and
     * `run_sub_agent` would reject every guess with "unknown task id".
     *
     * Empty task list (no detection matched the enabled features, FR-2.5):
     * the body short-circuits to a single directive — call
     * `propose_rule_content` once with no findings — so the turn still
     * terminates normally instead of looping on a zero-task manifest.
     *
     * @param name the rule file's display name (used in the opening line).
     * @param taskList the seeded task list. Its ids are rendered verbatim
     *   into the manifest; the orchestrator must use them unchanged in
     *   `run_sub_agent(taskId=...)` and `update_task(taskId, ...)`.
     */
    fun detectionInstruction(name: String, taskList: TaskList): String = buildString {
        appendLine(
            "I'm starting a new rule file '$name' (currently empty). " +
                "Detect any custom framework patterns in this project that lack a rule, " +
                "then propose initial rule content for them."
        )
        appendLine()
        appendLine(
            "Standard HTTP frameworks (Spring MVC, WebFlux, JAX-RS, Feign) need no rules. " +
                "The task list seeded below covers Custom-Pattern and Workflow-Pattern " +
                "Catalog detections (Filter/HandlerInterceptor/WebFilter, " +
                "ResponseBodyAdvice, HandlerMethodArgumentResolver, custom annotations, " +
                "auth-token chaining, static auth, correlation/idempotency headers, HMAC " +
                "signing) — one task per detection family."
        )
        appendLine()
        if (taskList.tasks.isEmpty()) {
            // FR-2.5 — no detection matched the enabled features. Direct the
            // orchestrator straight to the terminal action so the turn ends
            // normally instead of looping on an empty manifest.
            appendLine(
                "No detection tasks were seeded for this project's enabled features " +
                    "(the catalog had no matching entry). Skip run_sub_agent and " +
                    "update_task entirely and call propose_rule_content ONCE with a " +
                    "suggestedFileName and empty findings to end the turn. The tool " +
                    "will stage no proposal (nothing to apply) and the turn will end."
            )
            return@buildString
        }
        appendLine(
            "A task list has been seeded for you with one task per detection pattern. " +
                "Do NOT call create_task_list — the task list is already in working memory. " +
                "You are the ORCHESTRATOR: you have three tools (run_sub_agent, update_task, " +
                "propose_rule_content) and NO perception tools."
        )
        appendLine()
        appendLine(
            "Seeded tasks — use these EXACT ids verbatim as the taskId argument to " +
                "run_sub_agent and update_task. Do NOT invent, abbreviate, reformat, " +
                "or guess ids; a wrong id is rejected with \"unknown task id\"."
        )
        taskList.tasks.forEachIndexed { index, task ->
            appendLine("${index + 1}. ${task.id} — ${task.title}")
        }
        appendLine()
        appendLine("For each PENDING task:")
        appendLine()
        appendLine("1. Call run_sub_agent(taskId=...) to spawn a sub-agent for that task.")
        appendLine("   The sub-agent perceives the project's PSI in isolation, decides")
        appendLine("   whether the pattern is present, and reports back via report_findings.")
        appendLine("   run_sub_agent returns the sub-agent's findings (detected, findings,")
        appendLine("   proposedRules) as text. It ALSO automatically records the task")
        appendLine("   status (completed if the sub-agent ran successfully — whether or")
        appendLine("   not it detected the pattern; failed on error) and ticks the")
        appendLine("   checklist card in the UI, so you do NOT need to call update_task")
        appendLine("   afterwards for the status. \"Nothing detected\" is a valid finding,")
        appendLine("   NOT a skip.")
        appendLine("2. After EVERY task is closed (run_sub_agent returned for all of")
        appendLine("   them), call propose_rule_content ONCE with a suggestedFileName.")
        appendLine("   You do NOT need to compose the merged content yourself — the tool")
        appendLine("   automatically merges the collected sub-agent findings via")
        appendLine("   concatenate-with-tags (each detection's findings tagged")
        appendLine("   `source: detection:<id>`). If no pattern was detected, the tool")
        appendLine("   stages no proposal and the turn ends without prompting the user.")
        appendLine()
        appendLine(
            "Do NOT call propose_rule_content until all tasks are closed. Do NOT call " +
                "perception tools (find_classes_by_annotation, get_detection_prompt, " +
                "get_psi_class_info, etc.) — they are not in your tool set. Each " +
                "detection runs inside its own sub-agent with a fresh memory; the " +
                "results are merged for you."
        )
    }

    /**
     * The instruction body for the non-empty Magic flow (Route B Stage 1).
     *
     * Directs the agent to review and improve the file. The file's current
     * content is embedded in a fenced block. Contains no task-list
     * directive, no `update_task`, and no detection language — the gate
     * decides whether to enter the detection-pass contract (Route B
     * Stage 2), and Stage 2 itself runs the [detectionInstruction] body
     * with the file content read at gate-Yes time.
     *
     * @param name the rule file's display name (used in the opening line).
     * @param content the file's current content (embedded in a fenced
     *   block so the agent can review it).
     */
    fun reviewInstruction(name: String, content: String): String = buildString {
        appendLine(
            "Review and improve the rule file '$name' that I'm editing. " +
                "Fix anything broken or incomplete, then propose the full updated file content."
        )
        appendLine()
        appendLine("Current content of '$name':")
        appendLine("```")
        append(content)
        appendLine()
        appendLine("```")
    }
}

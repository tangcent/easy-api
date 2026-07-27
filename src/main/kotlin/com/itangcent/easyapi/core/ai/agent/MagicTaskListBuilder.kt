package com.itangcent.easyapi.core.ai.agent

/**
 * Builds the entrance-driven task list for a Magic run.
 *
 * Magic is the task-list entry path for "full audit" tasks: instead of
 * letting the agent decide which detections to run (and burning a turn on
 * `create_task_list`), the caller pre-builds a [TaskList] with one [Task] per
 * detection file in `ai/detection/`, in catalog-manifest order. The task list
 * is seeded into `AgentMemory` via `AiChatPanel.runTaskList` before the first
 * LLM round-trip, so the Todo List renders immediately and the agent executes
 * the seeded tasks directly (entrance-driven, not agent-driven).
 *
 * ## Feature filtering (AC-S7)
 *
 * The task list is **enablement-aware**: only detections whose `channel:` /
 * `format:` / `framework:` scope matches the currently-enabled features
 * become tasks. A `framework: springmvc` detection is excluded when Spring
 * MVC is disabled (or absent from the project) — there is nothing to scan,
 * so seeding a task would only waste a turn on `update_task(skipped)`. The
 * same filter that [SystemPromptBuilder.indexMessage] applies to the
 * Reactive menu is applied here, so Magic and Reactive agree on which
 * detections exist for this turn.
 */
object MagicTaskListBuilder {

    /**
     * Builds a [TaskList] with one [Task] per detection catalog entry whose
     * scope matches the supplied active-feature sets.
     *
     * - Task id: `"detect_" + ` detection id with non-alphanumeric chars
     *   replaced by `_` (keeps `update_task`'s `taskId` simple and stable).
     * - Task title: detection `title`.
     * - Task detail: detection `cue` (one-line "when to use").
     * - Task status: [TaskStatus.PENDING].
     *
     * @param activeChannels ids of the currently-enabled export channels
     *   (e.g. `"postman"`, `"markdown"`). Detections with `channel:` scope
     *   not in this set are excluded.
     * @param activeFormats ids of the currently-enabled field-format
     *   channels (e.g. `"json"`, `"yaml"`). Detections with `format:` scope
     *   not in this set are excluded.
     * @param activeFrameworks framework labels of the currently-enabled AND
     *   PSI-detected web frameworks (e.g. `"springmvc"`, `"jaxrs"`). These
     *   come from [Ambient.frameworkHints]. Detections with `framework:`
     *   scope not in this set are excluded.
     * @return the task list. Empty (zero tasks) if no detection matches the
     *   enabled features; the caller still seeds it and the agent proceeds
     *   straight to `propose_rule_content`.
     */
    fun buildDetectionPlan(
        activeChannels: Set<String>,
        activeFormats: Set<String>,
        activeFrameworks: Set<String>
    ): TaskList {
        val tasks = PromptCatalog.listFor(
            "detection",
            activeChannels,
            activeFormats,
            activeFrameworks
        ).map { entry ->
            Task(
                id = "detect_" + entry.id.replace(Regex("[^A-Za-z0-9]"), "_"),
                title = entry.title,
                detail = entry.cue,
                status = TaskStatus.PENDING
            )
        }
        return TaskList(tasks)
    }
}

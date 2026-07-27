package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.agent.AgentEvent
import com.itangcent.easyapi.core.ai.agent.Task
import com.itangcent.easyapi.core.ai.agent.TaskList
import com.itangcent.easyapi.core.ai.agent.TaskStatus
import com.itangcent.easyapi.core.logging.IdeaLog

/**
 * Magic-path tool that commits the agent to a concrete task list before it
 * starts producing rules (design C7 / task B4).
 *
 * - `name = "create_task_list"`, `kind = PERCEPTION`, `requiresApproval = false`.
 * - Schema: `{ tasks: [{ id, title, detail? }] }`.
 * - Behaviour: builds a [TaskList] with every task at [TaskStatus.PENDING],
 *   stores it in `ctx.workingMemory.taskList`, emits
 *   [AgentEvent.TaskListCreated] via `ctx.events` so the UI renders a live
 *   checklist card, and returns a one-line confirmation fed back to the LLM.
 *
 * If a task list is already staged the call still replaces it (edge case)
 * and logs `console.info("task list replaced")` so the replacement is
 * observable. The previous task list's progress is discarded —
 * `create_task_list` is a fresh commitment, not a merge.
 *
 * The agent is instructed (in `agent-base.md`) to use this tool only for
 * Magic tasks (≥2 distinct steps). Reactive chat turns never call it.
 */
class CreateTaskListTool : AiTool, IdeaLog {

    override val name: String = "create_task_list"

    override val description: String =
        "Commit to a concrete task list before working a complex (Magic) " +
            "task. Pass an ordered list of tasks, each with a short stable " +
            "id (e.g. \"s1\", \"s2\"), a one-line title, and an optional " +
            "1-2 sentence detail. All tasks start PENDING; use update_task " +
            "to mark them in_progress / completed / failed / skipped as you " +
            "work. Use this ONLY for Magic tasks with ≥2 distinct " +
            "steps; plain chat does not need a task list."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "tasks" to mapOf(
                "type" to "array",
                "description" to "Ordered list of tasks. Each task has " +
                    "an id (short, stable, e.g. \"s1\"), a title (one line), " +
                    "and an optional detail (1-2 sentences). Task ids MUST be " +
                    "unique within the task list.",
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "id" to mapOf(
                            "type" to "string",
                            "description" to "Short stable identifier used in " +
                                "subsequent update_task calls and lifecycle " +
                                "events."
                        ),
                        "title" to mapOf(
                            "type" to "string",
                            "description" to "One-line description of what " +
                                "this task does."
                        ),
                        "detail" to mapOf(
                            "type" to "string",
                            "description" to "Optional 1-2 sentence elaboration."
                        )
                    ),
                    "required" to listOf("id", "title")
                ),
                "minItems" to 1
            )
        ),
        "required" to listOf("tasks")
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        @Suppress("UNCHECKED_CAST")
        val rawTasks = args["tasks"] as? List<Map<String, Any?>>
        if (rawTasks.isNullOrEmpty()) {
            return ToolResult.Error("missing required parameter: tasks (non-empty array)")
        }

        // Parse + validate each task. Missing id/title is a recoverable error
        // so the agent can retry with a corrected call.
        val parsed = mutableListOf<Task>()
        for ((index, raw) in rawTasks.withIndex()) {
            val id = (raw["id"] as? String)?.trim()
            if (id.isNullOrBlank()) {
                return ToolResult.Error("tasks[$index].id is missing or blank")
            }
            val title = (raw["title"] as? String)?.trim()
            if (title.isNullOrBlank()) {
                return ToolResult.Error("tasks[$index].title is missing or blank")
            }
            val detail = (raw["detail"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            parsed += Task(id = id, title = title, detail = detail, status = TaskStatus.PENDING)
        }

        // TaskList.init rejects duplicate ids — surface as a recoverable Error
        // so the agent can correct the call rather than crash the turn.
        val taskList = try {
            TaskList(parsed)
        } catch (e: IllegalArgumentException) {
            return ToolResult.Error(e.message ?: "duplicate task ids")
        }

        if (ctx.workingMemory.taskList != null) {
            LOG.info("create_task_list: task list replaced (prev=${ctx.workingMemory.taskList!!.tasks.size} tasks)")
        }
        ctx.workingMemory.taskList = taskList
        ctx.events.emit(AgentEvent.TaskListCreated(taskList))
        return ToolResult.Text("task list created with ${taskList.tasks.size} tasks")
    }
}

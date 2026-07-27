package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.agent.AgentEvent
import com.itangcent.easyapi.core.ai.agent.TaskList
import com.itangcent.easyapi.core.ai.agent.TaskStatus
import com.itangcent.easyapi.core.logging.IdeaLog

/**
 * Magic-path tool that ticks a task in the active task list (design C7 /
 * task B5).
 *
 * - `name = "update_task"`, `kind = PERCEPTION`, `requiresApproval = false`.
 * - Schema: `{ taskId, status, reason? }` where `status` ∈
 *   `in_progress | completed | failed | skipped`.
 * - Behaviour: looks up [taskId] in `ctx.workingMemory.taskList`; on miss →
 *   `Error("unknown task id: <id>")`; on no-task-list →
 *   `Error("no active task list")`. On hit: replaces the task's status,
 *   emits the matching [AgentEvent] (`TaskStarted` for `in_progress`,
 *   `TaskCompleted` for `completed`, `TaskFailed` for `failed`,
 *   `TaskSkipped` for `skipped`), and returns `Text("task <id> now <status>")`.
 *
 * `reason` is only meaningful for `failed` (it is carried on the
 * [AgentEvent.TaskFailed] event); for other statuses it is ignored.
 *
 * The agent is instructed (in `agent-base.md`) to use this tool only for
 * Magic tasks. Reactive chat turns never call it; if called without a
 * task list staged, the tool returns a recoverable `Error`.
 */
class UpdateTaskTool : AiTool, IdeaLog {

    override val name: String = "update_task"

    override val description: String =
        "Update the status of one task in the active task list. Pass the " +
            "task id (as given to create_task_list), the new status (one of " +
            "\"in_progress\", \"completed\", \"failed\", \"skipped\"), and " +
            "an optional reason (only used for \"failed\"). Marks the task " +
            "and ticks the checklist card in the UI. Use this ONLY for " +
            "Magic tasks with an active task list."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "taskId" to mapOf(
                "type" to "string",
                "description" to "The id of the task to update (as given to " +
                    "create_task_list)."
            ),
            "status" to mapOf(
                "type" to "string",
                "enum" to listOf("in_progress", "completed", "failed", "skipped"),
                "description" to "The new status. in_progress when starting " +
                    "the task, completed when done, failed if it could not be " +
                    "finished (pass a reason), skipped if deliberately skipped."
            ),
            "reason" to mapOf(
                "type" to "string",
                "description" to "Optional. Only meaningful for status " +
                    "\"failed\" — carried on the TaskFailed event for the UI."
            )
        ),
        "required" to listOf("taskId", "status")
    )

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val taskId = (args["taskId"] as? String)?.trim()
        if (taskId.isNullOrBlank()) {
            return ToolResult.Error("missing required parameter: taskId")
        }
        val statusStr = (args["status"] as? String)?.trim()
        if (statusStr.isNullOrBlank()) {
            return ToolResult.Error("missing required parameter: status")
        }
        val newStatus = parseStatus(statusStr)
            ?: return ToolResult.Error(
                "invalid status: $statusStr (expected one of " +
                    "in_progress, completed, failed, skipped)"
            )

        val taskList = ctx.workingMemory.taskList
            ?: return ToolResult.Error("no active task list")
        val task = taskList.byId(taskId)
            ?: return ToolResult.Error("unknown task id: $taskId")

        // Replace the task in-place by building a fresh task list with the
        // updated row. Task is a data class — copy + replace keeps the task
        // list immutable from the consumer's POV. TaskList.init re-validates
        // ids.
        val reason = (args["reason"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val updated = task.copy(status = newStatus)
        val newTasks = taskList.tasks.map { if (it.id == taskId) updated else it }
        ctx.workingMemory.taskList = TaskList(newTasks)

        val event = when (newStatus) {
            TaskStatus.IN_PROGRESS -> AgentEvent.TaskStarted(taskId)
            TaskStatus.COMPLETED -> AgentEvent.TaskCompleted(taskId)
            TaskStatus.FAILED -> AgentEvent.TaskFailed(taskId, reason ?: "")
            TaskStatus.SKIPPED -> AgentEvent.TaskSkipped(taskId)
            TaskStatus.PENDING -> null // not emit-worthy; treat as no-op
        }
        if (event != null) {
            ctx.events.emit(event)
        }
        return ToolResult.Text("task $taskId now $statusStr")
    }

    private fun parseStatus(value: String): TaskStatus? = when (value) {
        "in_progress" -> TaskStatus.IN_PROGRESS
        "completed" -> TaskStatus.COMPLETED
        "failed" -> TaskStatus.FAILED
        "skipped" -> TaskStatus.SKIPPED
        else -> null
    }
}

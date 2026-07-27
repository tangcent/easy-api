package com.itangcent.easyapi.core.ai.agent

/**
 * An ordered, ephemeral task list the agent commits to before working a complex
 * task in the Magic path.
 *
 * Lives in [AgentMemory.taskList] only for the duration of a conversation; never
 * persisted to disk. Tasks are addressed by [Task.id] so lifecycle events
 * ([TaskStarted], [TaskCompleted], [TaskFailed], [TaskSkipped]) can reference
 * a row without carrying the whole list.
 *
 * @property tasks Ordered list — order is presentation order; status changes
 *   are addressed by id, not by index.
 */
data class TaskList(val tasks: List<Task>) {
    init {
        require(tasks.distinctBy { it.id }.size == tasks.size) {
            "duplicate task ids: ${tasks.map { it.id }}"
        }
    }

    /** @return the task with [id], or `null` if no such task exists. */
    fun byId(id: String): Task? = tasks.firstOrNull { it.id == id }
}

/**
 * One unit of work in a [TaskList].
 *
 * @property id Stable identifier the agent uses in `update_task` calls and
 *   the lifecycle events carry back. Short, e.g. `s1`, `s2`.
 * @property title Single short line describing what this task does.
 * @property detail Optional 1–2 sentence elaboration. May be `null`.
 * @property status Lifecycle state. New task lists start every task at
 *   [TaskStatus.PENDING].
 */
data class Task(
    val id: String,
    val title: String,
    val detail: String? = null,
    val status: TaskStatus = TaskStatus.PENDING
)

/**
 * Lifecycle state of a [Task].
 *
 * Glyphs are the UI rendering for each state (design C11):
 * - PENDING → `[ ]`
 * - IN_PROGRESS → `[~]`
 * - COMPLETED → `[X]`
 * - FAILED → `[!]`
 * - SKIPPED → `[-]`
 */
enum class TaskStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED }

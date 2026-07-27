package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.AiProvider
import com.itangcent.easyapi.core.ai.AiRuntimeConfig
import com.itangcent.easyapi.core.ai.agent.AgentEvent
import com.itangcent.easyapi.core.ai.agent.AgentMemory
import com.itangcent.easyapi.core.ai.agent.ApprovalGate
import com.itangcent.easyapi.core.ai.agent.Task
import com.itangcent.easyapi.core.ai.agent.TaskList
import com.itangcent.easyapi.core.ai.agent.TaskStatus
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.source.RuleFileResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert

/**
 * Tests for [UpdateTaskTool] (design C7 / task B5).
 *
 * Verifies the contract:
 * - All four status transitions (`in_progress`, `completed`, `failed`,
 *   `skipped`) emit the matching [AgentEvent] and update the task in memory.
 * - `failed` carries the optional `reason` on the [AgentEvent.TaskFailed]
 *   event.
 * - No-task-list → `Error("no active task list")`; unknown task →
 *   `Error("unknown task id: <id>")`.
 * - Validation errors (missing/blank `taskId` / `status`, invalid status
 *   string) return `Error` and do not mutate memory.
 */
class UpdateTaskToolTest : EasyApiLightCodeInsightFixtureTestCase() {

    private fun ctx(memory: AgentMemory = AgentMemory()): ToolContext = ToolContext(
        project = project,
        configReader = ConfigReader.getInstance(project),
        aiSettings = AiRuntimeConfig(
            provider = AiProvider.OPENAI,
            baseUrl = "", apiKey = "", model = "",
            requestTimeoutSec = 30, maxRequests = 8
        ),
        ruleFileResolver = RuleFileResolver(project),
        workingMemory = memory,
        approvals = NoOpApprovalGate(),
        events = MutableSharedFlow(extraBufferCapacity = 64)
    )

    private fun stagedTaskList(): TaskList = TaskList(
        listOf(
            Task(id = "s1", title = "first"),
            Task(id = "s2", title = "second")
        )
    )

    private suspend fun collectEvents(
        ctx: ToolContext,
        block: suspend () -> Unit
    ): List<AgentEvent> = coroutineScope {
        val collected = mutableListOf<AgentEvent>()
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            ctx.events.collect { collected.add(it) }
        }
        try {
            block()
        } finally {
            collector.cancel()
        }
        collected
    }

    // ------------------------------------------------------------------
    // happy paths — one test per status transition
    // ------------------------------------------------------------------

    fun testInProgressEmitsTaskStarted() = runBlocking {
        val memory = AgentMemory().apply { taskList = stagedTaskList() }
        val ctx = ctx(memory)
        val events = collectEvents(ctx) {
            val result = UpdateTaskTool().execute(
                mapOf("taskId" to "s1", "status" to "in_progress"),
                ctx
            )
            Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
            Assert.assertEquals(
                "task s1 now in_progress",
                (result as ToolResult.Text).value
            )
        }
        // TaskStarted emitted; no other Task* events.
        val started = events.filterIsInstance<AgentEvent.TaskStarted>().singleOrNull()
        Assert.assertNotNull("TaskStarted should be emitted", started)
        Assert.assertEquals("s1", started!!.taskId)
        Assert.assertTrue(
            "no other Task* events expected",
            events.none {
                it is AgentEvent.TaskCompleted ||
                    it is AgentEvent.TaskFailed ||
                    it is AgentEvent.TaskSkipped
            }
        )
        // Memory updated.
        Assert.assertEquals(
            TaskStatus.IN_PROGRESS,
            memory.taskList!!.byId("s1")!!.status
        )
        // Sibling task untouched.
        Assert.assertEquals(
            TaskStatus.PENDING,
            memory.taskList!!.byId("s2")!!.status
        )
    }

    fun testCompletedEmitsTaskCompleted() = runBlocking {
        val memory = AgentMemory().apply { taskList = stagedTaskList() }
        val ctx = ctx(memory)
        val events = collectEvents(ctx) {
            val result = UpdateTaskTool().execute(
                mapOf("taskId" to "s2", "status" to "completed"),
                ctx
            )
            Assert.assertTrue(result is ToolResult.Text)
            Assert.assertEquals("task s2 now completed", (result as ToolResult.Text).value)
        }
        val completed = events.filterIsInstance<AgentEvent.TaskCompleted>().singleOrNull()
        Assert.assertNotNull("TaskCompleted should be emitted", completed)
        Assert.assertEquals("s2", completed!!.taskId)
        Assert.assertEquals(TaskStatus.COMPLETED, memory.taskList!!.byId("s2")!!.status)
    }

    fun testFailedEmitsTaskFailedWithReason() = runBlocking {
        val memory = AgentMemory().apply { taskList = stagedTaskList() }
        val ctx = ctx(memory)
        val events = collectEvents(ctx) {
            val result = UpdateTaskTool().execute(
                mapOf("taskId" to "s1", "status" to "failed", "reason" to "class not found"),
                ctx
            )
            Assert.assertTrue(result is ToolResult.Text)
            Assert.assertEquals("task s1 now failed", (result as ToolResult.Text).value)
        }
        val failed = events.filterIsInstance<AgentEvent.TaskFailed>().singleOrNull()
        Assert.assertNotNull("TaskFailed should be emitted", failed)
        Assert.assertEquals("s1", failed!!.taskId)
        Assert.assertEquals("class not found", failed.reason)
        Assert.assertEquals(TaskStatus.FAILED, memory.taskList!!.byId("s1")!!.status)
    }

    fun testFailedWithoutReasonEmitsTaskFailedWithEmptyReason() = runBlocking {
        val memory = AgentMemory().apply { taskList = stagedTaskList() }
        val ctx = ctx(memory)
        val events = collectEvents(ctx) {
            UpdateTaskTool().execute(
                mapOf("taskId" to "s1", "status" to "failed"),
                ctx
            )
        }
        val failed = events.filterIsInstance<AgentEvent.TaskFailed>().single()
        Assert.assertEquals("s1", failed.taskId)
        Assert.assertEquals("reason should be empty when not provided", "", failed.reason)
    }

    fun testSkippedEmitsTaskSkipped() = runBlocking {
        val memory = AgentMemory().apply { taskList = stagedTaskList() }
        val ctx = ctx(memory)
        val events = collectEvents(ctx) {
            val result = UpdateTaskTool().execute(
                mapOf("taskId" to "s2", "status" to "skipped"),
                ctx
            )
            Assert.assertTrue(result is ToolResult.Text)
            Assert.assertEquals("task s2 now skipped", (result as ToolResult.Text).value)
        }
        val skipped = events.filterIsInstance<AgentEvent.TaskSkipped>().singleOrNull()
        Assert.assertNotNull("TaskSkipped should be emitted", skipped)
        Assert.assertEquals("s2", skipped!!.taskId)
        Assert.assertEquals(TaskStatus.SKIPPED, memory.taskList!!.byId("s2")!!.status)
    }

    fun testReasonIgnoredForNonFailedStatuses() = runBlocking {
        // `reason` is only meaningful for `failed`. For other statuses it
        // should not appear in the returned text or cause issues.
        val memory = AgentMemory().apply { taskList = stagedTaskList() }
        val ctx = ctx(memory)
        val result = UpdateTaskTool().execute(
            mapOf("taskId" to "s1", "status" to "completed", "reason" to "ignored"),
            ctx
        )
        Assert.assertTrue(result is ToolResult.Text)
        Assert.assertEquals(
            "task s1 now completed",
            (result as ToolResult.Text).value
        )
    }

    // ------------------------------------------------------------------
    // error paths
    // ------------------------------------------------------------------

    fun testNoActiveTaskListReturnsError() = runBlocking {
        val memory = AgentMemory() // no task list staged
        val ctx = ctx(memory)
        val result = UpdateTaskTool().execute(
            mapOf("taskId" to "s1", "status" to "in_progress"),
            ctx
        )
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            "error should mention no active task list: ${(result as ToolResult.Error).message}",
            (result as ToolResult.Error).message.contains("no active task list")
        )
    }

    fun testUnknownTaskIdReturnsError() = runBlocking {
        val memory = AgentMemory().apply { taskList = stagedTaskList() }
        val ctx = ctx(memory)
        val result = UpdateTaskTool().execute(
            mapOf("taskId" to "nope", "status" to "in_progress"),
            ctx
        )
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            "error should mention unknown task id: ${(result as ToolResult.Error).message}",
            (result as ToolResult.Error).message.contains("unknown task id")
        )
        Assert.assertTrue(
            "error should include the bad id: ${(result as ToolResult.Error).message}",
            (result as ToolResult.Error).message.contains("nope")
        )
        // Memory untouched.
        Assert.assertEquals(TaskStatus.PENDING, memory.taskList!!.byId("s1")!!.status)
    }

    fun testMissingTaskIdReturnsError() = runBlocking {
        val memory = AgentMemory().apply { taskList = stagedTaskList() }
        val ctx = ctx(memory)
        val result = UpdateTaskTool().execute(
            mapOf("status" to "in_progress"),
            ctx
        )
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("taskId")
        )
    }

    fun testMissingStatusReturnsError() = runBlocking {
        val memory = AgentMemory().apply { taskList = stagedTaskList() }
        val ctx = ctx(memory)
        val result = UpdateTaskTool().execute(
            mapOf("taskId" to "s1"),
            ctx
        )
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("status")
        )
    }

    fun testInvalidStatusReturnsError() = runBlocking {
        val memory = AgentMemory().apply { taskList = stagedTaskList() }
        val ctx = ctx(memory)
        val result = UpdateTaskTool().execute(
            mapOf("taskId" to "s1", "status" to "done"),
            ctx
        )
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            "error should mention invalid status: ${(result as ToolResult.Error).message}",
            (result as ToolResult.Error).message.contains("invalid status")
        )
        // Memory untouched.
        Assert.assertEquals(TaskStatus.PENDING, memory.taskList!!.byId("s1")!!.status)
    }

    fun testBlankTaskIdReturnsError() = runBlocking {
        val memory = AgentMemory().apply { taskList = stagedTaskList() }
        val ctx = ctx(memory)
        val result = UpdateTaskTool().execute(
            mapOf("taskId" to "   ", "status" to "in_progress"),
            ctx
        )
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("taskId")
        )
    }

    // ------------------------------------------------------------------
    // registry / kind / schema
    // ------------------------------------------------------------------

    fun testIsPerceptionTool() {
        Assert.assertEquals(ToolKind.PERCEPTION, UpdateTaskTool().kind)
    }

    fun testDoesNotRequireApproval() {
        Assert.assertFalse(UpdateTaskTool().requiresApproval)
    }

    fun testIsRegisteredInStandardToolSet() {
        val names = standardRuleTools().map { it.name }
        Assert.assertTrue("update_task must be registered", names.contains("update_task"))
    }

    fun testSchemaDeclaresRequiredParameters() {
        val schema = UpdateTaskTool().parametersSchema
        val required = schema["required"] as List<*>
        Assert.assertTrue("taskId must be required", required.contains("taskId"))
        Assert.assertTrue("status must be required", required.contains("status"))
    }

    private class NoOpApprovalGate : ApprovalGate {
        override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean = true
    }
}

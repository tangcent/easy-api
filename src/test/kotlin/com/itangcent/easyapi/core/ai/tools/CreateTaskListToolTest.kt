package com.itangcent.easyapi.core.ai.tools

import com.itangcent.easyapi.core.ai.AiProvider
import com.itangcent.easyapi.core.ai.AiRuntimeConfig
import com.itangcent.easyapi.core.ai.agent.AgentEvent
import com.itangcent.easyapi.core.ai.agent.AgentMemory
import com.itangcent.easyapi.core.ai.agent.ApprovalGate
import com.itangcent.easyapi.core.ai.agent.TaskStatus
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.source.RuleFileResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert

/**
 * Tests for [CreateTaskListTool] (design C7 / task B4).
 *
 * Verifies the contract:
 * - Creates a task list with all tasks at [TaskStatus.PENDING], stores it in
 *   `workingMemory.taskList`, emits [AgentEvent.TaskListCreated], returns
 *   `Text("task list created with N tasks")`.
 * - Replacement path (task list already staged) emits a fresh
 *   `TaskListCreated`.
 * - Validation errors (missing/blank tasks / id / title, duplicate ids)
 *   return `Error` and do not mutate memory.
 */
class CreateTaskListToolTest : EasyApiLightCodeInsightFixtureTestCase() {

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

    private fun tasks(
        vararg tasks: Triple<String, String, String?>
    ): Map<String, Any?> = mapOf(
        "tasks" to tasks.map { (id, title, detail) ->
            if (detail == null) {
                mapOf("id" to id, "title" to title)
            } else {
                mapOf("id" to id, "title" to title, "detail" to detail)
            }
        }
    )

    fun testCreatesTaskListAndEmitsEvent() = runBlocking {
        val memory = AgentMemory()
        val ctx = ctx(memory)
        val collected = mutableListOf<AgentEvent>()
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            ctx.events.collect { collected.add(it) }
        }
        try {
            val result = CreateTaskListTool().execute(
                tasks(
                    Triple("s1", "Detect filters", "find OncePerRequestFilter subclasses"),
                    Triple("s2", "Confirm with user", null),
                    Triple("s3", "Propose rules", null)
                ),
                ctx
            )
            Assert.assertTrue("expected Text result, got $result", result is ToolResult.Text)
            Assert.assertEquals(
                "task list created with 3 tasks",
                (result as ToolResult.Text).value
            )
            // Task list staged in memory with every task PENDING.
            val taskList = memory.taskList
            Assert.assertNotNull("task list should be staged in memory", taskList)
            Assert.assertEquals(3, taskList!!.tasks.size)
            Assert.assertEquals("s1", taskList.tasks[0].id)
            Assert.assertEquals("Detect filters", taskList.tasks[0].title)
            Assert.assertEquals(
                "find OncePerRequestFilter subclasses",
                taskList.tasks[0].detail
            )
            Assert.assertEquals(TaskStatus.PENDING, taskList.tasks[0].status)
            Assert.assertEquals(TaskStatus.PENDING, taskList.tasks[1].status)
            Assert.assertEquals(TaskStatus.PENDING, taskList.tasks[2].status)
            // TaskListCreated event emitted.
            val taskListCreated = collected.filterIsInstance<AgentEvent.TaskListCreated>().singleOrNull()
            Assert.assertNotNull("TaskListCreated should be emitted", taskListCreated)
            Assert.assertSame(taskList, taskListCreated!!.taskList)
        } finally {
            collector.cancel()
        }
    }

    fun testReplacementEmitsFreshTaskListCreated() = runBlocking {
        val memory = AgentMemory()
        val ctx = ctx(memory)
        val collected = mutableListOf<AgentEvent>()
        val collector = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            ctx.events.collect { collected.add(it) }
        }
        try {
            // First call stages a 2-task task list.
            CreateTaskListTool().execute(
                tasks(Triple("a", "first", null), Triple("b", "second", null)),
                ctx
            )
            // Second call replaces with a 1-task task list.
            val result = CreateTaskListTool().execute(
                tasks(Triple("x", "replacement", null)),
                ctx
            )
            Assert.assertTrue(result is ToolResult.Text)
            Assert.assertEquals("task list created with 1 tasks", (result as ToolResult.Text).value)
            // Memory holds the replacement.
            val taskList = memory.taskList!!
            Assert.assertEquals(1, taskList.tasks.size)
            Assert.assertEquals("x", taskList.tasks[0].id)
            // Two TaskListCreated events were emitted (one per call).
            val taskListCreatedEvents = collected.filterIsInstance<AgentEvent.TaskListCreated>()
            Assert.assertEquals("expected two TaskListCreated events", 2, taskListCreatedEvents.size)
            Assert.assertSame(taskList, taskListCreatedEvents.last().taskList)
        } finally {
            collector.cancel()
        }
    }

    fun testRejectsMissingTasks() = runBlocking {
        val memory = AgentMemory()
        val ctx = ctx(memory)
        val result = CreateTaskListTool().execute(emptyMap(), ctx)
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            (result as ToolResult.Error).message.contains("tasks")
        )
        Assert.assertNull("task list must not be staged on error", memory.taskList)
    }

    fun testRejectsEmptyTasksArray() = runBlocking {
        val memory = AgentMemory()
        val ctx = ctx(memory)
        val result = CreateTaskListTool().execute(mapOf("tasks" to emptyList<Any>()), ctx)
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertNull("task list must not be staged on error", memory.taskList)
    }

    fun testRejectsTaskWithMissingId() = runBlocking {
        val memory = AgentMemory()
        val ctx = ctx(memory)
        val result = CreateTaskListTool().execute(
            mapOf("tasks" to listOf(mapOf("title" to "no id"))),
            ctx
        )
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            "error should mention id: ${(result as ToolResult.Error).message}",
            result.message.contains("id")
        )
        Assert.assertNull("task list must not be staged on error", memory.taskList)
    }

    fun testRejectsTaskWithMissingTitle() = runBlocking {
        val memory = AgentMemory()
        val ctx = ctx(memory)
        val result = CreateTaskListTool().execute(
            mapOf("tasks" to listOf(mapOf("id" to "s1"))),
            ctx
        )
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            "error should mention title: ${(result as ToolResult.Error).message}",
            result.message.contains("title")
        )
        Assert.assertNull("task list must not be staged on error", memory.taskList)
    }

    fun testRejectsDuplicateTaskIds() = runBlocking {
        val memory = AgentMemory()
        val ctx = ctx(memory)
        val result = CreateTaskListTool().execute(
            tasks(
                Triple("dup", "first", null),
                Triple("dup", "second", null)
            ),
            ctx
        )
        Assert.assertTrue(result is ToolResult.Error)
        Assert.assertTrue(
            "error should mention duplicate ids: ${(result as ToolResult.Error).message}",
            result.message.contains("duplicate")
        )
        Assert.assertNull("task list must not be staged on error", memory.taskList)
    }

    fun testIsPerceptionTool() {
        Assert.assertEquals(ToolKind.PERCEPTION, CreateTaskListTool().kind)
    }

    fun testDoesNotRequireApproval() {
        // create_task_list is PERCEPTION → requiresApproval defaults to false.
        Assert.assertFalse(CreateTaskListTool().requiresApproval)
    }

    fun testIsRegisteredInStandardToolSet() {
        val names = standardRuleTools().map { it.name }
        Assert.assertTrue("create_task_list must be registered", names.contains("create_task_list"))
    }

    fun testSchemaDeclaresRequiredParameters() {
        val schema = CreateTaskListTool().parametersSchema
        val required = schema["required"] as List<*>
        Assert.assertTrue("tasks must be required", required.contains("tasks"))
    }

    private class NoOpApprovalGate : ApprovalGate {
        override suspend fun await(toolName: String, args: Map<String, Any?>): Boolean = true
    }
}

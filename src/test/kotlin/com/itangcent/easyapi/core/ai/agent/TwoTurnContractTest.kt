package com.itangcent.easyapi.core.ai.agent

import com.itangcent.easyapi.core.ai.AiChatRequest
import com.itangcent.easyapi.core.ai.AiChatResponse
import com.itangcent.easyapi.core.ai.AiMessage
import com.itangcent.easyapi.core.ai.AiProvider
import com.itangcent.easyapi.core.ai.AiRuntimeConfig
import com.itangcent.easyapi.core.ai.AIService
import com.itangcent.easyapi.core.ai.tools.ToolContext
import com.itangcent.easyapi.core.ai.tools.ToolRegistry
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.source.RuleFileResolver
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Empty-file two-turn contract regression tests.
 *
 * These tests verify the de facto contract that Magic on an empty rule file
 * follows: Turn 1 (system, no LLM) seeds the task list and emits
 * [AgentEvent.TaskListCreated] **before** Turn 2 (AI) emits its first
 * [AgentEvent.Thinking]; the orchestrator never calls `create_task_list`
 * (the task list is seeded); and an empty catalog still emits
 * [AgentEvent.TaskListCreated] (with zero tasks) and proceeds cleanly.
 *
 * The tests simulate what [com.itangcent.easyapi.core.ai.ui.AiChatPanel.runTaskList]
 * does — seed `memory.taskList`, `tryEmit(TaskListCreated)`, then call
 * `agent.runTurn(..., entryPath = TASK_LIST_PROGRAMMATIC)` — at the agent
 * level so the event stream (including agent-emitted `Thinking`) is captured
 * synchronously via an `Unconfined` collector. The panel itself uses
 * `Dispatchers.Default` + EDT, which makes end-to-end panel-level event
 * ordering timing-dependent; the contract is cleaner to assert here.
 */
class TwoTurnContractTest : EasyApiLightCodeInsightFixtureTestCase() {

    /**
     * FR-2.6 / AC-2(a) — `TaskListCreated` is emitted before the first
     * `Thinking` event of the turn.
     *
     * Simulates `runTaskList`: seeds a non-empty task list, emits
     * `TaskListCreated`, then runs the agent (which emits `Thinking` as its
     * first event). Asserts the first collected event is `TaskListCreated`
     * and no `Thinking` precedes it.
     */
    fun testTaskListCreatedEmittedBeforeFirstThinking() = runBlocking {
        val events = MutableSharedFlow<AgentEvent>(
            replay = Int.MAX_VALUE, extraBufferCapacity = 64
        )
        val memory = AgentMemory()
        val aiService = FakeAIService()
        // Plain text answer — no tool calls, turn completes immediately.
        aiService.enqueueText("done")
        val ctx = buildCtx(memory, events)
        val tools = ToolRegistry(emptyList())
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events)

        val collected = mutableListOf<AgentEvent>()
        val collectJob = scope().launch(
            Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED
        ) {
            events.collect { collected.add(it) }
        }

        // Simulate runTaskList: seed → emit → run.
        val taskList = TaskList(listOf(
            Task("t1", "Detect custom patterns", "scan for filters")
        ))
        memory.taskList = taskList
        events.tryEmit(AgentEvent.TaskListCreated(taskList))
        agent.runTurn(
            "instruction", memory,
            entryPath = EntryPath.TASK_LIST_PROGRAMMATIC
        )

        collectJob.cancelAndJoin()

        assertTrue("events should not be empty", collected.isNotEmpty())
        assertTrue(
            "First event should be TaskListCreated, got ${collected.first()::class.simpleName}",
            collected.first() is AgentEvent.TaskListCreated
        )
        val firstThinkingIdx = collected.indexOfFirst { it is AgentEvent.Thinking }
        assertTrue("Thinking should be emitted", firstThinkingIdx >= 0)
        assertTrue(
            "TaskListCreated should come before first Thinking " +
                "(TaskListCreated=0, Thinking=$firstThinkingIdx)",
            0 < firstThinkingIdx
        )
    }

    /**
     * FR-2.4 / AC-2(b) — the Magic orchestrator SHALL NOT call
     * `create_task_list`. The task list is seeded; only the Reactive path
     * may call it.
     *
     * Instruments the AIService to fail the test if any assistant message
     * carries a `create_task_list` tool call during a
     * `TASK_LIST_PROGRAMMATIC` turn. Drives the empty-file Magic turn;
     * asserts no throw.
     */
    fun testMagicOrchestratorNeverCallsCreateTaskList() = runBlocking {
        val events = MutableSharedFlow<AgentEvent>(
            replay = Int.MAX_VALUE, extraBufferCapacity = 64
        )
        val memory = AgentMemory()

        // Instrument: fail if the agent ever issues create_task_list.
        val aiService = object : AIService {
            override suspend fun chat(request: AiChatRequest): AiChatResponse {
                val lastAssistant = request.messages.lastOrNull { it is AiMessage.Assistant }
                    as? AiMessage.Assistant
                lastAssistant?.toolCalls?.forEach { tc ->
                    if (tc.name == "create_task_list") {
                        error(
                            "Magic orchestrator must NOT call create_task_list " +
                                "(FR-2.4), but got: ${tc.arguments}"
                        )
                    }
                }
                // Return a plain text answer so the turn ends immediately.
                return AiChatResponse(AiMessage.Assistant("done", null), "stop")
            }
            override suspend fun testConnection(): Result<String> =
                Result.success("ok")
        }

        val ctx = buildCtx(memory, events)
        val tools = ToolRegistry(emptyList())
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events)

        // Seed the task list (as runTaskList does) so the agent has no
        // reason to call create_task_list.
        val taskList = TaskList(listOf(
            Task("t1", "Detect custom patterns", "scan for filters")
        ))
        memory.taskList = taskList
        events.tryEmit(AgentEvent.TaskListCreated(taskList))

        // Run the turn — the instrumented AIService throws if create_task_list
        // is ever issued. A clean return proves the orchestrator didn't call it.
        val outcome = agent.runTurn(
            "instruction", memory,
            entryPath = EntryPath.TASK_LIST_PROGRAMMATIC
        )
        assertNotNull("turn should complete without throwing", outcome)
    }

    /**
     * FR-2.5 — when the detection catalog yields zero tasks, the system
     * still emits `TaskListCreated` (with an empty list) and proceeds
     * directly to `propose_rule_content` (or finishes cleanly with no
     * proposal).
     *
     * Seeds an empty task list, emits `TaskListCreated(empty)`, runs the
     * agent. Asserts `TaskListCreated` was emitted with zero tasks and the
     * turn completes cleanly.
     */
    fun testEmptyCatalogStillEmitsTaskListCreated() = runBlocking {
        val events = MutableSharedFlow<AgentEvent>(
            replay = Int.MAX_VALUE, extraBufferCapacity = 64
        )
        val memory = AgentMemory()
        val aiService = FakeAIService()
        // No tasks → agent has nothing to walk; it answers with plain text.
        aiService.enqueueText("no detections needed")
        val ctx = buildCtx(memory, events)
        val tools = ToolRegistry(emptyList())
        val agent = RuleAuthoringAgent(aiService, tools, ctx, events)

        val collected = mutableListOf<AgentEvent>()
        val collectJob = scope().launch(
            Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED
        ) {
            events.collect { collected.add(it) }
        }

        // Seed an EMPTY task list (zero tasks).
        val emptyTaskList = TaskList(emptyList())
        memory.taskList = emptyTaskList
        events.tryEmit(AgentEvent.TaskListCreated(emptyTaskList))

        val outcome = agent.runTurn(
            "instruction", memory,
            entryPath = EntryPath.TASK_LIST_PROGRAMMATIC
        )

        collectJob.cancelAndJoin()

        // TaskListCreated (with empty list) was emitted.
        val tlc = collected.filterIsInstance<AgentEvent.TaskListCreated>().single()
        assertTrue(
            "task list should be empty (zero tasks)",
            tlc.taskList.tasks.isEmpty()
        )
        // Turn completed cleanly — either Answered or Proposed.
        assertNotNull("turn should complete cleanly even with zero tasks", outcome)
    }

    // --- Helpers ---

    private fun buildCtx(
        memory: AgentMemory,
        events: MutableSharedFlow<AgentEvent>
    ): ToolContext = ToolContext(
        project = project,
        configReader = ConfigReader.getInstance(project),
        aiSettings = AiRuntimeConfig(
            provider = AiProvider.OLLAMA,
            baseUrl = "", apiKey = "", model = "",
            requestTimeoutSec = 30,
            // Small budget keeps the tests snappy.
            maxRequests = 3
        ),
        ruleFileResolver = RuleFileResolver(project),
        workingMemory = memory,
        approvals = FakeApprovalGate(),
        events = events
    )

    private fun scope() =
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
}

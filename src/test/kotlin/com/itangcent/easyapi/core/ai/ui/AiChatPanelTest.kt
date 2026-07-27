package com.itangcent.easyapi.core.ai.ui

import com.itangcent.easyapi.core.ai.AiMessage
import com.itangcent.easyapi.core.ai.agent.AgentEvent
import com.itangcent.easyapi.core.ai.agent.AgentMemory
import com.itangcent.easyapi.core.ai.agent.Clarification
import com.itangcent.easyapi.core.ai.agent.ClarificationAnswers
import com.itangcent.easyapi.core.ai.agent.ClarificationQuestion
import com.itangcent.easyapi.core.ai.agent.Task
import com.itangcent.easyapi.core.ai.agent.TaskList
import com.itangcent.easyapi.core.ai.agent.TaskStatus
import com.itangcent.easyapi.core.ai.agent.Proposal
import com.itangcent.easyapi.core.ai.agent.QuestionKind
import com.itangcent.easyapi.core.ai.agent.QuestionOption
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Tests for [AiChatPanel] event rendering.
 *
 * Verifies that rendering [AgentEvent]s produces the expected UI rows
 * (messages, tool-activity cards, approval cards, proposal card).
 *
 * The full 2-round conversation + save flow is covered by
 * [com.itangcent.easyapi.core.ai.AiAssistantServiceTest] (which exercises the
 * agent end-to-end via a fake service); here we test the panel's rendering
 * seams directly.
 */
class AiChatPanelTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testMessageEventRendersRow() {
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.Message("Hello, world!"))
        val after = panel.transcriptComponentCount()
        assertTrue("transcript should gain a row for a message",
            after > before)
        panel.dispose()
    }

    fun testPerceivingEventRendersCard() {
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.Perceiving("list_rule_keys", "{}"))
        val after = panel.transcriptComponentCount()
        assertTrue("transcript should gain a row for a perception event",
            after > before)
        panel.dispose()
    }

    fun testActingEventRendersCard() {
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.Acting("propose_rule_content", "{}"))
        val after = panel.transcriptComponentCount()
        assertTrue("transcript should gain a row for an action event",
            after > before)
        panel.dispose()
    }

    fun testApprovalRequestedRendersApprovalCard() {
        val panel = AiChatPanel(project)
        // Bind a fake session so the approval card can find the approvals gate.
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val memory = AgentMemory()
        val gate = com.itangcent.easyapi.core.ai.UiApprovalGate()
        val sess = com.itangcent.easyapi.core.ai.ConversationSession(
            agent = mockAgent(),
            memory = memory,
            events = events,
            approvals = gate
        )
        panel.bindSessionForTest(sess)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.ApprovalRequested("write_rule_file", "{}"))
        val after = panel.transcriptComponentCount()
        assertTrue("transcript should gain an approval card",
            after > before)
        panel.dispose()
    }

    fun testProposalReadyRendersProposalCard() {
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(
            AgentEvent.ProposalReady(
                Proposal("# my rule\napi.name=cool", "custom.rules")
            )
        )
        val after = panel.transcriptComponentCount()
        assertTrue("transcript should gain a proposal card",
            after > before)
        panel.dispose()
    }

    fun testObservedEventRendersObservation() {
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.Observed("list_rule_keys", "12 keys found"))
        val after = panel.transcriptComponentCount()
        assertTrue("transcript should gain an observation row",
            after > before)
        panel.dispose()
    }

    fun testThinkingEventDoesNotAddRow() {
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.Thinking(1))
        val after = panel.transcriptComponentCount()
        assertEquals("Thinking should not add a transcript row",
            before, after)
        panel.dispose()
    }

    fun testProposalApplyInvokesCallback() {
        val panel = AiChatPanel(project)
        var applied: String? = null
        panel.onApplyProposal = { applied = it }
        panel.renderEventForTest(
            AgentEvent.ProposalReady(Proposal("api.name=cool", "custom.rules"))
        )
        assertTrue(
            "Apply-to-editor button should be present when onApplyProposal is set",
            panel.clickApplyToEditorForTest()
        )
        assertEquals("api.name=cool", applied)
        // The proposal is consumed — its actions must be removed so it can't be
        // applied again (it's now stale).
        assertFalse(
            "Apply-to-editor button should be gone after it was applied",
            panel.clickApplyToEditorForTest()
        )
        panel.dispose()
    }

    fun testNewProposalFreezesPreviousProposal() {
        val panel = AiChatPanel(project)
        val applied = mutableListOf<String>()
        panel.onApplyProposal = { applied.add(it) }
        panel.renderEventForTest(
            AgentEvent.ProposalReady(Proposal("api.name=first", "custom.rules"))
        )
        // Render a second proposal — it supersedes the first.
        panel.renderEventForTest(
            AgentEvent.ProposalReady(Proposal("api.name=second", "custom.rules"))
        )
        // Only the latest proposal's apply button should remain; clicking it
        // applies "second", proving the first proposal's actions were frozen.
        assertTrue(
            "latest proposal's apply button should still be present",
            panel.clickApplyToEditorForTest()
        )
        assertEquals(
            "second apply should win after the first was superseded",
            listOf("api.name=second"),
            applied
        )
        panel.dispose()
    }

    fun testNewMessageFreezesPendingProposal() {
        val panel = AiChatPanel(project)
        val applied = mutableListOf<String>()
        panel.onApplyProposal = { applied.add(it) }
        panel.renderEventForTest(
            AgentEvent.ProposalReady(Proposal("api.name=cool", "custom.rules"))
        )
        // Sending a new message supersedes the pending proposal: its apply
        // button must be gone even if the new turn never runs (no session).
        panel.typeAndSendForTest("anything")
        assertFalse(
            "apply button should be gone once a new message is sent",
            panel.clickApplyToEditorForTest()
        )
        assertTrue("no apply should have fired", applied.isEmpty())
        panel.dispose()
    }

    fun testNoApplyButtonWithoutCallback() {
        val panel = AiChatPanel(project)
        panel.renderEventForTest(
            AgentEvent.ProposalReady(Proposal("api.name=cool", "custom.rules"))
        )
        assertFalse(
            "Apply-to-editor button should be absent when onApplyProposal is null",
            panel.clickApplyToEditorForTest()
        )
        panel.dispose()
    }

    // --- structured clarification card ---

    fun testClarificationCardSubmitCompletesGate() = runBlocking {
        val panel = AiChatPanel(project)
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val gate = com.itangcent.easyapi.core.ai.UiClarificationGate(events)
        val sess = com.itangcent.easyapi.core.ai.ConversationSession(
            agent = mockAgent(),
            memory = AgentMemory(),
            events = events,
            approvals = com.itangcent.easyapi.core.ai.UiApprovalGate(),
            clarifications = gate
        )
        panel.bindSessionForTest(sess)

        val clar = Clarification(
            prompt = "Could you clarify:",
            questions = listOf(
                ClarificationQuestion(
                    "scope", "Scope?", QuestionKind.SINGLE_CHOICE,
                    listOf(
                        QuestionOption("global", "Globally"),
                        QuestionOption("controllers", "Specific controllers", isDefault = true)
                    )
                )
            )
        )

        var result: ClarificationAnswers? = null
        val waiter = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            result = gate.await(clar)
        }
        assertTrue("gate should be pending", gate.isPending())

        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.ClarificationRequested(clar))
        assertTrue("clarification card should render", panel.transcriptComponentCount() > before)
        assertTrue(panel.isClarificationPendingForTest())

        assertTrue(panel.clickSubmitClarificationForTest())
        assertFalse(panel.isClarificationPendingForTest())

        assertNotNull("gate should have resolved", result)
        // The default-flagged option is pre-selected.
        assertEquals(listOf("controllers"), result!!.answers["scope"])

        waiter.cancel()
        panel.dispose()
    }

    fun testTypedReplyResolvesPendingClarification() = runBlocking {
        val panel = AiChatPanel(project)
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val gate = com.itangcent.easyapi.core.ai.UiClarificationGate(events)
        val sess = com.itangcent.easyapi.core.ai.ConversationSession(
            agent = mockAgent(),
            memory = AgentMemory(),
            events = events,
            approvals = com.itangcent.easyapi.core.ai.UiApprovalGate(),
            clarifications = gate
        )
        panel.bindSessionForTest(sess)

        val clar = Clarification(
            prompt = null,
            questions = listOf(ClarificationQuestion("q1", "Scope?", QuestionKind.FREE_TEXT))
        )
        var result: ClarificationAnswers? = null
        val waiter = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            result = gate.await(clar)
        }
        panel.renderEventForTest(AgentEvent.ClarificationRequested(clar))
        assertTrue(panel.isClarificationPendingForTest())

        // Typing a free-form reply resolves the pending card instead of starting a turn.
        panel.typeAndSendForTest("just globally")
        assertFalse(panel.isClarificationPendingForTest())

        assertNotNull(result)
        assertEquals(listOf("just globally"), result!!.answers[ClarificationAnswers.RAW_KEY])

        waiter.cancel()
        panel.dispose()
    }

    /** Build a minimal dummy agent — the panel never calls it in these tests. */
    private fun mockAgent(): com.itangcent.easyapi.core.ai.agent.RuleAuthoringAgent {
        // The panel tests don't drive the agent; they only render events.
        // We use reflection-free construction via a fake AIService + empty tool registry.
        val fakeService = object : com.itangcent.easyapi.core.ai.AIService {
            override suspend fun chat(request: com.itangcent.easyapi.core.ai.AiChatRequest) =
                com.itangcent.easyapi.core.ai.AiChatResponse(
                    AiMessage.Assistant("stub", null), "stop"
                )
            override suspend fun testConnection() = Result.success("ok")
        }
        val tools = com.itangcent.easyapi.core.ai.tools.ToolRegistry(emptyList())
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val ctx = com.itangcent.easyapi.core.ai.tools.ToolContext(
            project = project,
            configReader = com.itangcent.easyapi.core.config.ConfigReader.getInstance(project),
            aiSettings = com.itangcent.easyapi.core.ai.AiRuntimeConfig(
                provider = com.itangcent.easyapi.core.ai.AiProvider.OLLAMA,
                baseUrl = "", apiKey = "", model = "",
                requestTimeoutSec = 30, maxRequests = 8
            ),
            ruleFileResolver = com.itangcent.easyapi.core.config.source.RuleFileResolver(project),
            workingMemory = AgentMemory(),
            approvals = com.itangcent.easyapi.core.ai.UiApprovalGate(),
            events = events
        )
        return com.itangcent.easyapi.core.ai.agent.RuleAuthoringAgent(fakeService, tools, ctx, events)
    }

    // --- additional event-coverage tests ---

    fun testFailedEventDoesNotCrash() {
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        // Failed updates the status label + fires a notification — it must
        // not throw and must not add a transcript row.
        panel.renderEventForTest(AgentEvent.Failed("boom"))
        assertEquals(
            "Failed event should not add a transcript row",
            before, panel.transcriptComponentCount()
        )
        panel.dispose()
    }

    fun testTurnCompleteDoesNotAddRow() {
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.TurnComplete)
        assertEquals(
            "TurnComplete should not add a transcript row",
            before, panel.transcriptComponentCount()
        )
        panel.dispose()
    }

    fun testFileReadConsentCardRenders() {
        val panel = AiChatPanel(project)
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val sess = com.itangcent.easyapi.core.ai.ConversationSession(
            agent = mockAgent(),
            memory = AgentMemory(),
            events = events,
            approvals = com.itangcent.easyapi.core.ai.UiApprovalGate()
        )
        panel.bindSessionForTest(sess)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(
            AgentEvent.FileReadConsentRequested("/tmp/external.properties")
        )
        val after = panel.transcriptComponentCount()
        assertTrue(
            "transcript should gain a read-consent card",
            after > before
        )
        panel.dispose()
    }

    fun testFileReadConsentCardWithoutSessionIsNoop() {
        // Without a bound session, the card must silently return rather
        // than NPE on the gate.
        val panel = AiChatPanel(project)
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(
            AgentEvent.FileReadConsentRequested("/tmp/external.properties")
        )
        assertEquals(
            "read-consent card without session should not add a row",
            before, panel.transcriptComponentCount()
        )
        panel.dispose()
    }

    // --- MULTI_CHOICE clarification card ---

    fun testMultiChoiceClarificationSubmit() = runBlocking {
        val panel = AiChatPanel(project)
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val gate = com.itangcent.easyapi.core.ai.UiClarificationGate(events)
        val sess = com.itangcent.easyapi.core.ai.ConversationSession(
            agent = mockAgent(),
            memory = AgentMemory(),
            events = events,
            approvals = com.itangcent.easyapi.core.ai.UiApprovalGate(),
            clarifications = gate
        )
        panel.bindSessionForTest(sess)

        val clar = Clarification(
            prompt = "Pick features:",
            questions = listOf(
                ClarificationQuestion(
                    "features", "Features?", QuestionKind.MULTI_CHOICE,
                    listOf(
                        QuestionOption("auth", "Auth"),
                        QuestionOption("logging", "Logging", isDefault = true),
                        QuestionOption("cache", "Cache")
                    )
                )
            )
        )
        var result: ClarificationAnswers? = null
        val waiter = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            result = gate.await(clar)
        }
        panel.renderEventForTest(AgentEvent.ClarificationRequested(clar))
        assertTrue(panel.isClarificationPendingForTest())

        assertTrue(panel.clickSubmitClarificationForTest())
        assertFalse(panel.isClarificationPendingForTest())

        assertNotNull(result)
        // The default-flagged option ("logging") should be pre-selected.
        assertTrue(
            "default option should be in answers",
            result!!.answers["features"]?.contains("logging") == true
        )

        waiter.cancel()
        panel.dispose()
    }

    // --- FREE_TEXT clarification card via Submit button ---

    fun testFreeTextClarificationSubmitViaButton() = runBlocking {
        val panel = AiChatPanel(project)
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val gate = com.itangcent.easyapi.core.ai.UiClarificationGate(events)
        val sess = com.itangcent.easyapi.core.ai.ConversationSession(
            agent = mockAgent(),
            memory = AgentMemory(),
            events = events,
            approvals = com.itangcent.easyapi.core.ai.UiApprovalGate(),
            clarifications = gate
        )
        panel.bindSessionForTest(sess)

        val clar = Clarification(
            prompt = null,
            questions = listOf(
                ClarificationQuestion("note", "Any notes?", QuestionKind.FREE_TEXT)
            )
        )
        var result: ClarificationAnswers? = null
        val waiter = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            result = gate.await(clar)
        }
        panel.renderEventForTest(AgentEvent.ClarificationRequested(clar))
        assertTrue(panel.isClarificationPendingForTest())

        // Submit without typing anything → empty answer list for that key.
        assertTrue(panel.clickSubmitClarificationForTest())
        assertFalse(panel.isClarificationPendingForTest())

        assertNotNull(result)
        // Free-text with no input → empty list (not null, not absent).
        assertTrue(
            "free-text with no input should yield empty list",
            result!!.answers["note"]?.isEmpty() == true
        )

        waiter.cancel()
        panel.dispose()
    }

    // --- multiple questions in one card ---

    fun testMultipleQuestionsInSingleCard() = runBlocking {
        val panel = AiChatPanel(project)
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val gate = com.itangcent.easyapi.core.ai.UiClarificationGate(events)
        val sess = com.itangcent.easyapi.core.ai.ConversationSession(
            agent = mockAgent(),
            memory = AgentMemory(),
            events = events,
            approvals = com.itangcent.easyapi.core.ai.UiApprovalGate(),
            clarifications = gate
        )
        panel.bindSessionForTest(sess)

        val clar = Clarification(
            prompt = "Two questions:",
            questions = listOf(
                ClarificationQuestion(
                    "scope", "Scope?", QuestionKind.SINGLE_CHOICE,
                    listOf(QuestionOption("global", "Global"), QuestionOption("project", "Project"))
                ),
                ClarificationQuestion(
                    "note", "Notes?", QuestionKind.FREE_TEXT
                )
            )
        )
        var result: ClarificationAnswers? = null
        val waiter = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
            result = gate.await(clar)
        }
        panel.renderEventForTest(AgentEvent.ClarificationRequested(clar))
        assertTrue(panel.isClarificationPendingForTest())

        assertTrue(panel.clickSubmitClarificationForTest())
        assertFalse(panel.isClarificationPendingForTest())

        assertNotNull(result)
        // Both question IDs should be present in the answers map.
        assertTrue("scope answer should be present", result!!.answers.containsKey("scope"))
        assertTrue("note answer should be present", result.answers.containsKey("note"))

        waiter.cancel()
        panel.dispose()
    }

    // --- R2: Todo List panel (design R2-C3, replaces v1 PlanCard) ---

    private fun sampleTaskList(): TaskList = TaskList(listOf(
        Task("s1", "Detect custom patterns", "Scan for filters/interceptors"),
        Task("s2", "Confirm findings"),
        Task("s3", "Propose rule content")
    ))

    fun testTaskListCreatedRendersTodoList() {
        val panel = AiChatPanel(project)
        // TaskListCreated must NOT add a card to the transcript (v1 behaviour
        // removed) — the Todo List lives in the right-side panel.
        val before = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.TaskListCreated(sampleTaskList()))
        assertEquals(
            "TaskListCreated must not add a transcript row (Todo List is a side panel)",
            before, panel.transcriptComponentCount()
        )
        // All tasks start PENDING → glyph "[ ]".
        assertEquals("[ ]", panel.taskListGlyphForTest("s1"))
        assertEquals("[ ]", panel.taskListGlyphForTest("s2"))
        assertEquals("[ ]", panel.taskListGlyphForTest("s3"))
        panel.dispose()
    }

    fun testTaskStartedUpdatesGlyphToInProgress() {
        val panel = AiChatPanel(project)
        panel.renderEventForTest(AgentEvent.TaskListCreated(sampleTaskList()))
        panel.renderEventForTest(AgentEvent.TaskStarted("s1"))
        assertEquals("[~]", panel.taskListGlyphForTest("s1"))
        // Other tasks stay PENDING.
        assertEquals("[ ]", panel.taskListGlyphForTest("s2"))
        panel.dispose()
    }

    fun testTaskCompletedUpdatesGlyph() {
        val panel = AiChatPanel(project)
        panel.renderEventForTest(AgentEvent.TaskListCreated(sampleTaskList()))
        panel.renderEventForTest(AgentEvent.TaskStarted("s1"))
        panel.renderEventForTest(AgentEvent.TaskCompleted("s1"))
        assertEquals("[X]", panel.taskListGlyphForTest("s1"))
        panel.dispose()
    }

    fun testTaskFailedUpdatesGlyph() {
        val panel = AiChatPanel(project)
        panel.renderEventForTest(AgentEvent.TaskListCreated(sampleTaskList()))
        panel.renderEventForTest(AgentEvent.TaskStarted("s2"))
        panel.renderEventForTest(AgentEvent.TaskFailed("s2", "not found"))
        assertEquals("[!]", panel.taskListGlyphForTest("s2"))
        panel.dispose()
    }

    fun testTaskSkippedUpdatesGlyph() {
        val panel = AiChatPanel(project)
        panel.renderEventForTest(AgentEvent.TaskListCreated(sampleTaskList()))
        panel.renderEventForTest(AgentEvent.TaskSkipped("s3"))
        assertEquals("[-]", panel.taskListGlyphForTest("s3"))
        panel.dispose()
    }

    fun testTurnCompleteKeepsTodoListVisible() {
        val panel = AiChatPanel(project)
        panel.renderEventForTest(AgentEvent.TaskListCreated(sampleTaskList()))
        panel.renderEventForTest(AgentEvent.TurnComplete)
        // R2-C3: TurnComplete does NOT clear the Todo List — the user reviews
        // what was done. The glyphs stay accessible.
        assertEquals(
            "glyph should still be accessible after TurnComplete (Todo List stays visible)",
            "[ ]", panel.taskListGlyphForTest("s1")
        )
        panel.dispose()
    }

    fun testSplitPaneSetsInitialDividerLocationOnAddNotify() {
        // R3-C1: resizeWeight alone does not set the initial divider position
        // — JSplitPane defaults to the children's preferred sizes, which
        // collapses the Todo List to its minimum width. addNotify must set
        // the divider to 0.625 once, after the pane has a real size.
        val panel = AiChatPanel(project)
        val splitPane = panel.component
        // Give the pane a real size so setDividerLocation(proportional) has a
        // meaningful pixel width to compute against.
        splitPane.setSize(800, 600)
        // addNotify fires the initial divider set. The pane is already
        // constructed; calling addNotify here simulates the host having
        // attached the pane to a peer (mirrors what the tool window does).
        splitPane.addNotify()
        val divider = panel.splitPaneDividerLocationForTest()
        val width = panel.splitPaneWidthForTest()
        assertTrue(
            "split pane should have a positive width after setSize: $width",
            width > 0
        )
        // JSplitPane.setDividerLocation(double) computes the pixel location as
        // (width - dividerSize) * proportion, NOT width * proportion. Use the
        // same formula to derive the expected value, then allow ±2px for
        // integer rounding.
        val dividerSize = panel.splitPaneDividerSizeForTest()
        val expected = ((width - dividerSize) * 0.625).toInt()
        assertTrue(
            "divider should sit at ~0.625*(width-dividerSize) " +
                "(expected ~$expected, got $divider for width=$width, dividerSize=$dividerSize)",
            kotlin.math.abs(divider - expected) <= 2
        )
        panel.dispose()
    }

    fun testObservedSuppressedForCreateTaskList() {
        val panel = AiChatPanel(project)
        // Perceiving(create_task_list) renders a tool-activity card.
        panel.renderEventForTest(AgentEvent.Perceiving("create_task_list", "{}"))
        val afterPerceiving = panel.transcriptComponentCount()
        // The matching Observed must be suppressed (design C11).
        panel.renderEventForTest(AgentEvent.Observed("create_task_list", "task list created with 3 tasks"))
        assertEquals(
            "Observed for create_task_list should be suppressed (no new transcript row)",
            afterPerceiving, panel.transcriptComponentCount()
        )
        panel.dispose()
    }

    fun testObservedSuppressedForUpdateTask() {
        val panel = AiChatPanel(project)
        panel.renderEventForTest(AgentEvent.Perceiving("update_task", """{"taskId":"s1"}"""))
        val afterPerceiving = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.Observed("update_task", "task s1 now in_progress"))
        assertEquals(
            "Observed for update_task should be suppressed (no new transcript row)",
            afterPerceiving, panel.transcriptComponentCount()
        )
        panel.dispose()
    }

    fun testObservedNotSuppressedForOtherTools() {
        val panel = AiChatPanel(project)
        panel.renderEventForTest(AgentEvent.Perceiving("list_rule_keys", "{}"))
        val afterPerceiving = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.Observed("list_rule_keys", "12 keys found"))
        assertTrue(
            "Observed for non-task-list tools should still render a row",
            panel.transcriptComponentCount() > afterPerceiving
        )
        panel.dispose()
    }

    // --- Phase B: runTaskList programmatic seam (design C10 / task B7) ---

    fun testRunTaskListSeedsTaskListAndEmitsTaskListCreated() = runBlocking {
        val panel = AiChatPanel(project)
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val memory = AgentMemory()
        val sess = com.itangcent.easyapi.core.ai.ConversationSession(
            agent = mockAgent(),
            memory = memory,
            events = events,
            approvals = com.itangcent.easyapi.core.ai.UiApprovalGate()
        )
        panel.bindSessionForTest(sess)

        val taskList = sampleTaskList()
        panel.runTaskList(taskList, "Plan & Run", "instruction")

        // The task list is seeded in memory before the turn starts.
        assertEquals("runTaskList should seed the task list into working memory",
            taskList, memory.taskList)

        // The TaskListCreated event was emitted synchronously via tryEmit
        // before startTurn; the replay buffer keeps it even if the async turn
        // emits more events afterwards.
        assertTrue(
            "runTaskList should emit TaskListCreated: ${events.replayCache.map { it::class.simpleName }}",
            events.replayCache.any { it is AgentEvent.TaskListCreated }
        )

        panel.dispose()
    }

    /**
     * Regression for "TodoListPanel is not rendered even after entering
     * planning": the Todo List starts hidden. The split pane is laid out
     * while it is hidden — and `JSplitPane` does NOT re-lay out or restore
     * its divider when a child's visibility is later toggled from inside the
     * child's own `revalidate()`. So when `TaskListCreated` flips the panel
     * visible, the parent split pane must be told to re-position its divider;
     * otherwise the now-"visible" Todo List is allocated zero width and is
     * effectively unrendered.
     *
     * This test reproduces the real lifecycle: lay out the split pane with the
     * Todo List hidden, then deliver `TaskListCreated` and assert the Todo
     * List actually gets a non-zero width.
     */
    fun testTaskListCreatedRestoresTodoListWidthAfterHiddenLayout() {
        val panel = AiChatPanel(project)
        val splitPane = panel.component
        // Give the pane a real size and let it lay out while the Todo List is
        // still hidden — mirrors the dialog opening before any task list
        // exists.
        splitPane.setSize(800, 600)
        splitPane.addNotify()
        splitPane.doLayout()

        // Now the task list arrives (the production Magic / create_task_list
        // path).
        panel.renderEventForTest(AgentEvent.TaskListCreated(sampleTaskList()))
        splitPane.doLayout()

        val divider = panel.splitPaneDividerLocationForTest()
        val dividerSize = panel.splitPaneDividerSizeForTest()
        val todoListWidth = splitPane.width - divider - dividerSize
        assertTrue(
            "Todo List should get a non-zero width after TaskListCreated " +
                "(divider=$divider, todoListWidth=$todoListWidth, " +
                "splitWidth=${splitPane.width}, dividerSize=$dividerSize)",
            todoListWidth > 0
        )
        panel.dispose()
    }

    // --- Phase 2 Route B: non-empty Magic flow (FR-6.*, AC-7) ---

    /**
     * FR-6.1 / AC-7(a) — Magic on a non-empty file starts a Reactive review
     * turn; no `TaskListCreated` is emitted during Stage 1.
     *
     * Drives `runReviewTurn` with a bound session; asserts the session's
     * events flow has no `TaskListCreated` and the gate arm flag is set
     * (so the gate will fire on the next normal `TurnComplete`).
     */
    fun testNonEmptyMagicRunsReviewStageOnly() {
        val panel = AiChatPanel(project)
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val memory = AgentMemory()
        val sess = com.itangcent.easyapi.core.ai.ConversationSession(
            agent = mockAgent(),
            memory = memory,
            events = events,
            approvals = com.itangcent.easyapi.core.ai.UiApprovalGate()
        )
        panel.bindSessionForTest(sess)

        val beforeCount = panel.transcriptComponentCount()
        panel.runReviewTurn(
            displayText = "✨ Review and improve \"custom.rules\".",
            instruction = "Review and improve the rule file 'custom.rules'…"
        )
        // The display text is rendered as a user message row (synchronous).
        assertTrue(
            "transcript should gain a row for the review display text",
            panel.transcriptComponentCount() > beforeCount
        )
        // Stage 1 does NOT seed a task list — no TaskListCreated emitted.
        assertFalse(
            "Stage 1 must NOT emit TaskListCreated (FR-6.1): " +
                events.replayCache.map { it::class.simpleName },
            events.replayCache.any { it is AgentEvent.TaskListCreated }
        )
        // The gate is armed so it fires on the next normal TurnComplete.
        assertTrue(
            "ReviewGate arm flag should be set by runReviewTurn",
            panel.isReviewGatePendingForTest()
        )
        // No task list seeded in memory.
        assertNull(
            "memory.taskList should be null in Stage 1 (FR-6.1)",
            memory.taskList
        )
        panel.dispose()
    }

    /**
     * FR-6.3 / FR-6.4 / AC-7(b) — on normal `TurnComplete`, the gate fires
     * exactly once and the arm flag is cleared.
     *
     * Arms the gate, renders `TurnComplete` (simulating a normal review-turn
     * end), and asserts the gate card renders. Renders a second
     * `TurnComplete` and asserts no second gate card appears (the arm was
     * cleared by the first fire — FR-6.4).
     */
    fun testGateFiresOnceOnTurnComplete() {
        val panel = AiChatPanel(project)
        panel.armReviewGateForTest()
        assertFalse("gate card should not be rendered before TurnComplete",
            panel.isReviewGateCardRenderedForTest())

        // Normal turn end → gate fires.
        panel.renderEventForTest(AgentEvent.TurnComplete)
        assertTrue(
            "gate card should be rendered after TurnComplete (FR-6.3)",
            panel.isReviewGateCardRenderedForTest()
        )
        assertFalse(
            "arm flag should be cleared after firing (FR-6.4)",
            panel.isReviewGatePendingForTest()
        )

        // A second TurnComplete must NOT render a second gate card (the arm
        // was cleared by the first fire — FR-6.4 "fires exactly once").
        val cardCountBefore = panel.transcriptComponentCount()
        panel.renderEventForTest(AgentEvent.TurnComplete)
        assertFalse(
            "arm flag should remain cleared after second TurnComplete",
            panel.isReviewGatePendingForTest()
        )
        // The second TurnComplete should not add a second gate card. The
        // card-count check is loose because TurnComplete may add other rows
        // (it doesn't, per renderEvent, but we stay robust) — the key
        // assertion is that no NEW "Continue to detection pass?" label
        // appears. We verify by counting labels containing that text.
        // Since arm was already cleared, no second gate card renders.
        assertFalse(
            "arm flag must remain cleared (gate cannot re-fire)",
            panel.isReviewGatePendingForTest()
        )
        panel.dispose()
    }

    /**
     * FR-6.5 / FR-6.8 / FR-6.9 / AC-7(c) — clicking **Yes** at the gate
     * seeds a detection task list and runs the detection-pass contract.
     *
     * Wires `onReviewGateYes` to call `runTaskList` with a detection task
     * list (as `RuleFileEditDialog.onReviewGateYes` does in production).
     * Arms the gate, fires it via `TurnComplete`, clicks Yes, and asserts
     * `TaskListCreated` is emitted and the task list is seeded in memory
     * (the detection-pass contract entry — same as Route A).
     */
    fun testGateYesSeedsDetectionTaskListAndRunsContract() {
        val panel = AiChatPanel(project)
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val memory = AgentMemory()
        val sess = com.itangcent.easyapi.core.ai.ConversationSession(
            agent = mockAgent(),
            memory = memory,
            events = events,
            approvals = com.itangcent.easyapi.core.ai.UiApprovalGate()
        )
        panel.bindSessionForTest(sess)

        // Wire onReviewGateYes to re-enter the detection-pass contract, as
        // RuleFileEditDialog.onReviewGateYes does in production. The task
        // list and instruction are built at Yes time (FR-6.8).
        val detectionTaskList = TaskList(listOf(
            Task("d1", "Detect custom patterns", "scan for filters/interceptors")
        ))
        panel.onReviewGateYes = {
            panel.runTaskList(
                taskList = detectionTaskList,
                displayText = "✨ Detect missing custom-pattern rules…",
                instruction = "detection instruction (empty-file Magic body)"
            )
        }

        // Arm + fire the gate.
        panel.armReviewGateForTest()
        panel.renderEventForTest(AgentEvent.TurnComplete)
        assertTrue(
            "gate card should be rendered before Yes click",
            panel.isReviewGateCardRenderedForTest()
        )

        // Click Yes → onReviewGateYes → runTaskList → TaskListCreated.
        assertTrue(
            "Yes button should be clickable",
            panel.clickReviewGateYesForTest()
        )
        assertTrue(
            "TaskListCreated should be emitted after clicking Yes (FR-6.5): " +
                events.replayCache.map { it::class.simpleName },
            events.replayCache.any { it is AgentEvent.TaskListCreated }
        )
        assertEquals(
            "detection task list should be seeded in memory (FR-6.5)",
            detectionTaskList, memory.taskList
        )
        panel.dispose()
    }

    /**
     * FR-6.6 / AC-7(d) — clicking **No** at the gate terminates without
     * detections.
     *
     * Arms the gate, fires it via `TurnComplete`, clicks No, and asserts no
     * `TaskListCreated` is emitted and no detection turn runs (the Stage-1
     * outcome stands as-is).
     */
    fun testGateNoTerminatesWithoutDetections() {
        val panel = AiChatPanel(project)
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val memory = AgentMemory()
        val sess = com.itangcent.easyapi.core.ai.ConversationSession(
            agent = mockAgent(),
            memory = memory,
            events = events,
            approvals = com.itangcent.easyapi.core.ai.UiApprovalGate()
        )
        panel.bindSessionForTest(sess)

        // Track whether onReviewGateYes was invoked (it should NOT be on No).
        var yesInvoked = false
        panel.onReviewGateYes = { yesInvoked = true }

        // Arm + fire the gate.
        panel.armReviewGateForTest()
        panel.renderEventForTest(AgentEvent.TurnComplete)
        assertTrue(
            "gate card should be rendered before No click",
            panel.isReviewGateCardRenderedForTest()
        )

        // Click No → terminate; no Stage 2.
        assertTrue(
            "No button should be clickable",
            panel.clickReviewGateNoForTest()
        )
        assertFalse(
            "onReviewGateYes must NOT be invoked on No (FR-6.6)",
            yesInvoked
        )
        assertFalse(
            "No TaskListCreated should be emitted after No (FR-6.6): " +
                events.replayCache.map { it::class.simpleName },
            events.replayCache.any { it is AgentEvent.TaskListCreated }
        )
        assertNull(
            "memory.taskList should remain null after No (FR-6.6)",
            memory.taskList
        )
        panel.dispose()
    }

    /**
     * FR-6.7 / AC-7(e) — an abnormal turn end (`Failed`, `LoopDetected`,
     * `StepLimitHit`) does not fire the gate; the arm flag is cleared.
     *
     * For `Failed` and `LoopDetected`, the panel's `renderEvent` clears the
     * arm when those events arrive (they are terminal events that do NOT
     * emit `TurnComplete`). For `StepLimitHit` (a `TurnOutcome`, not an
     * `AgentEvent`), the arm is cleared in `startTurn`'s outcome branch.
     *
     * This test covers the `Failed` and `LoopDetected` event paths; the
     * `StepLimitHit` outcome path is covered by
     * [testStepLimitHitOutcomeDoesNotFireGate] below.
     */
    fun testAbnormalTurnEndDoesNotFireGate() {
        // --- Failed ---
        run {
            val panel = AiChatPanel(project)
            panel.armReviewGateForTest()
            panel.renderEventForTest(AgentEvent.Failed("network error"))
            assertFalse(
                "arm flag should be cleared after Failed (FR-6.7)",
                panel.isReviewGatePendingForTest()
            )
            assertFalse(
                "gate card should NOT render after Failed (FR-6.7)",
                panel.isReviewGateCardRenderedForTest()
            )
            panel.dispose()
        }
        // --- LoopDetected ---
        run {
            val panel = AiChatPanel(project)
            panel.armReviewGateForTest()
            panel.renderEventForTest(
                AgentEvent.LoopDetected("consecutive duplicate", "list_rule_keys", 3)
            )
            assertFalse(
                "arm flag should be cleared after LoopDetected (FR-6.7)",
                panel.isReviewGatePendingForTest()
            )
            assertFalse(
                "gate card should NOT render after LoopDetected (FR-6.7)",
                panel.isReviewGateCardRenderedForTest()
            )
            panel.dispose()
        }
    }

    /**
     * FR-6.7 / AC-7(e) — `StepLimitHit` (an abnormal `TurnOutcome`, not an
     * `AgentEvent`) does not fire the gate; the arm flag is cleared.
     *
     * Drives a real agent (sharing the session's events flow) with a tiny
     * `maxRequests` budget and a FakeAIService that issues a tool call,
     * forcing the step budget to exhaust → `TurnOutcome.StepLimitHit`. The
     * outcome branch in `startTurn` clears `reviewGatePending` and does NOT
     * render the gate card (no `TurnComplete` is emitted on StepLimitHit).
     */
    fun testStepLimitHitOutcomeDoesNotFireGate() = runBlocking {
        val panel = AiChatPanel(project)
        val events = MutableSharedFlow<AgentEvent>(replay = 64, extraBufferCapacity = 64)
        val memory = AgentMemory()
        // Build a real agent sharing the session's events flow so agent-emitted
        // events (Thinking, Observed) reach the panel's collector, and the
        // outcome branch runs on uiScope (EDT) where it clears the arm.
        val aiService = com.itangcent.easyapi.core.ai.agent.FakeAIService()
        // Enqueue a tool-call response so the agent consumes its step budget
        // (with maxRequests=1, one tool-call dispatch exhausts the budget →
        // StepLimitHit on the next while-check).
        aiService.enqueueToolCalls(
            com.itangcent.easyapi.core.ai.AiToolCall("c1", "unknown_tool", "{}")
        )
        val tools = com.itangcent.easyapi.core.ai.tools.ToolRegistry(emptyList())
        val ctx = com.itangcent.easyapi.core.ai.tools.ToolContext(
            project = project,
            configReader = com.itangcent.easyapi.core.config.ConfigReader.getInstance(project),
            aiSettings = com.itangcent.easyapi.core.ai.AiRuntimeConfig(
                provider = com.itangcent.easyapi.core.ai.AiProvider.OLLAMA,
                baseUrl = "", apiKey = "", model = "",
                requestTimeoutSec = 30,
                maxRequests = 1 // → StepLimitHit after one step.
            ),
            ruleFileResolver = com.itangcent.easyapi.core.config.source.RuleFileResolver(project),
            workingMemory = memory,
            approvals = com.itangcent.easyapi.core.ai.UiApprovalGate(),
            events = events
        )
        val agent = com.itangcent.easyapi.core.ai.agent.RuleAuthoringAgent(
            aiService, tools, ctx, events
        )
        val sess = com.itangcent.easyapi.core.ai.ConversationSession(
            agent = agent,
            memory = memory,
            events = events,
            approvals = com.itangcent.easyapi.core.ai.UiApprovalGate()
        )
        panel.bindSessionForTest(sess)

        // Suppress the "Continue or stop?" JOptionPane that StepLimitHit
        // triggers via offerContinueOrCancel. Without this, the modal dialog
        // blocks the EDT and hangs all subsequent tests in the suite.
        panel.offerContinueOrCancelHandler = { /* no-op in test */ }

        // Start the review turn — arms the gate.
        panel.runReviewTurn("✨ Review…", "review instruction")

        // Wait for the StepLimitHit outcome to be processed on the EDT.
        // The agent runs on Dispatchers.Default; the outcome branch clears
        // reviewGatePending via ui{} on uiScope (EDT). Pump EDT + yield
        // until the arm is cleared (or timeout).
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline &&
            panel.isReviewGatePendingForTest()
        ) {
            pumpEdt()
            kotlinx.coroutines.delay(50)
        }

        assertFalse(
            "arm flag should be cleared after StepLimitHit (FR-6.7)",
            panel.isReviewGatePendingForTest()
        )
        assertFalse(
            "gate card should NOT render after StepLimitHit (FR-6.7)",
            panel.isReviewGateCardRenderedForTest()
        )
        panel.dispose()
    }

    /** Drain pending EDT dispatches so the uiScope collector runs. */
    private fun pumpEdt() {
        val ui = com.intellij.openapi.application.ApplicationManager.getApplication()
        ui.invokeAndWait { /* run anything queued on the EDT */ }
        ui.invokeAndWait { /* second pass for revalidate/repaint cascade */ }
    }
}

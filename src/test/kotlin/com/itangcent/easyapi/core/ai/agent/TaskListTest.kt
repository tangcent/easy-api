package com.itangcent.easyapi.core.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Pure unit tests for [TaskList] / [Task] / [TaskStatus] and the
 * `taskList` field on [AgentMemory].
 *
 * No IDE / PSI dependency — Pattern A (simple JUnit 4).
 */
class TaskListTest {

    @Test
    fun taskListAcceptsDistinctTaskIds() {
        val taskList = TaskList(listOf(
            Task("s1", "detect"),
            Task("s2", "propose")
        ))
        assertEquals(2, taskList.tasks.size)
        assertEquals("s1", taskList.byId("s1")?.id)
        assertEquals("s2", taskList.byId("s2")?.id)
    }

    @Test
    fun taskListRejectsDuplicateTaskIds() {
        try {
            TaskList(listOf(
                Task("s1", "first"),
                Task("s1", "second")
            ))
            fail("expected IllegalArgumentException for duplicate ids")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "message should mention duplicate ids: ${e.message}",
                e.message?.contains("duplicate task ids") == true
            )
        }
    }

    @Test
    fun byIdReturnsNullForUnknownId() {
        val taskList = TaskList(listOf(Task("s1", "only")))
        assertNull(taskList.byId("nope"))
    }

    @Test
    fun newTasksDefaultToPending() {
        val taskList = TaskList(listOf(
            Task("s1", "a"),
            Task("s2", "b", detail = "elaboration")
        ))
        taskList.tasks.forEach {
            assertEquals(
                "task ${it.id} should default to PENDING",
                TaskStatus.PENDING, it.status
            )
        }
        assertEquals("elaboration", taskList.byId("s2")?.detail)
    }

    @Test
    fun agentMemoryTaskListStartsNullAndResets() {
        val memory = AgentMemory()
        assertNull("taskList should start null", memory.taskList)
        memory.taskList = TaskList(listOf(Task("s1", "x")))
        memory.reset()
        assertNull("reset() should clear taskList", memory.taskList)
    }
}

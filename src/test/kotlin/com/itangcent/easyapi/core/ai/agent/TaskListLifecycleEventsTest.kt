package com.itangcent.easyapi.core.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure unit tests for the five new [AgentEvent] subtypes added in B2.
 *
 * The sealed hierarchy is additive — existing consumers keep compiling.
 * This test confirms each subtype instantiates and carries its payload.
 */
class TaskListLifecycleEventsTest {

    @Test
    fun taskListCreatedCarriesTaskList() {
        val taskList = TaskList(listOf(Task("s1", "first")))
        val ev = AgentEvent.TaskListCreated(taskList)
        assertEquals(taskList, ev.taskList)
    }

    @Test
    fun taskStartedCarriesId() {
        val ev = AgentEvent.TaskStarted("s1")
        assertEquals("s1", ev.taskId)
    }

    @Test
    fun taskCompletedCarriesId() {
        val ev = AgentEvent.TaskCompleted("s2")
        assertEquals("s2", ev.taskId)
    }

    @Test
    fun taskFailedCarriesIdAndReason() {
        val ev = AgentEvent.TaskFailed("s3", "boom")
        assertEquals("s3", ev.taskId)
        assertEquals("boom", ev.reason)
    }

    @Test
    fun taskSkippedCarriesId() {
        val ev = AgentEvent.TaskSkipped("s4")
        assertEquals("s4", ev.taskId)
    }
}

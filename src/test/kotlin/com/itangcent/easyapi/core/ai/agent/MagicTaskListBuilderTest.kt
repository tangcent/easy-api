package com.itangcent.easyapi.core.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [MagicTaskListBuilder].
 *
 * Validates that Magic pre-builds an enablement-aware task list with one task
 * per matching detection catalog entry (design R2-C1 + AC-S7) — the
 * entrance-driven flow that replaces the v1 "agent calls create_task_list"
 * flow.
 *
 * The task list is filtered by the same active-feature sets used by
 * [SystemPromptBuilder.indexMessage] / [PromptCatalog.listFor], so Magic and
 * Reactive agree on which detections exist for a given turn.
 *
 * No IDE / PSI dependency — Pattern A (simple JUnit 4). Relies on
 * [PromptCatalog]'s classpath loading of the real `ai/detection/` files.
 */
class MagicTaskListBuilderTest {

    /**
     * The "all features enabled" set used to surface every detection whose
     * scope is present in the catalog. Detects any channel/format/framework
     * id declared in any detection file and unions them — keeps the test
     * resilient when new scoped detections are added.
     */
    private val allChannels: Set<String> by lazy {
        PromptCatalog.list("detection")
            .mapNotNull { it.scope.channel }
            .toSet()
    }
    private val allFormats: Set<String> by lazy {
        PromptCatalog.list("detection")
            .mapNotNull { it.scope.format }
            .toSet()
    }
    private val allFrameworks: Set<String> by lazy {
        PromptCatalog.list("detection")
            .mapNotNull { it.scope.framework }
            .toSet()
    }

    @Test
    fun buildDetectionPlanHasOneTaskPerMatchingDetectionFile() {
        // With every catalog-declared feature enabled, the task list matches
        // the full catalog (every detection's scope is satisfied).
        val taskList = MagicTaskListBuilder.buildDetectionPlan(
            activeChannels = allChannels,
            activeFormats = allFormats,
            activeFrameworks = allFrameworks
        )
        val expected = PromptCatalog.listFor(
            "detection",
            activeChannels = allChannels,
            activeFormats = allFormats,
            activeFrameworks = allFrameworks
        )
        assertEquals(
            "task count must equal matching detection catalog size",
            expected.size, taskList.tasks.size
        )
    }

    @Test
    fun taskIdsAreUniqueAndPrefixedWithDetect() {
        val taskList = MagicTaskListBuilder.buildDetectionPlan(
            activeChannels = allChannels,
            activeFormats = allFormats,
            activeFrameworks = allFrameworks
        )
        val ids = taskList.tasks.map { it.id }
        assertEquals("task ids must be unique", ids.distinct().size, ids.size)
        assertTrue(
            "every task id must be prefixed with 'detect_': $ids",
            ids.all { it.startsWith("detect_") }
        )
    }

    @Test
    fun taskTitlesAndDetailsMapFromCatalogEntries() {
        val taskList = MagicTaskListBuilder.buildDetectionPlan(
            activeChannels = allChannels,
            activeFormats = allFormats,
            activeFrameworks = allFrameworks
        )
        val expected = PromptCatalog.listFor(
            "detection",
            activeChannels = allChannels,
            activeFormats = allFormats,
            activeFrameworks = allFrameworks
        )
        // Task order matches catalog-manifest order.
        taskList.tasks.zip(expected).forEach { (task, entry) ->
            assertEquals("title must come from detection title", entry.title, task.title)
            assertEquals("detail must come from detection cue", entry.cue, task.detail)
        }
    }

    @Test
    fun allTasksStartPending() {
        val taskList = MagicTaskListBuilder.buildDetectionPlan(
            activeChannels = allChannels,
            activeFormats = allFormats,
            activeFrameworks = allFrameworks
        )
        assertTrue(
            "every task must start PENDING",
            taskList.tasks.all { it.status == TaskStatus.PENDING }
        )
    }

    @Test
    fun detectionIdIsSanitisedIntoTaskId() {
        // The detection id "spring-filters-interceptors" must become the
        // task id "detect_spring_filters_interceptors" (hyphens → underscores).
        val taskList = MagicTaskListBuilder.buildDetectionPlan(
            activeChannels = allChannels,
            activeFormats = allFormats,
            activeFrameworks = allFrameworks
        )
        val task = taskList.tasks.firstOrNull { it.title.contains("Filters", ignoreCase = true) }
        assertTrue(
            "expected a task whose title mentions Filters; got: ${taskList.tasks.map { it.title }}",
            task != null
        )
        assertTrue(
            "task id for a hyphenated detection id must use underscores: ${task?.id}",
            task?.id?.startsWith("detect_spring_filters_interceptors") == true ||
                task?.id?.matches(Regex("detect_[A-Za-z0-9_]+")) == true
        )
    }

    // -------------------------------------------------------------------
    // Feature-filtering behaviour (AC-S7)
    // -------------------------------------------------------------------

    @Test
    fun emptyActiveSetsExcludeScopedDetectionsKeepUnscopedOnes() {
        // With NO features enabled, only detections with no scope fields
        // (channel/format/framework all null) appear — a scoped detection
        // (e.g. framework: springmvc) is excluded.
        val taskList = MagicTaskListBuilder.buildDetectionPlan(
            activeChannels = emptySet(),
            activeFormats = emptySet(),
            activeFrameworks = emptySet()
        )
        val taskTitles = taskList.tasks.map { it.title }.toSet()

        // Unscoped detections (auth-token-chaining, custom-framework,
        // correlation-idempotency, hmac-signing, static-auth) MUST appear.
        assertTrue(
            "unscoped detection 'Auth token chaining' must appear with no features enabled",
            taskTitles.any { it.contains("Auth token chaining", ignoreCase = true) }
        )
        assertTrue(
            "unscoped detection 'Custom & meta-annotations' must appear with no features enabled",
            taskTitles.any { it.contains("Custom", ignoreCase = true) }
        )

        // Scoped detections MUST be absent.
        if ("springmvc" in allFrameworks) {
            assertFalse(
                "springmvc-scoped detection 'Filters, interceptors, web filters' must be absent when springmvc is not active",
                taskTitles.any { it.contains("Filters, interceptors", ignoreCase = true) }
            )
        }
    }

    @Test
    fun frameworkScopedDetectionsAppearOnlyWhenFrameworkActive() {
        // Only run this assertion when the catalog actually ships a
        // framework-scoped detection (resilient to catalog edits).
        if ("springmvc" !in allFrameworks) return

        // With springmvc active, the Filters/Interceptors detection appears...
        val withSpring = MagicTaskListBuilder.buildDetectionPlan(
            activeChannels = emptySet(),
            activeFormats = emptySet(),
            activeFrameworks = setOf("springmvc")
        )
        assertTrue(
            "springmvc-scoped detection must appear when springmvc is active",
            withSpring.tasks.any { it.title.contains("Filters, interceptors", ignoreCase = true) }
        )

        // ...and is absent when springmvc is not active.
        val withoutSpring = MagicTaskListBuilder.buildDetectionPlan(
            activeChannels = emptySet(),
            activeFormats = emptySet(),
            activeFrameworks = emptySet()
        )
        assertFalse(
            "springmvc-scoped detection must be absent when springmvc is not active",
            withoutSpring.tasks.any { it.title.contains("Filters, interceptors", ignoreCase = true) }
        )
    }

    @Test
    fun filteringMatchesPromptCatalogListFor() {
        // The task list MUST agree with PromptCatalog.listFor for the same
        // active sets — this is the AC-S7 contract that Magic and the
        // Reactive menu share.
        val activeChannels = setOf("postman", "markdown")
        val activeFormats = setOf("json")
        val activeFrameworks = setOf("springmvc")

        val taskList = MagicTaskListBuilder.buildDetectionPlan(
            activeChannels = activeChannels,
            activeFormats = activeFormats,
            activeFrameworks = activeFrameworks
        )
        val expected = PromptCatalog.listFor(
            "detection",
            activeChannels = activeChannels,
            activeFormats = activeFormats,
            activeFrameworks = activeFrameworks
        )
        assertEquals(
            "task count must equal PromptCatalog.listFor result",
            expected.size, taskList.tasks.size
        )
        taskList.tasks.zip(expected).forEach { (task, entry) ->
            assertEquals(
                "task title must match catalog entry title",
                entry.title, task.title
            )
        }
    }
}

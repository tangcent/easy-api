package com.itangcent.easyapi.core.ai.agent

import com.itangcent.easyapi.core.ai.tools.GetDetectionPromptTool
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for R3-C2: every detection id literal that appears in
 * shipped prompt/tool text MUST resolve through
 * [PromptCatalog.entry] — otherwise the agent learns a stale id, calls
 * `get_detection_prompt(id=...)` with it, and the catalog rejects every
 * one (the `unknown detection id` bursts seen in `idea.log` after the
 * Phase-A catalog rename).
 *
 * Surfaces scanned:
 *  - `src/main/resources/ai/agent-base.md` — the base prompt body that
 *    lists detection ids as examples in two places (the `get_detection_prompt`
 *    tool-index bullet and the "fetch the full recipe" paragraph).
 *  - [GetDetectionPromptTool.description] — the tool description sent to
 *    the LLM as part of the schema; example ids there are the most likely
 *    to be reused by the agent verbatim.
 *
 * The test fails fast on a future stale-id regression — e.g. if a catalog
 * file is renamed without updating these references.
 *
 * No IDE / PSI dependency — Pattern A (simple JUnit 4). Relies on
 * [PromptCatalog]'s classpath loading of the real `ai/detection/` files.
 */
class AgentBaseCatalogIdGuardTest {

    @Test
    fun everyDetectionIdInAgentBasePromptResolvesThroughCatalog() {
        val body = loadAgentBase()
        val ids = extractQuotedDetectionIds(body)
        assertTrue(
            "agent-base.md should mention at least one detection id literal " +
                "(found none — has the prompt format changed?); body was:\n$body",
            ids.isNotEmpty()
        )
        val known = PromptCatalog.list("detection").map { it.id }.toSet()
        val unresolved = ids.filter { it !in known }
        assertTrue(
            "every detection id literal in agent-base.md must resolve through " +
                "PromptCatalog.entry(\"detection\", id). Unresolved: $unresolved. " +
                "Known ids: $known",
            unresolved.isEmpty()
        )
    }

    @Test
    fun everyDetectionIdInGetDetectionPromptToolDescriptionResolves() {
        val description = GetDetectionPromptTool().description
        val ids = extractQuotedDetectionIds(description)
        assertTrue(
            "GetDetectionPromptTool.description should mention at least one " +
                "detection id literal (found none — has the description changed?); " +
                "description was: $description",
            ids.isNotEmpty()
        )
        val known = PromptCatalog.list("detection").map { it.id }.toSet()
        val unresolved = ids.filter { it !in known }
        assertTrue(
            "every detection id literal in GetDetectionPromptTool.description " +
                "must resolve through PromptCatalog.entry(\"detection\", id). " +
                "Unresolved: $unresolved. Known ids: $known",
            unresolved.isEmpty()
        )
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    /**
     * Loads `/ai/agent-base.md` from the classpath (the same resource
     * [SystemPromptBuilder] loads at runtime).
     */
    private fun loadAgentBase(): String {
        val stream = javaClass.getResourceAsStream("/ai/agent-base.md")
            ?: error("/ai/agent-base.md not found on classpath — run from the project root so test resources resolve")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /**
     * Extracts bare id literals quoted in backticks (`` `id` ``) or double
     * quotes (`"id"`) from [text], but only from contexts where detection ids
     * are explicitly listed as examples — i.e. text following a `(e.g.`
     * opener (up to the matching `)`) or following an `Examples:` marker (up
     * to the next blank line).
     *
     * Two quote forms are supported because the surfaces scanned use
     * different conventions: `agent-base.md` uses backtick-quoted ids
     * (markdown), while [GetDetectionPromptTool.description] uses
     * double-quoted ids (plain text sent to the LLM as a tool schema).
     *
     * This avoids false positives like `` `rule-guide` `` (a knowledge-base
     * page name, not a detection id) that happen to share the same
     * lowercase-hyphenated shape.
     */
    private fun extractQuotedDetectionIds(text: String): List<String> {
        // Match either `id` (backticks, used in markdown) or "id" (double
        // quotes, used in plain-text tool descriptions).
        val tokenRegex = Regex("[`\"]([a-z][a-z0-9]*(?:-[a-z0-9]+)+)[`\"]")
        val result = LinkedHashSet<String>()

        // Context 1: "(e.g. `id`, `id`, ...)" — extract up to the matching ")".
        val egContext = Regex("\\(e\\.g\\.[^)]*\\)", RegexOption.DOT_MATCHES_ALL)
        egContext.findAll(text).forEach { m ->
            tokenRegex.findAll(m.value).forEach { result.add(it.groupValues[1]) }
        }

        // Context 2: "Examples: `id`, `id`, ..." — extract up to the next
        // blank line (a line that is empty or whitespace-only).
        val examplesMarkerIndex = text.indexOf("Examples:")
        if (examplesMarkerIndex >= 0) {
            val fromMarker = text.substring(examplesMarkerIndex)
            val blankLineIndex = fromMarker.indexOf("\n\n")
            val block = if (blankLineIndex < 0) fromMarker else fromMarker.substring(0, blankLineIndex)
            tokenRegex.findAll(block).forEach { result.add(it.groupValues[1]) }
        }

        return result.toList()
    }
}

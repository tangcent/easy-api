package com.itangcent.easyapi.core.extension

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Semantic safety checks applied to every bundled extension rule.
 *
 * The registry test separately guarantees that every bundled extension is
 * discovered. This test audits every discovered rule body for expressions
 * that are syntactically valid but use a simple class name where a qualified
 * name or package is required.
 */
class BuiltInExtensionRuleSemanticsTest {

    @Test
    fun testClassContextFqnComparisonsUseQualifiedName() {
        val violations = ExtensionConfigRegistry.allExtensions().flatMap { extension ->
            extension.content.lineSequence().mapIndexedNotNull { index, line ->
                if (CLASS_CONTEXT_NAME_USED_AS_FQN.containsMatchIn(line)) {
                    "${extension.code}:${index + 1}: ${line.trim()}"
                } else {
                    null
                }
            }.toList()
        }

        assertTrue(
            "Class contexts return a simple name from name(); use qualifiedName() " +
                "for FQN/package comparisons:\n${violations.joinToString("\n")}",
            violations.isEmpty()
        )
    }

    private companion object {
        val CLASS_CONTEXT_NAME_USED_AS_FQN = Regex(
            """(?:containingClass|defineClass)\(\)\??\.name\(\)\s*(?:\.\s*(?:startsWith|endsWith|equals|matches)\s*\(\s*["'][^"']*\.[^"']*|(?:==|!=)\s*["'][^"']*\.[^"']*)"""
        )
    }
}

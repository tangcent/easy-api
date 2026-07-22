package com.itangcent.easyapi.core.psi.helper

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Direct PSI tests for [AnnotatedElementsHelper].
 *
 * Unlike [com.itangcent.easyapi.core.rule.parser.ScriptHelperFindAnnotatedTest],
 * these tests do NOT go through `ScriptHelper` / the JSR-223 binding. They
 * exercise the project-scoped `@Service` directly, asserting on raw
 * [PsiClass] / [PsiMethod] return types. This is the whole point of the
 * refactor: the lookup logic must be reachable from non-script code
 * and independently testable.
 *
 * Mirrors the fixture style of
 * [com.itangcent.easyapi.core.ai.tools.FindClassesByAnnotationToolTest] and
 * `ScriptHelperFindAnnotatedTest`:
 * [com.intellij.psi.search.searches.AnnotatedElementsSearch.searchPsiClasses]
 * / `searchPsiMethods` need source files (annotation + annotated classes) in
 * the fixture's VFS, so each test loads them via `myFixture.addFileToProject`
 * inside a write action.
 *
 * JUnit 3-style `testXxx()` naming is required because
 * [EasyApiLightCodeInsightFixtureTestCase] extends
 * `LightJavaCodeInsightFixtureTestCase` (a JUnit 3 `TestCase` subclass).
 */
class AnnotatedElementsHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var helper: AnnotatedElementsHelper

    override fun setUp() {
        super.setUp()
        helper = AnnotatedElementsHelper.getInstance(project)
    }

    private fun addClasses() {
        ApplicationManager.getApplication().runWriteAction {
            // A project-defined annotation that targets both TYPE and METHOD.
            myFixture.addFileToProject(
                "com/example/Marker.java",
                """
                package com.example;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;
                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.TYPE, ElementType.METHOD})
                public @interface Marker {}
                """.trimIndent()
            )
            // Project classes annotated with @Marker.
            myFixture.addFileToProject(
                "com/example/MarkedOne.java",
                """
                package com.example;
                @Marker
                public class MarkedOne {}
                """.trimIndent()
            )
            myFixture.addFileToProject(
                "com/example/MarkedTwo.java",
                """
                package com.example;
                @Marker
                public class MarkedTwo {}
                """.trimIndent()
            )
            // A class with @Marker-annotated methods (and one unannotated method).
            myFixture.addFileToProject(
                "com/example/ServiceBean.java",
                """
                package com.example;
                public class ServiceBean {
                    @Marker
                    public void doSomething() {}

                    @Marker
                    public String computeValue() { return ""; }

                    public void unannotated() {}
                }
                """.trimIndent()
            )
            // Unannotated class — must not be returned.
            myFixture.addFileToProject(
                "com/example/PlainService.java",
                """
                package com.example;
                public class PlainService {}
                """.trimIndent()
            )
        }
    }

    // ─── getInstance ─────────────────────────────────────────────────────

    fun testGetInstanceReturnsSameInstance() {
        val instance1 = AnnotatedElementsHelper.getInstance(project)
        val instance2 = AnnotatedElementsHelper.getInstance(project)
        assertSame(
            "getInstance should return the same project-scoped service instance",
            instance1,
            instance2,
        )
    }

    // ─── findClassByAnnotation (singular) ────────────────────────────────

    fun testFindClassByAnnotationReturnsFirstAnnotatedClass() {
        addClasses()
        val hit = helper.findClassByAnnotation("com.example.Marker")
        assertNotNull("expected a non-null result for @Marker, got null", hit)
        val fqn = hit!!.qualifiedName
        assertTrue(
            "expected one of MarkedOne/MarkedTwo, got $fqn",
            fqn == "com.example.MarkedOne" || fqn == "com.example.MarkedTwo",
        )
    }

    fun testFindClassByAnnotationReturnsRawPsiClassInstance() {
        addClasses()
        val hit = helper.findClassByAnnotation("com.example.Marker")
        assertNotNull("expected a non-null result for @Marker, got null", hit)
        assertTrue(
            "result must be a raw PsiClass (not a script context wrapper): $hit",
            hit is PsiClass,
        )
    }

    fun testFindClassByAnnotationReturnsNullWhenAnnotationNotResolvable() {
        addClasses()
        val hit = helper.findClassByAnnotation("does.not.Exist")
        assertNull("expected null for unresolvable annotation, got $hit", hit)
    }

    fun testFindClassByAnnotationReturnsNullWhenFqnIsNotAnAnnotationType() {
        addClasses()
        // PlainService is a regular class, not an annotation — the helper
        // must refuse to search and return null (mirrors
        // FindClassesByAnnotationTool behavior).
        val hit = helper.findClassByAnnotation("com.example.PlainService")
        assertNull("expected null when FQN is not an annotation, got $hit", hit)
    }

    fun testFindClassByAnnotationReturnsNullWhenNoClassIsAnnotated() {
        // No project source annotated with @Nonexistent — the annotation
        // itself resolves, but nothing carries it.
        ApplicationManager.getApplication().runWriteAction {
            myFixture.addFileToProject(
                "com/example/Unused.java",
                """
                package com.example;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;
                @Retention(RetentionPolicy.RUNTIME)
                @Target({ElementType.TYPE})
                public @interface Unused {}
                """.trimIndent()
            )
        }
        val hit = helper.findClassByAnnotation("com.example.Unused")
        assertNull("expected null when no class carries the annotation, got $hit", hit)
    }

    fun testFindClassByAnnotationReturnsNullForBlankFqn() {
        val hit = helper.findClassByAnnotation("   ")
        assertNull("expected null for blank annotation FQN, got $hit", hit)
    }

    // ─── findClassesByAnnotation ─────────────────────────────────────────

    fun testFindClassesByAnnotationReturnsAnnotatedClasses() {
        addClasses()
        val hits = helper.findClassesByAnnotation("com.example.Marker")
        val fqns = hits.mapNotNull { it.qualifiedName }
        assertTrue(
            "expected MarkedOne and MarkedTwo in $fqns",
            fqns.contains("com.example.MarkedOne") && fqns.contains("com.example.MarkedTwo"),
        )
        // PlainService is unannotated — must not be returned.
        assertTrue(
            "unannotated class must not be returned: $fqns",
            !fqns.contains("com.example.PlainService"),
        )
        // ServiceBean has annotated methods but the class itself is NOT @Marker.
        assertTrue(
            "class with annotated methods (but not annotated itself) must not appear: $fqns",
            !fqns.contains("com.example.ServiceBean"),
        )
    }

    fun testFindClassesByAnnotationReturnsRawPsiClassInstances() {
        addClasses()
        // The whole point of the refactor: results are raw PsiClass instances, NOT
        // ScriptPsiClassContext wrappers. Non-script callers can use them directly.
        val hits = helper.findClassesByAnnotation("com.example.Marker")
        assertTrue(
            "every result must be a raw PsiClass (not a script context wrapper)",
            hits.all { it is PsiClass },
        )
    }

    fun testFindClassesByAnnotationReturnsEmptyWhenAnnotationNotResolvable() {
        addClasses()
        val hits = helper.findClassesByAnnotation("does.not.Exist")
        assertTrue("expected empty list for unresolvable annotation, got: $hits", hits.isEmpty())
    }

    fun testFindClassesByAnnotationReturnsEmptyWhenFqnIsNotAnAnnotationType() {
        addClasses()
        // PlainService is a regular class, not an annotation — the helper
        // must refuse to search and return an empty list (mirrors
        // FindClassesByAnnotationTool behavior).
        val hits = helper.findClassesByAnnotation("com.example.PlainService")
        assertTrue("expected empty list when FQN is not an annotation, got: $hits", hits.isEmpty())
    }

    fun testFindClassesByAnnotationReturnsEmptyForBlankFqn() {
        val hits = helper.findClassesByAnnotation("   ")
        assertTrue("expected empty list for blank annotation FQN, got: $hits", hits.isEmpty())
    }

    // ─── findMethodsByAnnotation ──────────────────────────────────────────

    fun testFindMethodsByAnnotationReturnsAnnotatedMethods() {
        addClasses()
        val hits = helper.findMethodsByAnnotation("com.example.Marker")
        // Only ServiceBean has @Marker-annotated methods — doSomething and computeValue.
        assertEquals(
            "expected exactly 2 @Marker-annotated methods, got: ${hits.size}",
            2,
            hits.size,
        )
        val methodNames = hits.map { it.name }
        assertTrue(
            "expected doSomething and computeValue in $methodNames",
            methodNames.contains("doSomething") && methodNames.contains("computeValue"),
        )
        // The unannotated method must not appear.
        assertTrue(
            "unannotated method must not be returned: $methodNames",
            !methodNames.contains("unannotated"),
        )
    }

    fun testFindMethodsByAnnotationReturnsRawPsiMethodInstances() {
        addClasses()
        // The whole point of the refactor: results are raw PsiMethod instances, NOT
        // ScriptPsiMethodContext wrappers.
        val hits = helper.findMethodsByAnnotation("com.example.Marker")
        assertTrue(
            "every result must be a raw PsiMethod (not a script context wrapper)",
            hits.all { it is PsiMethod },
        )
    }

    fun testFindMethodsByAnnotationReturnsEmptyWhenAnnotationNotResolvable() {
        addClasses()
        val hits = helper.findMethodsByAnnotation("does.not.Exist")
        assertTrue("expected empty list for unresolvable annotation, got: $hits", hits.isEmpty())
    }

    fun testFindMethodsByAnnotationReturnsEmptyWhenFqnIsNotAnAnnotationType() {
        addClasses()
        val hits = helper.findMethodsByAnnotation("com.example.PlainService")
        assertTrue("expected empty list when FQN is not an annotation, got: $hits", hits.isEmpty())
    }

    fun testFindMethodsByAnnotationReturnsEmptyForBlankFqn() {
        val hits = helper.findMethodsByAnnotation("")
        assertTrue("expected empty list for blank annotation FQN, got: $hits", hits.isEmpty())
    }
}

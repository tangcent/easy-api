package com.itangcent.easyapi.core.rule.parser

import com.intellij.openapi.application.ApplicationManager
import com.itangcent.easyapi.core.rule.context.RuleContext
import com.itangcent.easyapi.core.rule.context.ScriptPsiClassContext
import com.itangcent.easyapi.core.rule.context.ScriptPsiMethodContext
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Tests for the `findClassesByAnnotation` / `findMethodsByAnnotation`
 * helpers on [ScriptHelper].
 *
 * These helpers back the `springfox-openapi.config` Springfox `Docket`
 * extractor and are generally useful for any `groovy:` rule script that
 * needs to locate annotated code (`helper.findClassesByAnnotation(...)`
 * / `H.findMethodsByAnnotation(...)`).
 *
 * Mirrors the fixture style of
 * [com.itangcent.easyapi.core.ai.tools.FindClassesByAnnotationToolTest]:
 * `AnnotatedElementsSearch` needs source files (annotation + annotated
 * classes) in the fixture's VFS, so each test loads them via
 * `myFixture.addFileToProject` inside a write action.
 *
 * JUnit 3-style `testXxx()` naming is required because
 * [EasyApiLightCodeInsightFixtureTestCase] extends
 * `LightJavaCodeInsightFixtureTestCase` (a JUnit 3 `TestCase` subclass).
 */
class ScriptHelperFindAnnotatedTest : EasyApiLightCodeInsightFixtureTestCase() {

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

    private fun newHelper(): ScriptHelper {
        // `findClassesByAnnotation` / `findMethodsByAnnotation` don't use the
        // context's element; they resolve the annotation via
        // `JavaPsiFacade.findClass(...)` and search the project scope. A
        // without-element context is sufficient.
        val context = RuleContext.withoutElement(project)
        return ScriptHelper(context)
    }

    // ─── findClassByAnnotation (singular) ────────────────────────────────

    fun testFindClassByAnnotationReturnsFirstAnnotatedClass() {
        addClasses()
        val hit = newHelper().findClassByAnnotation("com.example.Marker")
        assertNotNull("expected a non-null result for @Marker, got null", hit)
        val fqn = (hit as? ScriptPsiClassContext)?.qualifiedName()
        assertTrue(
            "expected one of MarkedOne/MarkedTwo, got $fqn",
            fqn == "com.example.MarkedOne" || fqn == "com.example.MarkedTwo",
        )
    }

    fun testFindClassByAnnotationReturnsScriptPsiClassContext() {
        addClasses()
        val hit = newHelper().findClassByAnnotation("com.example.Marker")
        assertNotNull("expected a non-null result for @Marker, got null", hit)
        assertTrue(
            "result must be a ScriptPsiClassContext for the script binding: $hit",
            hit is ScriptPsiClassContext,
        )
    }

    fun testFindClassByAnnotationReturnsNullWhenAnnotationNotResolvable() {
        addClasses()
        val hit = newHelper().findClassByAnnotation("does.not.Exist")
        assertNull("expected null for unresolvable annotation, got $hit", hit)
    }

    fun testFindClassByAnnotationReturnsNullWhenFqnIsNotAnAnnotationType() {
        addClasses()
        // PlainService is a regular class, not an annotation — the helper
        // must refuse to search and return null (mirrors
        // FindClassesByAnnotationTool behavior).
        val hit = newHelper().findClassByAnnotation("com.example.PlainService")
        assertNull("expected null when FQN is not an annotation, got $hit", hit)
    }

    fun testFindClassByAnnotationReturnsNullForBlankFqn() {
        val hit = newHelper().findClassByAnnotation("   ")
        assertNull("expected null for blank annotation FQN, got $hit", hit)
    }

    // ─── findClassesByAnnotation ─────────────────────────────────────────

    fun testFindClassesByAnnotationReturnsAnnotatedClasses() {
        addClasses()
        val hits = newHelper().findClassesByAnnotation("com.example.Marker")
        val fqns = hits.mapNotNull { (it as? ScriptPsiClassContext)?.qualifiedName() }
        assertTrue(
            "expected MarkedOne and MarkedTwo in $fqns",
            fqns.contains("com.example.MarkedOne") && fqns.contains("com.example.MarkedTwo"),
        )
        // PlainService is unannotated — must not appear.
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

    fun testFindClassesByAnnotationReturnsEmptyWhenAnnotationNotResolvable() {
        addClasses()
        val hits = newHelper().findClassesByAnnotation("does.not.Exist")
        assertTrue("expected empty list for unresolvable annotation, got: $hits", hits.isEmpty())
    }

    fun testFindClassesByAnnotationReturnsEmptyWhenFqnIsNotAnAnnotationType() {
        addClasses()
        // PlainService is a regular class, not an annotation — the helper
        // must refuse to search and return an empty list (mirrors
        // FindClassesByAnnotationTool behavior).
        val hits = newHelper().findClassesByAnnotation("com.example.PlainService")
        assertTrue("expected empty list when FQN is not an annotation, got: $hits", hits.isEmpty())
    }

    fun testFindClassesByAnnotationReturnsEmptyForBlankFqn() {
        val hits = newHelper().findClassesByAnnotation("   ")
        assertTrue("expected empty list for blank annotation FQN, got: $hits", hits.isEmpty())
    }

    // ─── findMethodsByAnnotation ──────────────────────────────────────────

    fun testFindMethodsByAnnotationReturnsAnnotatedMethods() {
        addClasses()
        val hits = newHelper().findMethodsByAnnotation("com.example.Marker")
        // Only ServiceBean has @Marker-annotated methods — doSomething and computeValue.
        assertEquals(
            "expected exactly 2 @Marker-annotated methods, got: ${hits.size}",
            2,
            hits.size,
        )
        val methodNames = hits.mapNotNull { (it as? ScriptPsiMethodContext)?.name() }
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

    fun testFindMethodsByAnnotationReturnsEmptyWhenAnnotationNotResolvable() {
        addClasses()
        val hits = newHelper().findMethodsByAnnotation("does.not.Exist")
        assertTrue("expected empty list for unresolvable annotation, got: $hits", hits.isEmpty())
    }

    fun testFindMethodsByAnnotationReturnsEmptyWhenFqnIsNotAnAnnotationType() {
        addClasses()
        val hits = newHelper().findMethodsByAnnotation("com.example.PlainService")
        assertTrue("expected empty list when FQN is not an annotation, got: $hits", hits.isEmpty())
    }

    fun testFindMethodsByAnnotationReturnsEmptyForBlankFqn() {
        val hits = newHelper().findMethodsByAnnotation("")
        assertTrue("expected empty list for blank annotation FQN, got: $hits", hits.isEmpty())
    }
}

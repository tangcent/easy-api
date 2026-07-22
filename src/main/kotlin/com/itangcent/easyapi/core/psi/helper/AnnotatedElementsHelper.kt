package com.itangcent.easyapi.core.psi.helper

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.itangcent.easyapi.core.internal.threading.readSync
import com.itangcent.easyapi.core.logging.IdeaLog

/**
 * Finds [PsiClass] / [PsiMethod] instances by annotation FQN.
 *
 * Wraps [AnnotatedElementsSearch.searchPsiClasses] /
 * [AnnotatedElementsSearch.searchPsiMethods] with annotation resolution via
 * [JavaPsiFacade.findClass] (all-scope) + an `isAnnotationType` guard. The
 * search itself runs in [GlobalSearchScope.projectScope] so only project
 * sources are returned (matching the existing
 * [com.itangcent.easyapi.core.ai.tools.FindClassesByAnnotationTool] behavior).
 *
 * ## Why a separate helper
 *
 * Previously the lookup logic lived directly on
 * [com.itangcent.easyapi.core.rule.parser.ScriptHelper], which made it
 * unreachable from non-script code and untestable in isolation. This class
 * splits the responsibilities:
     * - **`AnnotatedElementsHelper`** (this class) — pure PSI lookup, returns
     *   `List<PsiClass>` / `List<PsiMethod>` (or a single `PsiClass?` via the
     *   singular `findClassByAnnotation`). Project-scoped `@Service`, no
     *   dependency on rule-script context.
     * - **`ScriptHelper.findClassByAnnotation` /
     *   `ScriptHelper.findClassesByAnnotation` / `findMethodsByAnnotation`** —
     *   thin wrappers that call this helper and wrap each result in a
     *   [com.itangcent.easyapi.core.rule.context.ScriptPsiClassContext] /
     *   [com.itangcent.easyapi.core.rule.context.ScriptPsiMethodContext] for the
     *   `helper` / `H` script binding.
 *
 * This mirrors the existing `SourceHelper` pattern (project service, plain
 * PSI return type, suspend + sync variants) and the
 * [com.itangcent.easyapi.core.ai.tools.FindClassesByAnnotationTool] AI-tool
 * pattern (same `AnnotatedElementsSearch` API + `isAnnotationType` filter).
 *
 * ## Threading
 *
 * All public methods run inside [readSync] so they can be called from any
 * rule-script context (the JSR-223 boundary is non-suspending). Callers
 * already inside a read action incur no nested read action cost (IntelliJ
 * re-entrant read actions are essentially free).
 *
 * ## Error handling
 *
     * Every public method returns an empty value on any failure (empty list
     * for the plural variants, `null` for `findClassByAnnotation`; unresolvable
     * annotation FQN, non-annotation type, read-action failure). Errors are
     * logged at `warn` with the throwable as the last arg — per AGENTS.md
     * §"Logging" → "Anti-patterns" (no silent `runCatching{}.getOrNull()`).
 *
 * @see ScriptHelper for the script-binding surface
 * @see com.itangcent.easyapi.core.ai.tools.FindClassesByAnnotationTool for the AI-tool equivalent
 */
@Service(Service.Level.PROJECT)
class AnnotatedElementsHelper(private val project: Project) : IdeaLog {

    companion object {
        /**
         * Get the [AnnotatedElementsHelper] instance for a project.
         *
         * @param project the IntelliJ project
         * @return the project-scoped service instance
         */
        fun getInstance(project: Project): AnnotatedElementsHelper = project.service()
    }

    /**
     * Finds the first class annotated with the given annotation FQN.
     *
     * Singular counterpart of [findClassesByAnnotation]: it stops at the
     * first match instead of collecting all hits, so it is cheaper when the
     * caller only needs one result. This covers the common case where a
     * document-level annotation (e.g. `@SwaggerDefinition`,
     * `@OpenAPIDefinition`) is declared on a single config class — the rule
     * scripts previously wrote `findClassesByAnnotation(...)[0]`, which still
     * paid for the full scan.
     *
     * Returns `null` when:
     * - [annotationFqn] is blank
     * - the annotation cannot be resolved via [JavaPsiFacade.findClass] in
     *   [GlobalSearchScope.allScope]
     * - the resolved class is NOT an annotation type (e.g., the FQN points at
     *   a regular class)
     * - the read action throws
     * - no class is annotated
     *
     * @param annotationFqn fully-qualified annotation name (e.g.,
     *   `"org.springframework.stereotype.Service"`). Simple names are NOT
     *   supported here — the script-binding layer (`ScriptHelper`) keeps that
     *   flexibility for AI tools via `PsiNameResolver`. Production rule
     *   scripts always pass FQNs.
     * @return the first matching [PsiClass] in project scope, or `null` on
     *   any error or when no class is annotated (never throws).
     * @see findClassesByAnnotation for the all-matches variant
     */
    fun findClassByAnnotation(annotationFqn: String): PsiClass? {
        if (annotationFqn.isBlank()) return null
        return runCatching {
            readSync {
                val annotationClass = resolveAnnotation(annotationFqn) ?: return@readSync null
                val scope = GlobalSearchScope.projectScope(project)
                AnnotatedElementsSearch
                    .searchPsiClasses(annotationClass, scope)
                    .findFirst()
            }
        }.onFailure {
            LOG.warn("AnnotatedElementsHelper.findClassByAnnotation failed for '$annotationFqn'", it)
        }.getOrNull()
    }

    /**
     * Finds all classes annotated with the given annotation FQN.
     *
     * Returns an empty list when:
     * - [annotationFqn] is blank
     * - the annotation cannot be resolved via [JavaPsiFacade.findClass] in
     *   [GlobalSearchScope.allScope]
     * - the resolved class is NOT an annotation type (e.g., the FQN points at
     *   a regular class)
     * - the read action throws
     *
     * When only the first match is needed, prefer the cheaper
     * [findClassByAnnotation], which stops the search early.
     *
     * @param annotationFqn fully-qualified annotation name (e.g.,
     *   `"org.springframework.stereotype.Service"`). Simple names are NOT
     *   supported here — the script-binding layer (`ScriptHelper`) keeps that
     *   flexibility for AI tools via `PsiNameResolver`. Production rule
     *   scripts always pass FQNs.
     * @return matching [PsiClass] instances in project scope (empty on any
     *   error; never throws).
     */
    fun findClassesByAnnotation(annotationFqn: String): List<PsiClass> {
        if (annotationFqn.isBlank()) return emptyList()
        return runCatching {
            readSync {
                val annotationClass = resolveAnnotation(annotationFqn) ?: return@readSync emptyList<PsiClass>()
                val scope = GlobalSearchScope.projectScope(project)
                val hits = AnnotatedElementsSearch
                    .searchPsiClasses(annotationClass, scope)
                    .findAll()
                LOG.info("AnnotatedElementsHelper: found ${hits.size} class(es) annotated with $annotationFqn")
                hits.toList()
            }
        }.onFailure {
            LOG.warn("AnnotatedElementsHelper.findClassesByAnnotation failed for '$annotationFqn'", it)
        }.getOrDefault(emptyList())
    }

    /**
     * Finds all methods annotated with the given annotation FQN.
     *
     * Returns an empty list when:
     * - [annotationFqn] is blank
     * - the annotation cannot be resolved via [JavaPsiFacade.findClass]
     * - the resolved class is NOT an annotation type
     * - the read action throws
     *
     * @param annotationFqn fully-qualified annotation name (e.g.,
     *   `"org.springframework.context.annotation.Bean"`).
     * @return matching [PsiMethod] instances in project scope (empty on any
     *   error; never throws).
     */
    fun findMethodsByAnnotation(annotationFqn: String): List<PsiMethod> {
        if (annotationFqn.isBlank()) return emptyList()
        return runCatching {
            readSync {
                val annotationClass = resolveAnnotation(annotationFqn) ?: return@readSync emptyList<PsiMethod>()
                val scope = GlobalSearchScope.projectScope(project)
                val hits = AnnotatedElementsSearch
                    .searchPsiMethods(annotationClass, scope)
                    .findAll()
                LOG.info("AnnotatedElementsHelper: found ${hits.size} method(s) annotated with $annotationFqn")
                hits.toList()
            }
        }.onFailure {
            LOG.warn("AnnotatedElementsHelper.findMethodsByAnnotation failed for '$annotationFqn'", it)
        }.getOrDefault(emptyList())
    }

    /**
     * Resolves [annotationFqn] to a [PsiClass] and verifies it is an
     * annotation type.
     *
     * Returns `null` and logs at `info` when:
     * - the FQN cannot be resolved (no such class on the project classpath)
     * - the resolved class is not `isAnnotationType` (e.g., the FQN points at
     *   a regular class/interface/enum)
     *
     * `info` (not `warn`) is correct here because "user passed a non-existent
     * FQN" is a routine rule-script condition, not a recoverable failure —
     * the caller returns an empty list and the rule engine falls through.
     */
    private fun resolveAnnotation(annotationFqn: String): PsiClass? {
        val annotationClass = JavaPsiFacade.getInstance(project)
            .findClass(annotationFqn, GlobalSearchScope.allScope(project))
        if (annotationClass == null) {
            LOG.info("AnnotatedElementsHelper: annotation not resolvable: $annotationFqn")
            return null
        }
        if (!annotationClass.isAnnotationType) {
            LOG.info("AnnotatedElementsHelper: not an annotation type: $annotationFqn")
            return null
        }
        return annotationClass
    }
}

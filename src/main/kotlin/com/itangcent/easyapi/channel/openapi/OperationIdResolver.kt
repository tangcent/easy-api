package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.logging.IdeaLog
import com.intellij.psi.PsiMethod

/**
 * Outcome of [OperationIdResolver.resolve] — carries both the resolved
 * operation id and a flag signalling whether the resolution had to break a
 * collision (which the resolver logs at `warn`).
 *
 * Exposing [collisionDetected] lets tests assert the warn-log side effect
 * without capturing an IntelliJ `Logger` test fake.
 */
data class ResolutionResult(
    val operationId: String,
    val collisionDetected: Boolean,
)

/**
 * Resolves a stable, document-unique `operationId` for an OAS Operation
 * Object.
 *
 * Resolution order:
 * 1. `sourceMethod.name` when the method is available (e.g. `getUser`).
 * 2. `{method}_{path_slug}` slug fallback, where `path_slug` is the
 *    normalized path with `{`/`}`/`/` collapsed to `_`, trimmed and
 *    lowercased (e.g. `/users/{id}` → `users_id`, giving `get_users_id`).
 *    When the path slug is empty (e.g. root `/`), the operationId is just
 *    the lowercased method name.
 * 3. On collision with an id already in [used], a numeric `-N` suffix
 *    (N ≥ 2) is appended; the first free slot wins, so `getUser` →
 *    `getUser-2` → `getUser-3`. The collision is logged at `warn`
 *    and flagged in the returned [ResolutionResult].
 *
 * The resolved id is added to [used] before returning so the next caller
 * sees it. The object is stateless and pure — all mutable state is supplied
 * by the caller via [used].
 */
object OperationIdResolver : IdeaLog {

    fun resolve(
        meta: HttpMetadata,
        sourceMethod: PsiMethod?,
        used: MutableSet<String>,
    ): ResolutionResult {
        val baseName = pickBaseName(meta, sourceMethod)

        // First use — no collision.
        if (baseName !in used) {
            used.add(baseName)
            return ResolutionResult(operationId = baseName, collisionDetected = false)
        }

        // Collision — find the first free `-N` slot (N ≥ 2).
        var suffix = 2
        while ("$baseName-$suffix" in used) {
            suffix++
        }
        val finalId = "$baseName-$suffix"
        LOG.warn("operationId collision: '$baseName' already used; suffixing as '$finalId'")
        used.add(finalId)
        return ResolutionResult(operationId = finalId, collisionDetected = true)
    }

    /**
     * Picks the base name (before any collision suffixing) per the
     * resolution order: `sourceMethod.name` → `{method}_{path_slug}`.
     */
    private fun pickBaseName(meta: HttpMetadata, sourceMethod: PsiMethod?): String {
        val methodName = sourceMethod?.name
        if (!methodName.isNullOrBlank()) return methodName

        return buildSlug(meta.method, meta.path)
    }

    /**
     * Builds the `{method}_{path_slug}` fallback. Path slashes, `{` and `}`
     * collapse to a single `_`; consecutive `_` runs collapse to one; the
     * result is trimmed of leading/trailing `_` and lowercased. If the path
     * slug is empty (root path `/`, or empty path), the result is just the
     * lowercased method name.
     */
    private fun buildSlug(method: HttpMethod, path: String): String {
        val methodSlug = method.name.lowercase()
        val pathSlug = path
            .replace('/', '_')
            .replace('{', '_')
            .replace('}', '_')
            .split('_')
            .filter { it.isNotEmpty() }
            .joinToString("_")
            .lowercase()

        return if (pathSlug.isEmpty()) methodSlug else "${methodSlug}_$pathSlug"
    }
}

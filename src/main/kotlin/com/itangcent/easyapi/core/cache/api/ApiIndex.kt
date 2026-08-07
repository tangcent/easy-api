package com.itangcent.easyapi.core.cache.api

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.internal.threading.backgroundAsync
import com.itangcent.easyapi.core.logging.IdeaLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-memory cache for API endpoints grouped by class name.
 *
 * `cacheReady` completes after the first successful mutation and remains completed
 * for the lifetime of this service; `cacheValid` indicates whether the retained
 * data is currently valid. The lifecycle controller intentionally leaves this
 * snapshot in place when continuous scanning stops, while [invalidate] explicitly
 * clears it. Use [retainedSnapshot] when a caller must not wait for scanning.
 *
 * Use this cache for fast, potentially stale endpoint snapshots and
 * [com.itangcent.easyapi.core.dashboard.ApiScanner] when a real-time PSI scan is
 * required. The endpoint map is synchronized, and returned lists are detached
 * from the internal snapshot before they are exposed.
 *
 * @see ApiIndexManager for cache lifecycle management
 */
@Service(Service.Level.PROJECT)
class ApiIndex {

    @Volatile
    private var endpointsByClass: Map<String, List<ApiEndpoint>> = emptyMap()

    private val cacheLock = Any()
    private val cacheValid = AtomicBoolean(false)
    private val cacheReady = CompletableDeferred<Unit>()
    private val endpointsFlow = MutableSharedFlow<List<ApiEndpoint>>(replay = 1)

    internal data class Mutation(
        val endpoints: List<ApiEndpoint>,
        val classCount: Int
    )

    /**
     * Subscribes to successful cache updates.
     *
     * The callback runs on a background thread.
     */
    fun subscribe(listener: suspend (List<ApiEndpoint>) -> Unit) {
        backgroundAsync {
            endpointsFlow.collect { listener(it) }
        }
    }

    /**
     * Returns all valid cached endpoints after the first successful update.
     *
     * This method keeps waiting until an initial update occurs. Use
     * [retainedSnapshot] when a caller must not wait for scanning to start.
     */
    suspend fun endpoints(): List<ApiEndpoint> {
        awaitCacheReady()
        if (!cacheValid.get()) return emptyList()
        return retainedSnapshot()
    }

    /** Returns valid cached endpoints for [className] after the first update. */
    suspend fun endpointsByClass(className: String): List<ApiEndpoint> {
        awaitCacheReady()
        if (!cacheValid.get()) return emptyList()
        return retainedSnapshotByClass(className)
    }

    /**
     * Returns the last retained endpoint snapshot without waiting for a scan.
     *
     * The returned list is detached from the internal map and is safe to read
     * after continuous scanning has been stopped.
     */
    fun retainedSnapshot(): List<ApiEndpoint> = synchronized(cacheLock) {
        endpointsByClass.values.flatten()
    }

    /** Returns the retained endpoints for [className] without waiting. */
    fun retainedSnapshotByClass(className: String): List<ApiEndpoint> = synchronized(cacheLock) {
        endpointsByClass[className]?.toList() ?: emptyList()
    }

    /** Replaces the cache with a complete successful scan result. */
    suspend fun updateEndpoints(endpoints: List<ApiEndpoint>) {
        publishMutation(replaceEndpointsSnapshot(endpoints), "Cache updated")
    }

    /** Merges successful incremental results by fully qualified class name. */
    suspend fun updateEndpointsByClasses(classEndpoints: Map<String, List<ApiEndpoint>>) {
        publishMutation(
            updateEndpointsByClassesSnapshot(classEndpoints),
            "Cache updated for ${classEndpoints.size} classes"
        )
    }

    /** Removes cached endpoints for the supplied classes. */
    suspend fun removeEndpointsByClasses(classNames: Set<String>) {
        val mutation = synchronized(cacheLock) {
            val mutableMap = endpointsByClass.toMutableMap()
            classNames.forEach(mutableMap::remove)
            endpointsByClass = mutableMap.toMap()
            Mutation(endpointsByClass.values.flatten(), endpointsByClass.size)
        }
        publishMutation(mutation, "Removed endpoints for ${classNames.size} classes")
    }

    /**
     * Explicitly invalidates and clears the cache.
     *
     * Lifecycle stop operations intentionally do not call this method.
     */
    suspend fun invalidate() {
        synchronized(cacheLock) {
            cacheValid.set(false)
            endpointsByClass = emptyMap()
        }
        LOG.info("Cache invalidated")
    }

    /** Returns whether the retained data is currently marked valid. */
    fun isValid(): Boolean = cacheValid.get()

    /** Returns whether at least one successful cache update has completed. */
    fun isReady(): Boolean = cacheReady.isCompleted

    /** Returns whether [method] is represented in the retained snapshot. */
    fun containsMethod(method: com.intellij.psi.PsiMethod): Boolean {
        val className = method.containingClass?.qualifiedName ?: return false
        return endpointsByClass[className]?.any { it.sourceMethod == method } == true
    }

    internal fun replaceEndpointsSnapshot(endpoints: List<ApiEndpoint>): Mutation = synchronized(cacheLock) {
        endpointsByClass = endpoints.groupBy { it.className ?: "Unknown" }
        onCacheReadyLocked()
        Mutation(endpointsByClass.values.flatten(), endpointsByClass.size)
    }

    internal fun updateEndpointsByClassesSnapshot(
        classEndpoints: Map<String, List<ApiEndpoint>>
    ): Mutation = synchronized(cacheLock) {
        val mutableMap = endpointsByClass.toMutableMap()
        classEndpoints.forEach { (className, endpoints) ->
            if (endpoints.isEmpty()) {
                mutableMap.remove(className)
            } else {
                mutableMap[className] = endpoints.toList()
            }
        }
        endpointsByClass = mutableMap.toMap()
        onCacheReadyLocked()
        Mutation(endpointsByClass.values.flatten(), endpointsByClass.size)
    }

    internal suspend fun publishMutation(mutation: Mutation, message: String) {
        LOG.info("$message: ${mutation.endpoints.size} endpoints across ${mutation.classCount} classes")
        endpointsFlow.emit(mutation.endpoints)
    }

    private suspend fun awaitCacheReady() {
        if (!cacheReady.isCompleted) {
            cacheReady.await()
        }
    }

    private fun onCacheReadyLocked() {
        cacheValid.set(true)
        if (!cacheReady.isCompleted) {
            cacheReady.complete(Unit)
        }
    }

    companion object : IdeaLog {
        fun getInstance(project: Project): ApiIndex = project.service()
    }
}

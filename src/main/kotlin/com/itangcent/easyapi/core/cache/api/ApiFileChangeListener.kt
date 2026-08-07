package com.itangcent.easyapi.core.cache.api

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.MessageBusConnection
import com.itangcent.easyapi.core.feature.CoreFeatureIds
import com.itangcent.easyapi.core.ide.DumbModeHelper
import com.itangcent.easyapi.core.internal.threading.IdeDispatchers
import com.itangcent.easyapi.core.logging.IdeaLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Batches relevant `.java` and `.kt` VFS changes and forwards admitted work to the
 * lifecycle controller.
 *
 * Rapid changes are coalesced by a 30-second debounce, and events received during
 * dumb mode are rejected without creating pending work. The message-bus connection
 * is explicitly restartable. Admission occurs before pending files or a debounce
 * job are created, so disabled or stale generations cannot accumulate work.
 */
@Service(Service.Level.PROJECT)
class ApiFileChangeListener internal constructor(
    private val project: Project,
    private val requestSinkProvider: () -> ApiScanRequestSink,
    private val connectionFactory: () -> MessageBusConnection,
    dispatcher: CoroutineDispatcher,
    private val debounceDelayMs: Long
) : BulkFileListener, Disposable, IdeaLog {

    constructor(project: Project) : this(
        project = project,
        requestSinkProvider = { ApiScanLifecycleController.getInstance(project) },
        connectionFactory = {
            ApplicationManager.getApplication().messageBus.connect()
        },
        dispatcher = IdeDispatchers.Background,
        debounceDelayMs = DEFAULT_DEBOUNCE_DELAY_MS
    )

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LOG.warn("Uncaught coroutine exception in ApiFileChangeListener", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + exceptionHandler)
    private val stateLock = Any()
    private val pendingFiles = linkedSetOf<String>()

    @Volatile
    private var connection: MessageBusConnection? = null

    @Volatile
    private var activeGeneration: Long? = null

    private var debounceJob: Job? = null

    /** Starts one VFS subscription for [generation]. */
    fun start(generation: Long): Boolean = synchronized(stateLock) {
        if (connection != null) return false
        val newConnection = connectionFactory()
        try {
            newConnection.subscribe(VirtualFileManager.VFS_CHANGES, this)
            connection = newConnection
            activeGeneration = generation
            LOG.info(
                "VFS listener featureId=${CoreFeatureIds.API_SCANNING.value} " +
                    "target=true generation=$generation source=controller result=started"
            )
            true
        } catch (e: Exception) {
            try {
                newConnection.disconnect()
            } catch (cleanupFailure: Exception) {
                LOG.warn("Failed to clean up VFS listener after start failure", cleanupFailure)
            }
            LOG.warn("Failed to start VFS API listener generation=$generation", e)
            throw e
        }
    }

    /** Compatibility start that uses the controller's current generation. */
    fun start(): Boolean = start(ApiScanLifecycleController.getInstance(project).snapshot().generation)

    /** Stops the subscription and synchronously clears all pending debounce state. */
    fun stop(): Boolean {
        val connectionToClose: MessageBusConnection?
        val jobToCancel: Job?
        val generation: Long?
        synchronized(stateLock) {
            connectionToClose = connection
            jobToCancel = debounceJob
            generation = activeGeneration
            connection = null
            activeGeneration = null
            debounceJob = null
            pendingFiles.clear()
        }
        jobToCancel?.cancel()
        if (connectionToClose != null) {
            try {
                connectionToClose.disconnect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.warn("Failed to disconnect VFS API listener generation=$generation", e)
            }
            LOG.info(
                "VFS listener featureId=${CoreFeatureIds.API_SCANNING.value} " +
                    "target=false generation=$generation source=controller result=stopped"
            )
        }
        return connectionToClose != null
    }

    override fun after(events: MutableList<out VFileEvent>) {
        if (events.isEmpty()) return
        val changedFiles = events.mapNotNull { event ->
            val file = event.file ?: return@mapNotNull null
            if (file.name.endsWith(".java") || file.name.endsWith(".kt")) {
                file.path.takeIf(String::isNotEmpty)
            } else {
                null
            }
        }
        onFilesChanged(changedFiles)
    }

    internal fun onFilesChanged(filePaths: List<String>): ApiScanRequestDecision {
        val distinctPaths = filePaths.filter(String::isNotEmpty).distinct()
        if (distinctPaths.isEmpty()) return ApiScanRequestDecision.REJECTED_DISABLED

        val sink = requestSinkProvider()
        val admission = sink.admitVfs() ?: return ApiScanRequestDecision.REJECTED_DISABLED
        if (DumbModeHelper.isDumb(project)) {
            LOG.info(
                "VFS API request featureId=${CoreFeatureIds.API_SCANNING.value} " +
                    "target=true generation=${admission.generation} source=vfs " +
                    "result=rejected_dumb_mode"
            )
            return ApiScanRequestDecision.REJECTED_DUMB_MODE
        }
        synchronized(stateLock) {
            val generation = activeGeneration
            if (connection == null || generation == null) {
                return ApiScanRequestDecision.REJECTED_DISABLED
            }
            if (generation != admission.generation) {
                return ApiScanRequestDecision.REJECTED_STALE_GENERATION
            }

            pendingFiles.addAll(distinctPaths)
            debounceJob?.cancel()
            debounceJob = scope.launch {
                delay(debounceDelayMs)
                flushPending(admission)
            }
        }
        return ApiScanRequestDecision.ACCEPTED
    }

    internal fun isStarted(): Boolean = connection != null

    internal fun pendingFileCount(): Int = synchronized(stateLock) { pendingFiles.size }

    internal fun hasDebounceWork(): Boolean = synchronized(stateLock) {
        debounceJob?.isActive == true
    }

    private suspend fun flushPending(admission: ApiScanAdmission) {
        val files = synchronized(stateLock) {
            if (activeGeneration != admission.generation || connection == null) {
                pendingFiles.clear()
                debounceJob = null
                return
            }
            pendingFiles.toList().also {
                pendingFiles.clear()
                debounceJob = null
            }
        }
        if (files.isEmpty()) return

        try {
            requestSinkProvider().submitVfs(admission, files)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn(
                "Failed to submit VFS API changes generation=${admission.generation}",
                e
            )
        }
    }

    override fun dispose() {
        try {
            stop()
        } finally {
            scope.cancel()
        }
    }

    companion object {
        private const val DEFAULT_DEBOUNCE_DELAY_MS = 30_000L

        fun getInstance(project: Project): ApiFileChangeListener = project.service()
    }
}

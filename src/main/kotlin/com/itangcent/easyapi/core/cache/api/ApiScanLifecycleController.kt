package com.itangcent.easyapi.core.cache.api

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import com.itangcent.easyapi.core.cache.VcsBranchChangeListener
import com.itangcent.easyapi.core.feature.CoreFeatureIds
import com.itangcent.easyapi.core.feature.FeatureStateChange
import com.itangcent.easyapi.core.feature.FeatureStateEvents
import com.itangcent.easyapi.core.feature.FeatureStateService
import com.itangcent.easyapi.core.internal.threading.IdeDispatchers
import com.itangcent.easyapi.core.logging.IdeaLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Stable lifecycle phases exposed for diagnostics and tests. */
enum class ApiScanLifecycleState {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ONE_SHOT_SCANNING
}

/** Effective feature state consumed by the scan lifecycle. */
data class ApiScanFeatureTarget(
    val scanningEnabled: Boolean,
    val autoScanningEnabled: Boolean,
    val editorIntegrationEnabled: Boolean
)

/** Immutable view of the controller's serialized lifecycle state. */
data class ApiScanLifecycleSnapshot(
    val state: ApiScanLifecycleState,
    val target: ApiScanFeatureTarget,
    val revision: Long,
    val generation: Long
) {
    val isRunning: Boolean
        get() = state == ApiScanLifecycleState.RUNNING
}

/** Immediate admission result for an automatic request. */
enum class ApiScanRequestDecision {
    ACCEPTED,
    REJECTED_DISABLED,
    REJECTED_AUTO_DISABLED,
    REJECTED_EDITOR_DISABLED,
    REJECTED_DUMB_MODE,
    REJECTED_STALE_GENERATION,
    REJECTED_DISPOSED
}

/** Generation captured before a VFS listener creates pending work. */
data class ApiScanAdmission(val generation: Long)

internal interface ApiScanRequestSink {
    fun admitVfs(): ApiScanAdmission?
    fun submitVfs(admission: ApiScanAdmission, filePaths: List<String>): ApiScanRequestDecision
    fun requestVcs(branchName: String): ApiScanRequestDecision
}

internal fun interface ApiScanFeatureStateProvider {
    fun currentTarget(): ApiScanFeatureTarget
}

internal interface ApiScanLifecycleResources {
    suspend fun startManager(triggerInitialScan: Boolean): Long
    fun startVfs(generation: Long)
    fun startVcs(generation: Long)
    fun stopVfs()
    fun stopVcs()
    suspend fun stopManager(): Long
    fun requestFull(generation: Long, source: String): Deferred<ApiScanResult>
    fun requestIncremental(
        generation: Long,
        filePaths: List<String>,
        source: String
    ): Deferred<ApiScanResult>

    fun runOneShotFull(source: String): Deferred<ApiScanResult>
    fun stopImmediately()
}

/** One manual refresh operation with identity for completion isolation. */
internal data class ApiScanManualOperation(
    val id: Long,
    val work: Deferred<ApiScanResult>
)

/**
 * Pure serialized transition seam used by the project controller and fake-resource tests.
 */
internal class ApiScanLifecycleMachine(
    private val resources: ApiScanLifecycleResources,
    private val onSnapshot: (ApiScanLifecycleSnapshot, String, String) -> Unit = { _, _, _ -> },
    private val onFailure: (String, Throwable) -> Unit = { _, _ -> }
) {
    private var current = ApiScanLifecycleSnapshot(
        state = ApiScanLifecycleState.STOPPED,
        target = ApiScanFeatureTarget(false, false, false),
        revision = 0L,
        generation = 0L
    )
    private var manualOperationCounter = 0L
    private var activeOneShotOperationId: Long? = null

    fun snapshot(): ApiScanLifecycleSnapshot = current

    suspend fun reconcile(
        target: ApiScanFeatureTarget,
        revision: Long,
        source: String
    ): ApiScanLifecycleSnapshot {
        if (revision < current.revision) return current
        update(current.copy(target = target, revision = revision), source, "target-updated")

        if (current.state == ApiScanLifecycleState.ONE_SHOT_SCANNING) {
            stopContinuous("$source-cancel-one-shot")
        }
        if (target.scanningEnabled) {
            if (current.state != ApiScanLifecycleState.RUNNING) {
                startWithRecovery(source)
            }
        } else if (current.state != ApiScanLifecycleState.STOPPED) {
            stopContinuous(source)
        }
        return current
    }

    fun beginManualRefresh(source: String): ApiScanManualOperation {
        val operationId = ++manualOperationCounter
        if (current.state == ApiScanLifecycleState.RUNNING) {
            return ApiScanManualOperation(
                operationId,
                resources.requestFull(current.generation, source)
            )
        }
        if (current.state == ApiScanLifecycleState.ONE_SHOT_SCANNING) {
            return ApiScanManualOperation(
                operationId,
                completedResult(
                    ApiScanResult.Rejected(
                        current.generation,
                        ApiScanRejectionReason.NO_ACTIVE_SESSION
                    )
                )
            )
        }

        activeOneShotOperationId = operationId
        update(
            current.copy(state = ApiScanLifecycleState.ONE_SHOT_SCANNING),
            source,
            "one-shot-started"
        )
        return ApiScanManualOperation(operationId, resources.runOneShotFull(source))
    }

    fun completeManualRefresh(operationId: Long, source: String) {
        if (activeOneShotOperationId != operationId) return
        activeOneShotOperationId = null
        if (current.state == ApiScanLifecycleState.ONE_SHOT_SCANNING) {
            update(
                current.copy(state = ApiScanLifecycleState.STOPPED),
                source,
                "one-shot-stopped"
            )
        }
    }

    fun abortManualRefresh(source: String) {
        activeOneShotOperationId?.let { completeManualRefresh(it, source) }
    }

    fun requestVfs(
        generation: Long,
        filePaths: List<String>,
        source: String
    ): Deferred<ApiScanResult> {
        if (
            current.state != ApiScanLifecycleState.RUNNING ||
            !current.target.scanningEnabled ||
            !current.target.autoScanningEnabled
        ) {
            return completedResult(
                ApiScanResult.Rejected(
                    current.generation,
                    ApiScanRejectionReason.NO_ACTIVE_SESSION
                )
            )
        }
        if (generation != current.generation) {
            return completedResult(
                ApiScanResult.Rejected(
                    current.generation,
                    ApiScanRejectionReason.STALE_GENERATION
                )
            )
        }
        return resources.requestIncremental(generation, filePaths, source)
    }

    fun requestVcs(generation: Long, source: String): Deferred<ApiScanResult> {
        if (current.state != ApiScanLifecycleState.RUNNING || !current.target.scanningEnabled) {
            return completedResult(
                ApiScanResult.Rejected(
                    current.generation,
                    ApiScanRejectionReason.NO_ACTIVE_SESSION
                )
            )
        }
        if (generation != current.generation) {
            return completedResult(
                ApiScanResult.Rejected(
                    current.generation,
                    ApiScanRejectionReason.STALE_GENERATION
                )
            )
        }
        return resources.requestFull(generation, source)
    }

    fun requestGutter(
        generation: Long,
        filePaths: List<String>,
        source: String
    ): Deferred<ApiScanResult> {
        if (
            current.state != ApiScanLifecycleState.RUNNING ||
            !current.target.scanningEnabled ||
            !current.target.editorIntegrationEnabled
        ) {
            return completedResult(
                ApiScanResult.Rejected(
                    current.generation,
                    ApiScanRejectionReason.NO_ACTIVE_SESSION
                )
            )
        }
        if (generation != current.generation) {
            return completedResult(
                ApiScanResult.Rejected(
                    current.generation,
                    ApiScanRejectionReason.STALE_GENERATION
                )
            )
        }
        return resources.requestIncremental(generation, filePaths, source)
    }

    private fun completedResult(result: ApiScanResult): Deferred<ApiScanResult> =
        CompletableDeferred<ApiScanResult>().apply { complete(result) }

    private suspend fun startWithRecovery(source: String) {
        repeat(2) { attempt ->
            var managerStarted = false
            var vfsStarted = false
            var vcsStarted = false
            update(current.copy(state = ApiScanLifecycleState.STARTING), source, "starting")
            try {
                val generation = resources.startManager(triggerInitialScan = true)
                managerStarted = true
                update(current.copy(generation = generation), source, "manager-started")
                resources.startVfs(generation)
                vfsStarted = true
                resources.startVcs(generation)
                vcsStarted = true
                update(current.copy(state = ApiScanLifecycleState.RUNNING), source, "running")
                return
            } catch (e: CancellationException) {
                cleanupPartialStart(managerStarted, vfsStarted, vcsStarted, source)
                throw e
            } catch (e: Exception) {
                onFailure(
                    "API scan lifecycle start failed source=$source attempt=${attempt + 1}",
                    e
                )
                cleanupPartialStart(managerStarted, vfsStarted, vcsStarted, source)
            }
        }
    }

    private suspend fun cleanupPartialStart(
        managerStarted: Boolean,
        vfsStarted: Boolean,
        vcsStarted: Boolean,
        source: String
    ) {
        update(current.copy(state = ApiScanLifecycleState.STOPPING), source, "start-cleanup")
        if (vfsStarted) stopResource("VFS listener", source, resources::stopVfs)
        if (vcsStarted) stopResource("VCS listener", source, resources::stopVcs)
        if (managerStarted) {
            try {
                val generation = resources.stopManager()
                update(current.copy(generation = generation), source, "manager-stopped")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure("Failed to stop API scan manager source=$source", e)
            }
        }
        update(current.copy(state = ApiScanLifecycleState.STOPPED), source, "stopped")
    }

    private suspend fun stopContinuous(source: String) {
        update(current.copy(state = ApiScanLifecycleState.STOPPING), source, "stopping")
        stopResource("VFS listener", source, resources::stopVfs)
        stopResource("VCS listener", source, resources::stopVcs)
        try {
            val generation = resources.stopManager()
            update(current.copy(generation = generation), source, "manager-stopped")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure("Failed to stop API scan manager source=$source", e)
        }
        update(current.copy(state = ApiScanLifecycleState.STOPPED), source, "stopped")
    }

    private fun stopResource(name: String, source: String, stop: () -> Unit) {
        try {
            stop()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure("Failed to stop $name source=$source", e)
        }
    }

    private fun update(snapshot: ApiScanLifecycleSnapshot, source: String, result: String) {
        current = snapshot
        onSnapshot(snapshot, source, result)
    }
}

/**
 * Serializes all API scan lifecycle transitions and request admission.
 *
 * The startup activity runs only once per project, so this controller also bridges
 * later persisted feature-state changes: enabling scanning can start the manager
 * and listeners without a project restart, while disabling it stops continuous
 * work without discarding the retained index. A manual refresh can use an isolated
 * one-shot session when continuous scanning is stopped.
 *
 * The feature-state message-bus callback only enqueues reconciliation. Resource
 * transitions run on [IdeDispatchers.Background] in a dedicated supervised scope.
 */
@Service(Service.Level.PROJECT)
class ApiScanLifecycleController internal constructor(
    private val project: Project,
    private val stateProvider: ApiScanFeatureStateProvider,
    private val resources: ApiScanLifecycleResources,
    dispatcher: CoroutineDispatcher
) : Disposable, IdeaLog, ApiScanRequestSink {

    constructor(project: Project) : this(
        project = project,
        stateProvider = ProjectApiScanFeatureStateProvider(project),
        resources = ProjectApiScanLifecycleResources(project),
        dispatcher = IdeDispatchers.Background
    )

    private sealed interface Command {
        data class Reconcile(
            val revision: Long,
            val source: String,
            val completion: CompletableDeferred<ApiScanLifecycleSnapshot>?
        ) : Command

        data class ManualRefresh(
            val source: String,
            val completion: CompletableDeferred<ApiScanResult>
        ) : Command

        data class ManualFinished(
            val operationId: Long,
            val source: String,
            val result: ApiScanResult,
            val completion: CompletableDeferred<ApiScanResult>
        ) : Command

        data class Vfs(
            val admission: ApiScanAdmission,
            val filePaths: List<String>
        ) : Command

        data class Vcs(
            val generation: Long,
            val branchName: String
        ) : Command

        data class Gutter(
            val generation: Long,
            val filePaths: List<String>
        ) : Command

        data class Barrier(
            val completion: CompletableDeferred<ApiScanLifecycleSnapshot>
        ) : Command
    }

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        LOG.warn("Uncaught coroutine exception in ApiScanLifecycleController", throwable)
    }
    private val scope = CoroutineScope(SupervisorJob() + dispatcher + exceptionHandler)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val revisionCounter = AtomicLong(0L)
    private val pendingCompletions = ConcurrentHashMap.newKeySet<CompletableDeferred<*>>()

    @Volatile
    private var disposed = false

    @Volatile
    private var observableSnapshot = ApiScanLifecycleSnapshot(
        state = ApiScanLifecycleState.STOPPED,
        target = ApiScanFeatureTarget(false, false, false),
        revision = 0L,
        generation = 0L
    )

    private val machine = ApiScanLifecycleMachine(
        resources = resources,
        onSnapshot = { snapshot, source, result ->
            observableSnapshot = snapshot
            LOG.info(
                "API scan lifecycle featureId=${CoreFeatureIds.API_SCANNING.value} " +
                    "target=${snapshot.target.scanningEnabled} generation=${snapshot.generation} " +
                    "source=$source result=$result"
            )
        },
        onFailure = { message, throwable -> LOG.warn(message, throwable) }
    )

    private val featureConnection: MessageBusConnection

    init {
        scope.launch { processCommands() }
        featureConnection = project.messageBus.connect()
        featureConnection.subscribe(
            FeatureStateEvents.TOPIC,
            FeatureStateEvents { change -> enqueueFeatureChange(change) }
        )
    }

    /** Returns the most recently published lifecycle snapshot without waiting. */
    fun snapshot(): ApiScanLifecycleSnapshot = observableSnapshot

    /** Enqueues startup reconciliation and returns an observable completion. */
    fun reconcileInitial(): Deferred<ApiScanLifecycleSnapshot> = enqueueReconcile("initial")

    /** Enqueues reconciliation against the latest successfully saved feature state. */
    fun reconcileSaved(source: String = "settings"): Deferred<ApiScanLifecycleSnapshot> =
        enqueueReconcile(source)

    /**
     * Requests a user-initiated full refresh.
     *
     * Disabled scanning uses an isolated one-shot manager session and does not
     * establish VFS or VCS subscriptions.
     */
    fun manualRefresh(): Deferred<ApiScanResult> {
        val completion = trackedCompletion<ApiScanResult>()
        if (!enqueue(Command.ManualRefresh("manual-refresh", completion))) {
            completion.complete(
                ApiScanResult.Rejected(
                    observableSnapshot.generation,
                    ApiScanRejectionReason.SESSION_STOPPED
                )
            )
        }
        return completion
    }

    /** Enqueues a controlled editor fallback request when editor integration is effective. */
    fun requestGutterIncremental(filePaths: List<String>): ApiScanRequestDecision {
        val snapshot = observableSnapshot
        val decision = when {
            disposed -> ApiScanRequestDecision.REJECTED_DISPOSED
            snapshot.state != ApiScanLifecycleState.RUNNING || !snapshot.target.scanningEnabled ->
                ApiScanRequestDecision.REJECTED_DISABLED

            !snapshot.target.editorIntegrationEnabled ->
                ApiScanRequestDecision.REJECTED_EDITOR_DISABLED

            else -> ApiScanRequestDecision.ACCEPTED
        }
        if (decision == ApiScanRequestDecision.ACCEPTED) {
            enqueue(Command.Gutter(snapshot.generation, filePaths.distinct()))
        }
        logAdmission("gutter", snapshot, decision)
        return decision
    }

    /** Returns a completion that resolves after all commands already in the FIFO queue. */
    fun awaitIdle(): Deferred<ApiScanLifecycleSnapshot> {
        val completion = trackedCompletion<ApiScanLifecycleSnapshot>()
        if (!enqueue(Command.Barrier(completion))) {
            completion.complete(observableSnapshot)
        }
        return completion
    }

    /** Returns a generation token only when VFS automatic work is currently admitted. */
    override fun admitVfs(): ApiScanAdmission? {
        val snapshot = observableSnapshot
        val decision = when {
            disposed -> ApiScanRequestDecision.REJECTED_DISPOSED
            snapshot.state != ApiScanLifecycleState.RUNNING || !snapshot.target.scanningEnabled ->
                ApiScanRequestDecision.REJECTED_DISABLED

            !snapshot.target.autoScanningEnabled ->
                ApiScanRequestDecision.REJECTED_AUTO_DISABLED

            else -> ApiScanRequestDecision.ACCEPTED
        }
        logAdmission("vfs", snapshot, decision)
        return if (decision == ApiScanRequestDecision.ACCEPTED) {
            ApiScanAdmission(snapshot.generation)
        } else {
            null
        }
    }

    /** Submits debounced VFS paths only if [admission] still matches the running generation. */
    override fun submitVfs(
        admission: ApiScanAdmission,
        filePaths: List<String>
    ): ApiScanRequestDecision {
        val snapshot = observableSnapshot
        val decision = when {
            disposed -> ApiScanRequestDecision.REJECTED_DISPOSED
            snapshot.state != ApiScanLifecycleState.RUNNING || !snapshot.target.scanningEnabled ->
                ApiScanRequestDecision.REJECTED_DISABLED

            !snapshot.target.autoScanningEnabled ->
                ApiScanRequestDecision.REJECTED_AUTO_DISABLED

            admission.generation != snapshot.generation ->
                ApiScanRequestDecision.REJECTED_STALE_GENERATION

            else -> ApiScanRequestDecision.ACCEPTED
        }
        if (decision == ApiScanRequestDecision.ACCEPTED) {
            enqueue(Command.Vfs(admission, filePaths.distinct()))
        }
        logAdmission("vfs-debounce", snapshot, decision)
        return decision
    }

    /** Admits a VCS full-scan request only while continuous scanning is running. */
    override fun requestVcs(branchName: String): ApiScanRequestDecision {
        val snapshot = observableSnapshot
        val decision = when {
            disposed -> ApiScanRequestDecision.REJECTED_DISPOSED
            snapshot.state != ApiScanLifecycleState.RUNNING || !snapshot.target.scanningEnabled ->
                ApiScanRequestDecision.REJECTED_DISABLED

            else -> ApiScanRequestDecision.ACCEPTED
        }
        if (decision == ApiScanRequestDecision.ACCEPTED) {
            enqueue(Command.Vcs(snapshot.generation, branchName))
        }
        logAdmission("vcs", snapshot, decision)
        return decision
    }

    private fun enqueueFeatureChange(change: FeatureStateChange) {
        val relevant = change.entries.any { delta ->
            delta.id == CoreFeatureIds.API_SCANNING ||
                delta.id == CoreFeatureIds.AUTO_SCANNING ||
                delta.id == CoreFeatureIds.EDITOR_INTEGRATION
        }
        if (!relevant) return
        val revision = revisionCounter.incrementAndGet()
        enqueue(Command.Reconcile(revision, "feature-${change.source.name.lowercase()}", null))
    }

    private fun enqueueReconcile(source: String): Deferred<ApiScanLifecycleSnapshot> {
        val completion = trackedCompletion<ApiScanLifecycleSnapshot>()
        val revision = revisionCounter.incrementAndGet()
        if (!enqueue(Command.Reconcile(revision, source, completion))) {
            completion.complete(observableSnapshot)
        }
        return completion
    }

    private fun <T> trackedCompletion(): CompletableDeferred<T> =
        CompletableDeferred<T>().also { completion ->
            pendingCompletions += completion
            completion.invokeOnCompletion { pendingCompletions -= completion }
        }

    private fun enqueue(command: Command): Boolean =
        !disposed && commands.trySend(command).isSuccess

    private suspend fun processCommands() {
        for (command in commands) {
            try {
                when (command) {
                    is Command.Reconcile -> processReconcile(command)
                    is Command.ManualRefresh -> {
                        val operation = machine.beginManualRefresh(command.source)
                        observeManualRefresh(command, operation)
                    }

                    is Command.ManualFinished -> {
                        machine.completeManualRefresh(command.operationId, command.source)
                        logResult(command.source, command.result)
                        command.completion.complete(command.result)
                    }

                    is Command.Vfs -> {
                        observeResult(
                            "vfs",
                            machine.requestVfs(
                                command.admission.generation,
                                command.filePaths,
                                "vfs"
                            )
                        )
                    }

                    is Command.Vcs -> {
                        observeResult(
                            "vcs",
                            machine.requestVcs(
                                command.generation,
                                "vcs:${command.branchName}"
                            )
                        )
                    }

                    is Command.Gutter -> {
                        observeResult(
                            "gutter",
                            machine.requestGutter(
                                command.generation,
                                command.filePaths,
                                "gutter"
                            )
                        )
                    }

                    is Command.Barrier -> command.completion.complete(machine.snapshot())
                }
            } catch (e: CancellationException) {
                cancelCompletion(command, e)
                throw e
            } catch (e: Exception) {
                LOG.warn("API scan lifecycle command failed command=${command.javaClass.simpleName}", e)
                completeAfterFailure(command)
            }
        }
    }

    private suspend fun processReconcile(command: Command.Reconcile) {
        val target = stateProvider.currentTarget()
        val snapshot = machine.reconcile(target, command.revision, command.source)
        command.completion?.complete(snapshot)
    }

    private fun observeManualRefresh(
        command: Command.ManualRefresh,
        operation: ApiScanManualOperation
    ) {
        scope.launch {
            try {
                val result = operation.work.await()
                if (!enqueue(
                        Command.ManualFinished(
                            operation.id,
                            command.source,
                            result,
                            command.completion
                        )
                    )
                ) {
                    command.completion.complete(result)
                }
            } catch (e: CancellationException) {
                command.completion.cancel(e)
                throw e
            } catch (e: Exception) {
                LOG.warn("Manual API refresh failed source=${command.source}", e)
                val result = ApiScanResult.Failed(observableSnapshot.generation, e)
                if (!enqueue(
                        Command.ManualFinished(
                            operation.id,
                            command.source,
                            result,
                            command.completion
                        )
                    )
                ) {
                    command.completion.complete(result)
                }
            }
        }
    }

    private fun observeResult(source: String, work: Deferred<ApiScanResult>) {
        scope.launch {
            try {
                logResult(source, work.await())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.warn("API scan request observation failed source=$source", e)
            }
        }
    }

    private fun cancelCompletion(command: Command, cause: CancellationException) {
        when (command) {
            is Command.Reconcile -> command.completion?.cancel(cause)
            is Command.ManualRefresh -> command.completion.cancel(cause)
            is Command.ManualFinished -> command.completion.cancel(cause)
            is Command.Barrier -> command.completion.cancel(cause)
            else -> Unit
        }
    }

    private fun completeAfterFailure(command: Command) {
        when (command) {
            is Command.Reconcile -> command.completion?.complete(machine.snapshot())
            is Command.ManualRefresh -> {
                machine.abortManualRefresh(command.source)
                command.completion.complete(
                    ApiScanResult.Rejected(
                        machine.snapshot().generation,
                        ApiScanRejectionReason.SESSION_STOPPED
                    )
                )
            }

            is Command.ManualFinished -> {
                machine.completeManualRefresh(command.operationId, command.source)
                command.completion.complete(command.result)
            }

            is Command.Barrier -> command.completion.complete(machine.snapshot())
            else -> Unit
        }
    }

    private fun logAdmission(
        source: String,
        snapshot: ApiScanLifecycleSnapshot,
        decision: ApiScanRequestDecision
    ) {
        LOG.info(
            "API scan admission featureId=${CoreFeatureIds.API_SCANNING.value} " +
                "target=${snapshot.target.scanningEnabled} generation=${snapshot.generation} " +
                "source=$source result=${decision.name.lowercase()}"
        )
    }

    private fun logResult(source: String, result: ApiScanResult) {
        when (result) {
            is ApiScanResult.Failed -> LOG.warn(
                "API scan request featureId=${CoreFeatureIds.API_SCANNING.value} " +
                    "target=${observableSnapshot.target.scanningEnabled} " +
                    "generation=${result.generation} source=$source result=failed",
                result.throwable
            )

            else -> LOG.info(
                "API scan request featureId=${CoreFeatureIds.API_SCANNING.value} " +
                    "target=${observableSnapshot.target.scanningEnabled} " +
                    "generation=${result.generation} source=$source " +
                    "result=${result.javaClass.simpleName.lowercase()}"
            )
        }
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        observableSnapshot = observableSnapshot.copy(state = ApiScanLifecycleState.STOPPED)
        try {
            featureConnection.disconnect()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Failed to disconnect API scan feature-state listener", e)
        } finally {
            commands.close()
            val cancellation = CancellationException("API scan lifecycle controller disposed")
            pendingCompletions.toList().forEach { it.cancel(cancellation) }
            pendingCompletions.clear()
            scope.cancel(cancellation)
            try {
                resources.stopImmediately()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.warn("Failed to stop API scan resources during disposal", e)
            }
        }
    }

    companion object {
        fun getInstance(project: Project): ApiScanLifecycleController = project.service()
    }
}

private class ProjectApiScanFeatureStateProvider(
    private val project: Project
) : ApiScanFeatureStateProvider {
    override fun currentTarget(): ApiScanFeatureTarget {
        val states = FeatureStateService.getInstance(project).states()
        return ApiScanFeatureTarget(
            scanningEnabled = states[CoreFeatureIds.API_SCANNING]?.effectiveEnabled == true,
            autoScanningEnabled = states[CoreFeatureIds.AUTO_SCANNING]?.effectiveEnabled == true,
            editorIntegrationEnabled = states[CoreFeatureIds.EDITOR_INTEGRATION]?.effectiveEnabled == true
        )
    }
}

private class ProjectApiScanLifecycleResources(
    private val project: Project
) : ApiScanLifecycleResources {
    private val manager: ApiIndexManager
        get() = ApiIndexManager.getInstance(project)
    private val vfsListener: ApiFileChangeListener
        get() = ApiFileChangeListener.getInstance(project)
    private val vcsListener: VcsBranchChangeListener
        get() = VcsBranchChangeListener.getInstance(project)

    override suspend fun startManager(triggerInitialScan: Boolean): Long =
        manager.startContinuous(triggerInitialScan)

    override fun startVfs(generation: Long) {
        vfsListener.start(generation)
    }

    override fun startVcs(generation: Long) {
        vcsListener.start(generation)
    }

    override fun stopVfs() {
        vfsListener.stop()
    }

    override fun stopVcs() {
        vcsListener.stop()
    }

    override suspend fun stopManager(): Long = manager.stopContinuous()

    override fun requestFull(generation: Long, source: String): Deferred<ApiScanResult> =
        manager.requestFullScan(generation, source)

    override fun requestIncremental(
        generation: Long,
        filePaths: List<String>,
        source: String
    ): Deferred<ApiScanResult> = manager.requestIncrementalScan(filePaths, generation, source)

    override fun runOneShotFull(source: String): Deferred<ApiScanResult> =
        manager.runOneShotFullScanAsync(source)

    override fun stopImmediately() {
        vfsListener.stop()
        vcsListener.stop()
        manager.stop()
    }
}

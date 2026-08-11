package com.itangcent.easyapi.core.cache

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.BranchChangeListener
import com.intellij.util.messages.MessageBusConnection
import com.itangcent.easyapi.core.cache.api.ApiScanLifecycleController
import com.itangcent.easyapi.core.cache.api.ApiScanRequestDecision
import com.itangcent.easyapi.core.cache.api.ApiScanRequestSink
import com.itangcent.easyapi.core.feature.CoreFeatureIds
import com.itangcent.easyapi.core.logging.IdeaLog
import kotlinx.coroutines.CancellationException

/**
 * Converts VCS branch changes into lifecycle-controlled full-scan requests.
 *
 * When the user switches branches in IDEA, VFS file-change events may not fire
 * for every affected file. A branch change therefore requests a full scan so
 * the retained API index reflects the new branch.
 *
 * The connection is stored explicitly so stop and restart are immediate and
 * idempotent within the same project service instance.
 *
 * @see BranchChangeListener for the platform callback.
 */
@Service(Service.Level.PROJECT)
class VcsBranchChangeListener internal constructor(
    private val project: Project,
    private val requestSinkProvider: () -> ApiScanRequestSink,
    private val connectionFactory: () -> MessageBusConnection
) : BranchChangeListener, Disposable, IdeaLog {

    constructor(project: Project) : this(
        project = project,
        requestSinkProvider = { ApiScanLifecycleController.getInstance(project) },
        connectionFactory = { project.messageBus.connect() }
    )

    private val stateLock = Any()

    @Volatile
    private var connection: MessageBusConnection? = null

    @Volatile
    private var activeGeneration: Long? = null

    /** Starts one VCS subscription for [generation]. */
    fun start(generation: Long): Boolean = synchronized(stateLock) {
        if (connection != null) return false
        val newConnection = connectionFactory()
        try {
            newConnection.subscribe(BranchChangeListener.VCS_BRANCH_CHANGED, this)
            connection = newConnection
            activeGeneration = generation
            LOG.info(
                "VCS listener featureId=${CoreFeatureIds.API_SCANNING.value} " +
                    "target=true generation=$generation source=controller result=started"
            )
            true
        } catch (e: Exception) {
            try {
                newConnection.disconnect()
            } catch (cleanupFailure: Exception) {
                LOG.warn("Failed to clean up VCS listener after start failure", cleanupFailure)
            }
            LOG.warn("Failed to start VCS API listener generation=$generation", e)
            throw e
        }
    }

    /** Compatibility start that uses the controller's current generation. */
    fun start(): Boolean = start(ApiScanLifecycleController.getInstance(project).snapshot().generation)

    /** Stops the current VCS subscription immediately. */
    fun stop(): Boolean {
        val connectionToClose: MessageBusConnection?
        val generation: Long?
        synchronized(stateLock) {
            connectionToClose = connection
            generation = activeGeneration
            connection = null
            activeGeneration = null
        }
        if (connectionToClose != null) {
            try {
                connectionToClose.disconnect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.warn("Failed to disconnect VCS API listener generation=$generation", e)
            }
            LOG.info(
                "VCS listener featureId=${CoreFeatureIds.API_SCANNING.value} " +
                    "target=false generation=$generation source=controller result=stopped"
            )
        }
        return connectionToClose != null
    }

    override fun branchWillChange(branchName: String) = Unit

    override fun branchHasChanged(branchName: String) {
        val result = requestSinkProvider().requestVcs(branchName)
        LOG.info(
            "VCS API request featureId=${CoreFeatureIds.API_SCANNING.value} " +
                "target=${result == ApiScanRequestDecision.ACCEPTED} " +
                "generation=$activeGeneration source=vcs:$branchName " +
                "result=${result.name.lowercase()}"
        )
    }

    internal fun isStarted(): Boolean = connection != null

    override fun dispose() {
        stop()
    }

    companion object {
        fun getInstance(project: Project): VcsBranchChangeListener = project.service()
    }
}

package com.itangcent.easyapi.core.feature

import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

/** Identifies the workflow that produced a state change. */
enum class FeatureStateChangeSource {
    SETTINGS_APPLY,
    STARTUP_RECONCILIATION
}

/** One changed feature with its states before and after persistence. */
data class FeatureStateDelta(
    val id: FeatureId,
    val before: ResolvedFeatureState,
    val after: ResolvedFeatureState
) {
    init {
        require(before.id == id && after.id == id) { "Feature delta states must use the delta id" }
    }
}

/** A typed collection of feature state changes. */
data class FeatureStateChange(
    val source: FeatureStateChangeSource,
    val entries: List<FeatureStateDelta>
) {
    companion object {
        /** Builds a change containing only desired or effective state differences. */
        fun between(
            source: FeatureStateChangeSource,
            before: Map<FeatureId, ResolvedFeatureState>,
            after: Map<FeatureId, ResolvedFeatureState>
        ): FeatureStateChange {
            val orderedIds = linkedSetOf<FeatureId>().apply {
                addAll(before.keys)
                addAll(after.keys)
            }
            val deltas = orderedIds.mapNotNull { id ->
                val beforeState = before[id] ?: return@mapNotNull null
                val afterState = after[id] ?: return@mapNotNull null
                if (
                    beforeState.desiredEnabled == afterState.desiredEnabled &&
                    beforeState.effectiveEnabled == afterState.effectiveEnabled
                ) {
                    null
                } else {
                    FeatureStateDelta(id, beforeState, afterState)
                }
            }
            return FeatureStateChange(source, deltas)
        }
    }
}

/** Project message-bus listener for successfully persisted feature changes. */
fun interface FeatureStateEvents {
    fun featureStateChanged(change: FeatureStateChange)

    companion object {
        val TOPIC: Topic<FeatureStateEvents> = Topic.create(
            "EasyApi Feature State Changed",
            FeatureStateEvents::class.java
        )
    }
}

/** Publishes a non-empty typed feature-state change. */
fun Project.publishFeatureStateChange(change: FeatureStateChange) {
    if (change.entries.isNotEmpty()) {
        messageBus.syncPublisher(FeatureStateEvents.TOPIC).featureStateChanged(change)
    }
}

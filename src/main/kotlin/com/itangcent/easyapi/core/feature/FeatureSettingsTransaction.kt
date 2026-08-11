package com.itangcent.easyapi.core.feature

import com.itangcent.easyapi.core.settings.module.GeneralSettings

/** Holds an isolated desired-state draft for one settings editing session. */
class FeatureSettingsTransaction(
    val registrySnapshot: FeatureRegistrySnapshot,
    settings: GeneralSettings,
    private val resolver: FeatureStateResolver = FeatureStateResolver()
) {
    private val initialDesiredById: Map<FeatureId, Boolean> =
        resolver.readDesiredStates(registrySnapshot, settings)
    private val draftDesiredById: LinkedHashMap<FeatureId, Boolean> =
        LinkedHashMap(initialDesiredById)

    val initialDesiredStates: Map<FeatureId, Boolean>
        get() = initialDesiredById.toMap()

    val draftDesiredStates: Map<FeatureId, Boolean>
        get() = draftDesiredById.toMap()

    fun desiredState(id: FeatureId): Boolean =
        draftDesiredById[id] ?: throw IllegalArgumentException("Unknown feature id=${id.value}")

    fun setDesiredState(id: FeatureId, enabled: Boolean) {
        require(id in draftDesiredById) { "Unknown feature id=${id.value}" }
        draftDesiredById[id] = enabled
    }

    fun resolvedStates(): Map<FeatureId, ResolvedFeatureState> =
        resolver.resolve(registrySnapshot, draftDesiredById)

    fun modifiedFeatureIds(): Set<FeatureId> =
        draftDesiredById.keys.filterTo(linkedSetOf()) { id ->
            draftDesiredById[id] != initialDesiredById[id]
        }

    fun isModified(): Boolean = modifiedFeatureIds().isNotEmpty()

    /**
     * Atomically applies the draft to [settings] and returns a typed change for
     * the caller to publish only after persistence succeeds.
     */
    fun commit(
        settings: GeneralSettings,
        source: FeatureStateChangeSource = FeatureStateChangeSource.SETTINGS_APPLY
    ): FeatureStateChange? {
        val modifiedIds = modifiedFeatureIds()
        if (modifiedIds.isEmpty()) {
            return null
        }

        val batch = FeatureStateWriteBatch(modifiedIds)
        registrySnapshot.stateIdentities.forEach { identity ->
            identity.stateBridge.stageWrite(
                desired = draftDesiredById.getValue(identity.id),
                descriptor = identity,
                batch = batch
            )
        }

        val before = resolver.resolve(registrySnapshot, initialDesiredById)
        val after = resolver.resolve(registrySnapshot, draftDesiredById)
        batch.applyTo(settings)
        return FeatureStateChange.between(source, before, after)
            .takeIf { it.entries.isNotEmpty() }
    }
}

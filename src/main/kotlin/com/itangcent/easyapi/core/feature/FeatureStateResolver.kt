package com.itangcent.easyapi.core.feature

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.read

/** Resolves desired state through the dependency graph without mutating settings. */
class FeatureStateResolver {
    private enum class VisitState {
        VISITING,
        VISITED
    }

    /** Reads desired states through every bridge in [snapshot]. */
    fun readDesiredStates(
        snapshot: FeatureRegistrySnapshot,
        settings: GeneralSettings
    ): Map<FeatureId, Boolean> = linkedMapOf<FeatureId, Boolean>().apply {
        snapshot.stateIdentities.forEach { identity ->
            put(identity.id, identity.stateBridge.readDesired(settings, identity.defaultEnabled))
        }
    }

    /** Resolves states read from [settings]. */
    fun resolve(
        snapshot: FeatureRegistrySnapshot,
        settings: GeneralSettings
    ): Map<FeatureId, ResolvedFeatureState> =
        resolve(snapshot.stateIdentities, readDesiredStates(snapshot, settings))

    /** Resolves an explicit desired-state assignment for [snapshot]. */
    fun resolve(
        snapshot: FeatureRegistrySnapshot,
        desiredById: Map<FeatureId, Boolean>
    ): Map<FeatureId, ResolvedFeatureState> =
        resolve(snapshot.stateIdentities, desiredById)

    /** Resolves an ordered collection of feature identities. */
    fun resolve(
        identities: List<FeatureStateIdentity>,
        desiredById: Map<FeatureId, Boolean>
    ): Map<FeatureId, ResolvedFeatureState> {
        val identityById = linkedMapOf<FeatureId, FeatureStateIdentity>()
        identities.forEach { identity ->
            if (identity.id !in identityById) {
                identityById[identity.id] = identity
            }
        }

        val visitState = mutableMapOf<FeatureId, VisitState>()
        val cache = mutableMapOf<FeatureId, ResolvedFeatureState>()
        val stack = mutableListOf<FeatureId>()

        fun resolveIdentity(id: FeatureId): ResolvedFeatureState {
            cache[id]?.let { return it }
            val identity = identityById.getValue(id)
            val desired = desiredById[id] ?: identity.defaultEnabled

            if (!desired) {
                return ResolvedFeatureState(id, false, false, DisabledByUser).also {
                    cache[id] = it
                    visitState[id] = VisitState.VISITED
                }
            }

            if (visitState[id] == VisitState.VISITING) {
                val cycleStart = stack.indexOf(id)
                val cyclePath = (stack.subList(cycleStart, stack.size) + id).toList()
                val reason = DependencyCycle(cyclePath)
                cyclePath.dropLast(1).distinct().forEach { cycleId ->
                    val cycleIdentity = identityById.getValue(cycleId)
                    cache[cycleId] = ResolvedFeatureState(
                        id = cycleId,
                        desiredEnabled = desiredById[cycleId] ?: cycleIdentity.defaultEnabled,
                        effectiveEnabled = false,
                        reason = reason
                    )
                }
                return cache.getValue(id)
            }

            visitState[id] = VisitState.VISITING
            stack.add(id)
            var resolved: ResolvedFeatureState? = null

            for (dependencyId in identity.dependencyIds) {
                if (dependencyId !in identityById) {
                    resolved = ResolvedFeatureState(
                        id = id,
                        desiredEnabled = true,
                        effectiveEnabled = false,
                        reason = MissingDependency(dependencyId)
                    )
                    break
                }

                val dependencyState = resolveIdentity(dependencyId)
                val cycleState = cache[id]
                if (cycleState?.reason is DependencyCycle) {
                    resolved = cycleState
                    break
                }
                if (!dependencyState.effectiveEnabled) {
                    resolved = ResolvedFeatureState(
                        id = id,
                        desiredEnabled = true,
                        effectiveEnabled = false,
                        reason = DisabledByDependency(dependencyId)
                    )
                    break
                }
            }

            if (resolved == null) {
                resolved = ResolvedFeatureState(
                    id = id,
                    desiredEnabled = true,
                    effectiveEnabled = true,
                    reason = null
                )
            }
            if (id !in cache) {
                cache[id] = resolved
            }
            stack.removeAt(stack.lastIndex)
            visitState[id] = VisitState.VISITED
            return cache.getValue(id)
        }

        return linkedMapOf<FeatureId, ResolvedFeatureState>().apply {
            identityById.keys.forEach { id -> put(id, resolveIdentity(id)) }
        }
    }
}

/** Reads only persisted settings and exposes dependency-resolved runtime state. */
@Service(Service.Level.PROJECT)
class FeatureStateService(private val project: Project) {
    private val resolver = FeatureStateResolver()

    fun states(): Map<FeatureId, ResolvedFeatureState> {
        val snapshot = FeatureRegistry.getInstance(project).snapshot()
        val settings = SettingBinder.getInstance(project).read<GeneralSettings>()
        return resolver.resolve(snapshot, settings)
    }

    fun state(id: FeatureId): ResolvedFeatureState? = states()[id]

    fun isEffective(id: FeatureId): Boolean = state(id)?.effectiveEnabled == true

    fun isEffective(id: String): Boolean = isEffective(FeatureId(id))

    companion object {
        fun getInstance(project: Project): FeatureStateService = project.service()
    }
}

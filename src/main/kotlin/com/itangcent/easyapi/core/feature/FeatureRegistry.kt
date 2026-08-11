package com.itangcent.easyapi.core.feature

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.internal.PluginInfo
import com.itangcent.easyapi.core.logging.IdeaLog

/** Discovers contributors and produces deterministic, first-wins snapshots. */
@Service(Service.Level.PROJECT)
class FeatureRegistry(private val project: Project) : IdeaLog {

    /** Returns a new immutable snapshot of the currently registered contributions. */
    fun snapshot(): FeatureRegistrySnapshot {
        val entries = contributors().mapNotNull { contributor ->
            try {
                FeatureContributionEntry(contributor.sourceId, contributor.contribute(project))
            } catch (e: Exception) {
                LOG.warn("Failed to collect feature contribution from source=${contributor.sourceId}", e)
                null
            }
        }
        return buildSnapshot(
            entries = entries,
            onDuplicate = { id, firstSource, ignoredSource ->
                LOG.warn(
                    "Duplicate feature id=${id.value}; " +
                        "retaining source=$firstSource and ignoring source=$ignoredSource"
                )
            },
            onMissingDependency = { id, missingId ->
                LOG.warn(
                    "Feature id=${id.value} has missing dependency id=${missingId.value}; " +
                        "effective state will be disabled"
                )
            }
        )
    }

    private fun contributors(): List<FeatureContributor> = try {
        project.extensionArea
            .getExtensionPoint<FeatureContributor>(EP.name)
            .extensionList
    } catch (_: IllegalArgumentException) {
        emptyList()
    } catch (e: Exception) {
        LOG.warn("Failed to read feature contributors", e)
        emptyList()
    }

    companion object {
        private val EP = ExtensionPointName.create<FeatureContributor>(
            "${PluginInfo.PLUGIN_ID}.featureContributor"
        )

        fun getInstance(project: Project): FeatureRegistry = project.service()

        /**
         * Builds a snapshot from ordered contributions. The callbacks make
         * diagnostics observable while keeping the transformation pure.
         */
        fun buildSnapshot(
            entries: List<FeatureContributionEntry>,
            onDuplicate: (FeatureId, String, String) -> Unit = { _, _, _ -> },
            onMissingDependency: (FeatureId, FeatureId) -> Unit = { _, _ -> }
        ): FeatureRegistrySnapshot {
            val ownerById = linkedMapOf<FeatureId, String>()
            val retainedDescriptors = mutableListOf<FeatureDescriptor>()

            entries.forEach { entry ->
                entry.contribution.descriptors.forEach descriptorLoop@{ descriptor ->
                    val firstOwner = ownerById[descriptor.id]
                    if (firstOwner != null) {
                        onDuplicate(descriptor.id, firstOwner, entry.sourceId)
                        return@descriptorLoop
                    }
                    ownerById[descriptor.id] = entry.sourceId

                    val retainedOptions = descriptor.nestedOptions.map { option ->
                        option.copy(dependencyIds = option.dependencyIds.toList())
                    }.filter { option ->
                        val optionOwner = ownerById[option.id]
                        if (optionOwner == null) {
                            ownerById[option.id] = entry.sourceId
                            true
                        } else {
                            onDuplicate(option.id, optionOwner, entry.sourceId)
                            false
                        }
                    }
                    retainedDescriptors += descriptor.copy(
                        dependencyIds = descriptor.dependencyIds.toList(),
                        nestedOptions = retainedOptions.toList()
                    )
                }
            }

            val groups = retainedDescriptors
                .map { it.group }
                .distinctBy { it.id }
                .withIndex()
                .sortedWith(
                    compareBy<IndexedValue<FeatureGroup>> { it.value.order }
                        .thenBy { it.index }
                )
                .map { it.value }

            val snapshot = FeatureRegistrySnapshot(groups, retainedDescriptors)
            val knownIds = snapshot.stateIdentities.mapTo(linkedSetOf()) { it.id }
            snapshot.stateIdentities.forEach { identity ->
                identity.dependencyIds.distinct().forEach { dependencyId ->
                    if (dependencyId !in knownIds) {
                        onMissingDependency(identity.id, dependencyId)
                    }
                }
            }
            return snapshot
        }
    }
}

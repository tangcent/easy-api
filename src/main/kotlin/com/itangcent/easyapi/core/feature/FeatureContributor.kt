package com.itangcent.easyapi.core.feature

import com.intellij.openapi.project.Project

/** Supplies feature metadata for one project. */
interface FeatureContributor {
    val sourceId: String

    fun contribute(project: Project): FeatureContribution
}

/** A contributor result containing declared groups and top-level descriptors. */
data class FeatureContribution(
    val groups: List<FeatureGroup> = emptyList(),
    val descriptors: List<FeatureDescriptor> = emptyList()
)

/** Associates a pure contribution with its registration source. */
data class FeatureContributionEntry(
    val sourceId: String,
    val contribution: FeatureContribution
) {
    init {
        require(sourceId.isNotBlank()) { "Contributor source id must not be blank" }
    }
}

/** Immutable view of retained groups, descriptors, and nested state identities. */
class FeatureRegistrySnapshot internal constructor(
    groups: List<FeatureGroup>,
    descriptors: List<FeatureDescriptor>
) {
    val groups: List<FeatureGroup> = groups.toList()
    val descriptors: List<FeatureDescriptor> = descriptors.toList()
    val stateIdentities: List<FeatureStateIdentity> = buildList {
        this@FeatureRegistrySnapshot.descriptors.forEach { descriptor ->
            add(descriptor)
            addAll(descriptor.nestedOptions)
        }
    }

    fun descriptor(id: FeatureId): FeatureDescriptor? =
        descriptors.firstOrNull { it.id == id }

    fun identity(id: FeatureId): FeatureStateIdentity? =
        stateIdentities.firstOrNull { it.id == id }
}

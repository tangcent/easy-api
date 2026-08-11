package com.itangcent.easyapi.core.feature

/** Stable identifier used across persistence, dependencies, and runtime queries. */
@JvmInline
value class FeatureId(val value: String) {
    init {
        require(value.isNotBlank()) { "Feature id must not be blank" }
    }

    override fun toString(): String = value
}

/** A display group for top-level features. */
data class FeatureGroup(
    val id: String,
    val displayName: String,
    val order: Int
) {
    init {
        require(id.isNotBlank()) { "Feature group id must not be blank" }
        require(displayName.isNotBlank()) { "Feature group display name must not be blank" }
    }
}

/** Identifies the contributor that supplied a feature. */
data class FeatureSource(val id: String) {
    init {
        require(id.isNotBlank()) { "Feature source id must not be blank" }
    }
}

/** Common state metadata shared by top-level features and nested options. */
interface FeatureStateIdentity {
    val id: FeatureId
    val displayName: String
    val defaultEnabled: Boolean
    val dependencyIds: List<FeatureId>
    val stateBridge: FeatureStateBridge
    val source: FeatureSource

    /**
     * Short human-readable explanation shown as a tooltip on the feature's
     * checkbox in the Features settings panel. Blank means no tooltip.
     */
    val description: String get() = ""
}

/** Describes a top-level feature rendered in a feature group. */
data class FeatureDescriptor(
    override val id: FeatureId,
    override val displayName: String,
    override val defaultEnabled: Boolean,
    val group: FeatureGroup,
    override val dependencyIds: List<FeatureId> = emptyList(),
    override val stateBridge: FeatureStateBridge,
    val nestedOptions: List<FeatureOptionDescriptor> = emptyList(),
    override val source: FeatureSource,
    override val description: String = ""
) : FeatureStateIdentity {
    init {
        require(displayName.isNotBlank()) { "Feature display name must not be blank" }
    }
}

/** Describes a control nested under a top-level feature. */
data class FeatureOptionDescriptor(
    override val id: FeatureId,
    override val displayName: String,
    override val defaultEnabled: Boolean,
    override val dependencyIds: List<FeatureId> = emptyList(),
    override val stateBridge: FeatureStateBridge,
    override val source: FeatureSource,
    override val description: String = ""
) : FeatureStateIdentity {
    init {
        require(displayName.isNotBlank()) { "Feature option display name must not be blank" }
    }
}

/** Explains why a desired state is not currently effective. */
sealed interface FeatureDisabledReason

/** The feature was explicitly disabled by the user. */
data object DisabledByUser : FeatureDisabledReason

/** A declared dependency exists but is not effective. */
data class DisabledByDependency(val featureId: FeatureId) : FeatureDisabledReason

/** A declared dependency is absent from the current registry snapshot. */
data class MissingDependency(val featureId: FeatureId) : FeatureDisabledReason

/** A dependency cycle prevents the participating features from becoming effective. */
data class DependencyCycle(val path: List<FeatureId>) : FeatureDisabledReason {
    init {
        require(path.size >= 2) { "A dependency cycle path must contain a closing edge" }
        require(path.first() == path.last()) { "A dependency cycle path must end at its starting feature" }
    }
}

/** Desired and dependency-resolved state for one feature identity. */
data class ResolvedFeatureState(
    val id: FeatureId,
    val desiredEnabled: Boolean,
    val effectiveEnabled: Boolean,
    val reason: FeatureDisabledReason?
) {
    init {
        require(effectiveEnabled || reason != null) { "A disabled effective state must have a reason" }
        require(!effectiveEnabled || reason == null) { "An effective state must not have a disabled reason" }
    }
}

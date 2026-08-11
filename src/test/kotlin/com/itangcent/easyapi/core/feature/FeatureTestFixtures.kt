package com.itangcent.easyapi.core.feature

import com.itangcent.easyapi.core.settings.module.GeneralSettings

internal val TEST_FEATURE_GROUP = FeatureGroup("test-group", "Test Group", 10)
internal val TEST_FEATURE_SOURCE = FeatureSource("test")

internal fun testFeatureDescriptor(
    id: String,
    defaultEnabled: Boolean = true,
    dependencies: List<FeatureId> = emptyList(),
    bridge: FeatureStateBridge = DirectBooleanStateBridge(DirectBooleanSetting.API_SCAN_ENABLED),
    group: FeatureGroup = TEST_FEATURE_GROUP,
    source: FeatureSource = TEST_FEATURE_SOURCE,
    nestedOptions: List<FeatureOptionDescriptor> = emptyList(),
    description: String = ""
): FeatureDescriptor = FeatureDescriptor(
    id = FeatureId(id),
    displayName = "Display $id",
    defaultEnabled = defaultEnabled,
    group = group,
    dependencyIds = dependencies,
    stateBridge = bridge,
    nestedOptions = nestedOptions,
    source = source,
    description = description
)

internal fun featureSnapshotOf(vararg descriptors: FeatureDescriptor): FeatureRegistrySnapshot =
    FeatureRegistry.buildSnapshot(
        listOf(
            FeatureContributionEntry(
                sourceId = "test",
                contribution = FeatureContribution(
                    groups = descriptors.map { it.group }.distinctBy { it.id },
                    descriptors = descriptors.toList()
                )
            )
        )
    )

internal fun GeneralSettings.deepCopy(): GeneralSettings = copy(
    enabledChannels = enabledChannels.copyOf(),
    disabledChannels = disabledChannels.copyOf(),
    enabledFieldFormatChannels = enabledFieldFormatChannels.copyOf(),
    disabledFieldFormatChannels = disabledFieldFormatChannels.copyOf(),
    enabledFrameworks = enabledFrameworks.copyOf(),
    disabledFrameworks = disabledFrameworks.copyOf()
)

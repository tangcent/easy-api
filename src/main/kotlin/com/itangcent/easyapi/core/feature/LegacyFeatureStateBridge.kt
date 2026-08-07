package com.itangcent.easyapi.core.feature

import com.itangcent.easyapi.core.settings.module.GeneralSettings

/** Identifies one existing enabled/disabled override-array pair. */
enum class LegacyOverrideArrayGroup {
    CHANNELS {
        override fun readEnabled(settings: GeneralSettings): Array<String> = settings.enabledChannels
        override fun readDisabled(settings: GeneralSettings): Array<String> = settings.disabledChannels
        override fun write(settings: GeneralSettings, enabled: Array<String>, disabled: Array<String>) {
            settings.enabledChannels = enabled
            settings.disabledChannels = disabled
        }
    },
    FIELD_FORMAT_CHANNELS {
        override fun readEnabled(settings: GeneralSettings): Array<String> =
            settings.enabledFieldFormatChannels

        override fun readDisabled(settings: GeneralSettings): Array<String> =
            settings.disabledFieldFormatChannels

        override fun write(settings: GeneralSettings, enabled: Array<String>, disabled: Array<String>) {
            settings.enabledFieldFormatChannels = enabled
            settings.disabledFieldFormatChannels = disabled
        }
    },
    FRAMEWORKS {
        override fun readEnabled(settings: GeneralSettings): Array<String> = settings.enabledFrameworks
        override fun readDisabled(settings: GeneralSettings): Array<String> = settings.disabledFrameworks
        override fun write(settings: GeneralSettings, enabled: Array<String>, disabled: Array<String>) {
            settings.enabledFrameworks = enabled
            settings.disabledFrameworks = disabled
        }
    };

    internal abstract fun readEnabled(settings: GeneralSettings): Array<String>
    internal abstract fun readDisabled(settings: GeneralSettings): Array<String>
    internal abstract fun write(
        settings: GeneralSettings,
        enabled: Array<String>,
        disabled: Array<String>
    )
}

/** Bridges a namespaced feature identity to one raw legacy override id. */
class LegacyOverrideArrayStateBridge(
    val group: LegacyOverrideArrayGroup,
    val rawLegacyId: String
) : FeatureStateBridge {
    init {
        require(rawLegacyId.isNotBlank()) { "Legacy feature id must not be blank" }
    }

    override fun readDesired(settings: GeneralSettings, defaultEnabled: Boolean): Boolean =
        rawLegacyId in group.readEnabled(settings) ||
            (defaultEnabled && rawLegacyId !in group.readDisabled(settings))

    override fun stageWrite(
        desired: Boolean,
        descriptor: FeatureStateIdentity,
        batch: FeatureStateWriteBatch
    ) {
        batch.stageLegacy(
            group = group,
            featureId = descriptor.id,
            rawLegacyId = rawLegacyId,
            defaultEnabled = descriptor.defaultEnabled,
            desired = desired
        )
    }
}

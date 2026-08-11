package com.itangcent.easyapi.core.feature

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.channel.spi.ChannelRegistry
import com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer
import com.itangcent.easyapi.core.export.recognizer.CompositeApiClassRecognizer
import com.itangcent.easyapi.format.spi.FieldFormatChannel
import com.itangcent.easyapi.format.spi.FieldFormatChannelRegistry

/** Adapts existing extension enablement metadata without invoking extension behavior. */
class LegacyFeatureContributor : FeatureContributor {
    override val sourceId: String = "legacy"

    override fun contribute(project: Project): FeatureContribution = createContribution(
        channels = ChannelRegistry.getInstance(project).allChannels(),
        fieldFormatChannels = FieldFormatChannelRegistry.getInstance(project).allChannels(),
        recognizers = CompositeApiClassRecognizer.getInstance(project).allRecognizers()
    )

    internal fun createContribution(
        channels: List<Channel>,
        fieldFormatChannels: List<FieldFormatChannel>,
        recognizers: List<ApiClassRecognizer>
    ): FeatureContribution {
        val channelDescriptors = channels.map { channel ->
            FeatureDescriptor(
                id = FeatureId("channel/${channel.id}"),
                displayName = channel.displayName,
                defaultEnabled = channel.enabledByDefault,
                group = EXPORT_CHANNELS_GROUP,
                stateBridge = LegacyOverrideArrayStateBridge(
                    LegacyOverrideArrayGroup.CHANNELS,
                    channel.id
                ),
                source = CHANNEL_SOURCE,
                description = channelDescription(channel)
            )
        }
        val fieldFormatDescriptors = fieldFormatChannels.map { channel ->
            FeatureDescriptor(
                id = FeatureId("field-format/${channel.id}"),
                displayName = channel.displayName,
                defaultEnabled = channel.enabledByDefault,
                group = FIELD_FORMAT_CHANNELS_GROUP,
                stateBridge = LegacyOverrideArrayStateBridge(
                    LegacyOverrideArrayGroup.FIELD_FORMAT_CHANNELS,
                    channel.id
                ),
                source = FIELD_FORMAT_SOURCE,
                description = "${channel.displayName} field format — serialize fields and objects to ${channel.displayName}."
            )
        }
        val frameworkDescriptors = recognizers.map { recognizer ->
            FeatureDescriptor(
                id = FeatureId("framework/${recognizer.frameworkName}"),
                displayName = recognizer.frameworkName,
                defaultEnabled = recognizer.enabledByDefault,
                group = FRAMEWORKS_GROUP,
                stateBridge = LegacyOverrideArrayStateBridge(
                    LegacyOverrideArrayGroup.FRAMEWORKS,
                    recognizer.frameworkName
                ),
                source = FRAMEWORK_SOURCE,
                description = "${recognizer.frameworkName} framework support — recognize and export APIs from ${recognizer.frameworkName}-annotated classes."
            )
        }

        return FeatureContribution(
            groups = listOf(FRAMEWORKS_GROUP, EXPORT_CHANNELS_GROUP, FIELD_FORMAT_CHANNELS_GROUP),
            descriptors = frameworkDescriptors + channelDescriptors + fieldFormatDescriptors
        )
    }

    private fun channelDescription(channel: Channel): String {
        val parts = buildList {
            add("Export API documentation to ${channel.displayName}.")
            if (channel.beta) add("Beta — may have rough edges or breaking changes.")
        }
        return parts.joinToString(" ")
    }

    companion object {
        val FRAMEWORKS_GROUP = FeatureGroup("frameworks", "Framework Support", 100)
        val EXPORT_CHANNELS_GROUP = FeatureGroup("export-channels", "Export Channels", 200)
        val FIELD_FORMAT_CHANNELS_GROUP = FeatureGroup(
            "field-format-channels",
            "Field Format Channels",
            300
        )

        private val FRAMEWORK_SOURCE = FeatureSource("legacy.framework")
        private val CHANNEL_SOURCE = FeatureSource("legacy.channel")
        private val FIELD_FORMAT_SOURCE = FeatureSource("legacy.field-format")
    }
}

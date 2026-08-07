package com.itangcent.easyapi.core.feature

import com.intellij.openapi.project.Project

/** Stable ids for built-in feature capabilities. */
object CoreFeatureIds {
    val API_SCANNING = FeatureId("core.api-scanning")
    val AUTO_SCANNING = FeatureId("core.api-scanning.auto")
    val CONCURRENT_SCANNING = FeatureId("core.api-scanning.concurrent")
    val EDITOR_INTEGRATION = FeatureId("core.editor-integration")
}

/** Declares built-in scanning and editor capabilities. */
class CoreFeatureContributor : FeatureContributor {
    override val sourceId: String = SOURCE.id

    override fun contribute(project: Project): FeatureContribution = contribution()

    /** Returns the project-independent built-in contribution. */
    fun contribution(): FeatureContribution {
        val autoScanning = FeatureOptionDescriptor(
            id = CoreFeatureIds.AUTO_SCANNING,
            displayName = "Automatic API Scanning",
            defaultEnabled = true,
            dependencyIds = listOf(CoreFeatureIds.API_SCANNING),
            stateBridge = DirectBooleanStateBridge(DirectBooleanSetting.AUTO_SCAN_ENABLED),
            source = SOURCE,
            description = "Automatically scan APIs when the project opens or source files change."
        )
        val concurrentScanning = FeatureOptionDescriptor(
            id = CoreFeatureIds.CONCURRENT_SCANNING,
            displayName = "Concurrent API Scanning",
            defaultEnabled = false,
            dependencyIds = listOf(CoreFeatureIds.API_SCANNING),
            stateBridge = DirectBooleanStateBridge(DirectBooleanSetting.CONCURRENT_SCAN_ENABLED),
            source = SOURCE,
            description = "Scan APIs in parallel across modules for faster performance. Disable if you experience indexing slowdowns."
        )
        val apiScanning = FeatureDescriptor(
            id = CoreFeatureIds.API_SCANNING,
            displayName = "API Scanning",
            defaultEnabled = true,
            group = CORE_GROUP,
            stateBridge = DirectBooleanStateBridge(DirectBooleanSetting.API_SCAN_ENABLED),
            nestedOptions = listOf(autoScanning, concurrentScanning),
            source = SOURCE,
            description = "Scan source code to discover and collect API endpoints."
        )
        val editorIntegration = FeatureDescriptor(
            id = CoreFeatureIds.EDITOR_INTEGRATION,
            displayName = "Editor Integration",
            defaultEnabled = true,
            group = CORE_GROUP,
            dependencyIds = listOf(CoreFeatureIds.API_SCANNING),
            stateBridge = DirectBooleanStateBridge(DirectBooleanSetting.GUTTER_ICON_ENABLED),
            source = SOURCE,
            description = "Show gutter icons and line markers next to API methods in the editor."
        )
        return FeatureContribution(
            groups = listOf(CORE_GROUP),
            descriptors = listOf(apiScanning, editorIntegration)
        )
    }

    companion object {
        val CORE_GROUP = FeatureGroup("core-api", "API Features", 0)
        val SOURCE = FeatureSource("core")
    }
}

package com.itangcent.easyapi.framework.grpc

import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class GrpcSettingsPanelPlatformTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var panel: GrpcSettingsPanel

    /** No-op module passed to applyTo — the self-contained panel ignores it. */
    private val noopModule: Settings = object : Settings {}

    override fun setUp() {
        super.setUp()
        panel = GrpcSettingsPanel(project)
    }

    fun testResetFromAndApplyToDefaultSettings() {
        settingBinder.save(GrpcSettings())
        panel.resetFrom(null)
        panel.applyTo(noopModule)

        val result = settingBinder.read(GrpcSettings::class)
        assertFalse(result.grpcCallEnabled)
    }

    fun testResetFromCustomSettingsAndApplyTo() {
        settingBinder.save(GrpcSettings(grpcCallEnabled = true))
        panel.resetFrom(null)
        panel.applyTo(noopModule)

        val result = settingBinder.read(GrpcSettings::class)
        assertTrue(result.grpcCallEnabled)
    }

    fun testIsModifiedNullSettings() {
        settingBinder.save(GrpcSettings())
        panel.resetFrom(null)
        assertFalse(panel.isModified(null))
    }

    fun testComponentNotNull() {
        assertNotNull(panel.component)
    }

    fun testResetFromWithArtifactConfigs() {
        settingBinder.save(GrpcSettings(
            grpcCallEnabled = true,
            grpcArtifactConfigs = arrayOf(
                "io.grpc:grpc-stub:latest:true",
                "io.grpc:grpc-protobuf:1.58.0:false"
            )
        ))
        panel.resetFrom(null)
        panel.applyTo(noopModule)

        val result = settingBinder.read(GrpcSettings::class)
        assertTrue(result.grpcCallEnabled)
        assertTrue(result.grpcArtifactConfigs.isNotEmpty())
    }

    fun testResetFromWithAdditionalJars() {
        settingBinder.save(GrpcSettings(
            grpcCallEnabled = true,
            grpcAdditionalJars = arrayOf("/path/to/jar1.jar", "/path/to/jar2.jar")
        ))
        panel.resetFrom(null)
        panel.applyTo(noopModule)

        val result = settingBinder.read(GrpcSettings::class)
        assertTrue(result.grpcAdditionalJars.isNotEmpty())
    }

    fun testResetFromNullDoesNotThrow() {
        panel.resetFrom(null)
        // Should not throw
    }

    fun testResetFromNullAndApplyTo() {
        settingBinder.save(GrpcSettings())
        panel.resetFrom(null)
        panel.applyTo(noopModule)

        val result = settingBinder.read(GrpcSettings::class)
        // Should not throw; default state has grpcCallEnabled = false
        assertFalse(result.grpcCallEnabled)
    }
}

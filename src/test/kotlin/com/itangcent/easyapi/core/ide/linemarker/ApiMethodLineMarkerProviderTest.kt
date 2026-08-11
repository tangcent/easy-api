package com.itangcent.easyapi.core.ide.linemarker

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.core.cache.api.ApiScanRequestDecision
import com.itangcent.easyapi.core.feature.CoreFeatureIds
import com.itangcent.easyapi.core.feature.FeatureStateService
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class ApiMethodLineMarkerProviderTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var lineMarkerProvider: ApiMethodLineMarkerProvider

    override fun setUp() {
        super.setUp()
        loadFile("spring/RestController.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/RequestMapping.java")
        loadFile("api/UserCtrl.java")
        lineMarkerProvider = ApiMethodLineMarkerProvider()
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    override fun tearDown() {
        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = true
            gutterIconEnabled = true
        }
        super.tearDown()
    }

    fun testDesiredEditorDisabledProducesNoMarker() = runTest {
        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = true
            gutterIconEnabled = false
        }
        val method = apiMethod()

        assertFalse(
            "Editor integration should be effectively disabled by its own desired state",
            FeatureStateService.getInstance(project).isEffective(CoreFeatureIds.EDITOR_INTEGRATION)
        )
        assertNull(
            "Effectively disabled editor integration should not produce a marker",
            lineMarkerProvider.getLineMarkerInfo(method.nameIdentifier!!)
        )
    }

    fun testParentScanningDisabledProducesNoMarker() = runTest {
        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = false
            gutterIconEnabled = true
        }
        val method = apiMethod()

        assertFalse(
            "Editor integration should be ineffective when scanning is disabled",
            FeatureStateService.getInstance(project).isEffective(CoreFeatureIds.EDITOR_INTEGRATION)
        )
        assertNull(
            "Parent-disabled editor integration should not produce a marker",
            lineMarkerProvider.getLineMarkerInfo(method.nameIdentifier!!)
        )
    }

    fun testMarkerReturnsAfterEffectiveStateIsRestored() = runTest {
        val method = apiMethod()
        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = false
            gutterIconEnabled = true
        }
        assertNull(
            "Marker should be absent while the dependency is disabled",
            lineMarkerProvider.getLineMarkerInfo(method.nameIdentifier!!)
        )

        settingBinder.update(GeneralSettings::class) {
            apiScanEnabled = true
        }

        assertTrue(
            "Editor integration should become effective when scanning is restored",
            FeatureStateService.getInstance(project).isEffective(CoreFeatureIds.EDITOR_INTEGRATION)
        )
        assertNotNull(
            "The same provider should restore the API marker",
            lineMarkerProvider.getLineMarkerInfo(method.nameIdentifier!!)
        )
    }

    fun testFallbackDelegatesToLifecycleAdmissionSeam() = runTest {
        val admittedPaths = mutableListOf<List<String>>()
        val provider = ApiMethodLineMarkerProvider(
            editorIntegrationEffective = { true },
            requestGutterIncremental = { _: Project, paths: List<String> ->
                admittedPaths += paths
                ApiScanRequestDecision.REJECTED_EDITOR_DISABLED
            }
        )

        provider.navigateToMethod(apiMethod())

        assertEquals("Cache miss should make exactly one controlled request", 1, admittedPaths.size)
        assertEquals("Fallback should submit one containing file", 1, admittedPaths.single().size)
        assertTrue(
            "Fallback should submit the API method's source file",
            admittedPaths.single().single().endsWith("UserCtrl.java")
        )
    }

    private fun apiMethod(): PsiMethod {
        val controller = findClass("com.itangcent.api.UserCtrl")
        assertNotNull("Spring controller fixture should resolve", controller)
        val method = findMethod(controller!!, "greeting")
        assertNotNull("Spring API method fixture should resolve", method)
        return method!!
    }
}

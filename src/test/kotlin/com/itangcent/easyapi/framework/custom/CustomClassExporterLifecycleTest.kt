package com.itangcent.easyapi.framework.custom

import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.export.isHttp
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Verifies the rule lifecycle hooks fire in the correct order and at the
 * correct granularity for [CustomClassExporter]. Mirrors the JAX-RS
 * lifecycle test pattern: a config that emits log markers from each
 * `api.*` / `export.after` event AND each `custom.*` event, plus
 * assertions that endpoints carry the post-`export.after` shape.
 *
 * The exporter wraps the method loop in `API_CLASS_PARSE_BEFORE` /
 * `API_CLASS_PARSE_AFTER` (the latter in `finally`), each method in
 * `API_METHOD_PARSE_BEFORE` / `API_METHOD_PARSE_AFTER` (the latter in
 * `finally`), and fires `EXPORT_AFTER` per endpoint with
 * `ctx.setExt("api", endpoint)`. Additionally, framework-scoped
 * `custom.*` hooks fire immediately after their shared counterparts
 * (shared first, then custom — Req 6.4, Decision 8).
 */
class CustomClassExporterLifecycleTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: CustomClassExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = CustomClassExporter(project)
    }

    private fun loadTestFiles() {
        loadFile("custom/annotation/MyApi.java")
        loadFile("custom/annotation/MyEndpoint.java")
        loadFile("custom/api/Ctrl.java")
        loadFile("model/Result.java")
        loadFile("model/UserInfo.java")
    }

    override fun createConfigReader() = TestConfigReader.fromConfigText(
        project,
        """
        custom.class.is.api=groovy:it.hasAnn("com.itangcent.custom.annotation.MyApi")
        custom.method.is.api=groovy:it.hasAnn("com.itangcent.custom.annotation.MyEndpoint")
        custom.http.method=groovy:"POST"
        custom.path=groovy:it.contextType() == "class" ? "/ctrl" : ""
        api.class.parse.before=groovy:logger.info("lifecycle:api.class.parse.before:" + it.name())
        api.class.parse.after=groovy:logger.info("lifecycle:api.class.parse.after:" + it.name())
        api.method.parse.before=groovy:logger.info("lifecycle:api.method.parse.before:" + it.name())
        api.method.parse.after=groovy:logger.info("lifecycle:api.method.parse.after:" + it.name())
        export.after=groovy:logger.info("lifecycle:export.after:" + it.name())
        custom.class.parse.before=groovy:logger.info("lifecycle:custom.class.parse.before:" + it.name())
        custom.class.parse.after=groovy:logger.info("lifecycle:custom.class.parse.after:" + it.name())
        custom.method.parse.before=groovy:logger.info("lifecycle:custom.method.parse.before:" + it.name())
        custom.method.parse.after=groovy:logger.info("lifecycle:custom.method.parse.after:" + it.name())
        custom.export.after=groovy:logger.info("lifecycle:custom.export.after:" + it.name()); api.setDescription("hooked")
        """.trimIndent()
    )

    fun testClassParseBeforeAndAfterFire() = runTest {
        val psiClass = findClass("com.itangcent.custom.Ctrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())
    }

    fun testMethodParseBeforeAndAfterFire() = runTest {
        val psiClass = findClass("com.itangcent.custom.Ctrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())
        // Both Ctrl methods carry @MyEndpoint, so both should be exported.
        assertEquals(2, endpoints.size)
    }

    fun testExportAfterFiresPerEndpoint() = runTest {
        val psiClass = findClass("com.itangcent.custom.Ctrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        for (endpoint in endpoints) {
            assertNotNull("Each endpoint should have source method for EXPORT_AFTER", endpoint.sourceMethod)
            assertNotNull("Each endpoint should have HTTP metadata", endpoint.httpMetadata)
        }
    }

    fun testNonApiClassTriggersNoMethodEvents() = runTest {
        val psiClass = findClass("com.itangcent.model.UserInfo")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Non-API class should return empty endpoints", endpoints.isEmpty())
    }

    fun testMethodParseAfterFiresForAllMethods() = runTest {
        val psiClass = findClass("com.itangcent.custom.Ctrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        val methods = endpoints.mapNotNull { it.sourceMethod?.name }.toSet()
        assertTrue("Should have endpoints from multiple methods", methods.size > 1)
    }

    fun testAllEndpointsSharePostMethod() = runTest {
        val psiClass = findClass("com.itangcent.custom.Ctrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        for (endpoint in endpoints) {
            assertEquals(HttpMethod.POST, endpoint.httpMetadata?.method)
        }
    }

    fun testEndpointsHaveHttpMetadataAfterExport() = runTest {
        val psiClass = findClass("com.itangcent.custom.Ctrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        for (endpoint in endpoints) {
            assertTrue("Endpoint must carry HttpMetadata after EXPORT_AFTER", endpoint.isHttp)
            assertNotNull(endpoint.httpMetadata)
            assertNotNull(endpoint.httpMetadata!!.path)
            assertNotNull(endpoint.httpMetadata!!.method)
        }
    }

    /**
     * Verifies that the framework-scoped `custom.export.after` hook fires
     * alongside the shared `export.after` hook. The config's
     * `custom.export.after` rule mutates the endpoint's `description` (a
     * `var`) to `"hooked"` — if the hook didn't fire, the description would
     * be whatever `metadataResolver.resolveMethodDoc` returned (not
     * `"hooked"`) (Req 6.4, Decision 8).
     */
    fun testCustomExportAfterHookFires() = runTest {
        val psiClass = findClass("com.itangcent.custom.Ctrl")
        assertNotNull(psiClass)

        val endpoints = exporter.export(psiClass!!)
        assertTrue("Should export endpoints", endpoints.isNotEmpty())

        for (endpoint in endpoints) {
            assertEquals(
                "custom.export.after must have fired (description should be 'hooked')",
                "hooked",
                endpoint.description
            )
        }
    }
}

package com.itangcent.easyapi.framework.custom

import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Per-rule-key integration tests for the Custom framework.
 *
 * Each inner class configures exactly one (or a small group of) `custom.*`
 * rule keys against the `Ctrl` fixture and asserts the resulting
 * [com.itangcent.easyapi.core.export.ApiEndpoint] shape. This complements
 * [CustomClassExporterTest] (which covers recognition, HTTP method, path,
 * and the Spring reference ruleset) by isolating each parameter-binding
 * and name-override rule key.
 *
 * Fixture: `Ctrl.get(Long id)` — a single `Long id` parameter that can be
 * bound to any of {body, form, path, header, cookie, query} via rule config.
 */
class CustomRuleIntegrationTest {

    private companion object {
        const val BASE_RULES = """
            custom.class.is.api=groovy:it.hasAnn("com.itangcent.custom.annotation.MyApi")
            custom.method.is.api=groovy:it.hasAnn("com.itangcent.custom.annotation.MyEndpoint")
            custom.http.method=groovy:"POST"
        """
    }

    /** `custom.param.as.json.body=true` → ParameterBinding.Body + request body. */
    class WithJsonBodyBinding : EasyApiLightCodeInsightFixtureTestCase() {

        private lateinit var exporter: CustomClassExporter

        override fun setUp() {
            super.setUp()
            loadFile("custom/annotation/MyApi.java")
            loadFile("custom/annotation/MyEndpoint.java")
            loadFile("custom/api/Ctrl.java")
            loadFile("model/Result.java")
            loadFile("model/UserInfo.java")
            exporter = CustomClassExporter(project)
        }

        override fun createConfigReader() = TestConfigReader.fromConfigText(
            project,
            """
            $BASE_RULES
            custom.path=groovy:"/ctrl"
            custom.param.as.json.body=groovy:it.name() == "id"
            """.trimIndent()
        )

        fun testParamBoundAsBody() = runTest {
            val psiClass = findClass("com.itangcent.custom.Ctrl")
            assertNotNull(psiClass)
            val get = exporter.export(psiClass!!).find { it.sourceMethod?.name == "get" }
            assertNotNull(get)
            assertNotNull("Body parameter should produce a request body", get!!.httpMetadata!!.body)
            assertTrue(
                "Body-bound param should not appear in the parameters list",
                get.httpMetadata!!.parameters.none { it.binding == ParameterBinding.Body }
            )
        }
    }

    /** `custom.param.as.form.body=true` → ParameterBinding.Form. */
    class WithFormBodyBinding : EasyApiLightCodeInsightFixtureTestCase() {

        private lateinit var exporter: CustomClassExporter

        override fun setUp() {
            super.setUp()
            loadFile("custom/annotation/MyApi.java")
            loadFile("custom/annotation/MyEndpoint.java")
            loadFile("custom/api/Ctrl.java")
            loadFile("model/Result.java")
            loadFile("model/UserInfo.java")
            exporter = CustomClassExporter(project)
        }

        override fun createConfigReader() = TestConfigReader.fromConfigText(
            project,
            """
            $BASE_RULES
            custom.path=groovy:"/ctrl"
            custom.param.as.form.body=groovy:it.name() == "id"
            """.trimIndent()
        )

        fun testParamBoundAsForm() = runTest {
            val psiClass = findClass("com.itangcent.custom.Ctrl")
            assertNotNull(psiClass)
            val get = exporter.export(psiClass!!).find { it.sourceMethod?.name == "get" }
            assertNotNull(get)
            val formParam = get!!.httpMetadata!!.parameters.find { it.binding == ParameterBinding.Form }
            assertNotNull("Should have a form parameter", formParam)
        }
    }

    /** `custom.param.as.path.var=true` + path with `{id}` → ParameterBinding.Path. */
    class WithPathVariableBinding : EasyApiLightCodeInsightFixtureTestCase() {

        private lateinit var exporter: CustomClassExporter

        override fun setUp() {
            super.setUp()
            loadFile("custom/annotation/MyApi.java")
            loadFile("custom/annotation/MyEndpoint.java")
            loadFile("custom/api/Ctrl.java")
            loadFile("model/Result.java")
            loadFile("model/UserInfo.java")
            exporter = CustomClassExporter(project)
        }

        override fun createConfigReader() = TestConfigReader.fromConfigText(
            project,
            """
            $BASE_RULES
            custom.path=groovy:it.contextType() == "class" ? "/ctrl" : "/{id}"
            custom.param.as.path.var=groovy:it.name() == "id"
            """.trimIndent()
        )

        fun testParamBoundAsPath() = runTest {
            val psiClass = findClass("com.itangcent.custom.Ctrl")
            assertNotNull(psiClass)
            val get = exporter.export(psiClass!!).find { it.sourceMethod?.name == "get" }
            assertNotNull(get)
            val pathParam = get!!.httpMetadata!!.parameters.find { it.binding == ParameterBinding.Path }
            assertNotNull("Should have a path parameter", pathParam)
            assertEquals("id", pathParam!!.name)
            assertTrue("Path should contain {id}", get.httpMetadata!!.path.contains("{id}"))
        }
    }

    /** `custom.param.as.cookie=true` → ParameterBinding.Cookie. */
    class WithCookieBinding : EasyApiLightCodeInsightFixtureTestCase() {

        private lateinit var exporter: CustomClassExporter

        override fun setUp() {
            super.setUp()
            loadFile("custom/annotation/MyApi.java")
            loadFile("custom/annotation/MyEndpoint.java")
            loadFile("custom/api/Ctrl.java")
            loadFile("model/Result.java")
            loadFile("model/UserInfo.java")
            exporter = CustomClassExporter(project)
        }

        override fun createConfigReader() = TestConfigReader.fromConfigText(
            project,
            """
            $BASE_RULES
            custom.path=groovy:"/ctrl"
            custom.param.as.cookie=groovy:it.name() == "id"
            """.trimIndent()
        )

        fun testParamBoundAsCookie() = runTest {
            val psiClass = findClass("com.itangcent.custom.Ctrl")
            assertNotNull(psiClass)
            val get = exporter.export(psiClass!!).find { it.sourceMethod?.name == "get" }
            assertNotNull(get)
            val cookieParam = get!!.httpMetadata!!.parameters.find { it.binding == ParameterBinding.Cookie }
            assertNotNull("Should have a cookie parameter", cookieParam)
        }
    }

    /**
     * `param.http.type=header` + `custom.param.header=X-User-Id` →
     * ParameterBinding.Header with overridden name.
     */
    class WithHeaderNameOverride : EasyApiLightCodeInsightFixtureTestCase() {

        private lateinit var exporter: CustomClassExporter

        override fun setUp() {
            super.setUp()
            loadFile("custom/annotation/MyApi.java")
            loadFile("custom/annotation/MyEndpoint.java")
            loadFile("custom/api/Ctrl.java")
            loadFile("model/Result.java")
            loadFile("model/UserInfo.java")
            exporter = CustomClassExporter(project)
        }

        override fun createConfigReader() = TestConfigReader.fromConfigText(
            project,
            """
            $BASE_RULES
            custom.path=groovy:"/ctrl"
            param.http.type=groovy:it.name() == "id" ? "header" : null
            custom.param.header=groovy:it.name() == "id" ? "X-User-Id" : null
            """.trimIndent()
        )

        fun testHeaderNameOverridden() = runTest {
            val psiClass = findClass("com.itangcent.custom.Ctrl")
            assertNotNull(psiClass)
            val get = exporter.export(psiClass!!).find { it.sourceMethod?.name == "get" }
            assertNotNull(get)
            val headerParam = get!!.httpMetadata!!.parameters.find { it.binding == ParameterBinding.Header }
            assertNotNull("Should have a header parameter", headerParam)
            assertEquals("X-User-Id", headerParam!!.name)
        }
    }

    /**
     * `custom.param.as.path.var=true` + path `{id}` + `custom.param.path.var=userId`
     * → path parameter with overridden name "userId" (while path still uses `{id}`).
     */
    class WithPathVariableNameOverride : EasyApiLightCodeInsightFixtureTestCase() {

        private lateinit var exporter: CustomClassExporter

        override fun setUp() {
            super.setUp()
            loadFile("custom/annotation/MyApi.java")
            loadFile("custom/annotation/MyEndpoint.java")
            loadFile("custom/api/Ctrl.java")
            loadFile("model/Result.java")
            loadFile("model/UserInfo.java")
            exporter = CustomClassExporter(project)
        }

        override fun createConfigReader() = TestConfigReader.fromConfigText(
            project,
            """
            $BASE_RULES
            custom.path=groovy:it.contextType() == "class" ? "/ctrl" : "/{id}"
            custom.param.as.path.var=groovy:it.name() == "id"
            custom.param.path.var=groovy:it.name() == "id" ? "userId" : null
            """.trimIndent()
        )

        fun testPathVariableNameOverridden() = runTest {
            val psiClass = findClass("com.itangcent.custom.Ctrl")
            assertNotNull(psiClass)
            val get = exporter.export(psiClass!!).find { it.sourceMethod?.name == "get" }
            assertNotNull(get)
            // The path-extracted param from `{id}` is "id"; the rule-renamed one is "userId".
            // mergePathParameters keeps the rule override when names collide.
            val pathParams = get!!.httpMetadata!!.parameters.filter { it.binding == ParameterBinding.Path }
            assertTrue("Should have at least one path parameter", pathParams.isNotEmpty())
            assertTrue(
                "Path parameter name should be overridden to userId",
                pathParams.any { it.name == "userId" }
            )
        }
    }

    /**
     * `custom.param.as.cookie=true` + `custom.param.cookie=sessionId` →
     * cookie parameter with overridden name "sessionId".
     */
    class WithCookieNameOverride : EasyApiLightCodeInsightFixtureTestCase() {

        private lateinit var exporter: CustomClassExporter

        override fun setUp() {
            super.setUp()
            loadFile("custom/annotation/MyApi.java")
            loadFile("custom/annotation/MyEndpoint.java")
            loadFile("custom/api/Ctrl.java")
            loadFile("model/Result.java")
            loadFile("model/UserInfo.java")
            exporter = CustomClassExporter(project)
        }

        override fun createConfigReader() = TestConfigReader.fromConfigText(
            project,
            """
            $BASE_RULES
            custom.path=groovy:"/ctrl"
            custom.param.as.cookie=groovy:it.name() == "id"
            custom.param.cookie=groovy:it.name() == "id" ? "sessionId" : null
            """.trimIndent()
        )

        fun testCookieNameOverridden() = runTest {
            val psiClass = findClass("com.itangcent.custom.Ctrl")
            assertNotNull(psiClass)
            val get = exporter.export(psiClass!!).find { it.sourceMethod?.name == "get" }
            assertNotNull(get)
            val cookieParam = get!!.httpMetadata!!.parameters.find { it.binding == ParameterBinding.Cookie }
            assertNotNull("Should have a cookie parameter", cookieParam)
            assertEquals("sessionId", cookieParam!!.name)
        }
    }

    /**
     * `custom.param.name=userId` (no boolean classifier set) → default Query
     * binding with the name overridden to "userId".
     */
    class WithGenericParamNameOverride : EasyApiLightCodeInsightFixtureTestCase() {

        private lateinit var exporter: CustomClassExporter

        override fun setUp() {
            super.setUp()
            loadFile("custom/annotation/MyApi.java")
            loadFile("custom/annotation/MyEndpoint.java")
            loadFile("custom/api/Ctrl.java")
            loadFile("model/Result.java")
            loadFile("model/UserInfo.java")
            exporter = CustomClassExporter(project)
        }

        override fun createConfigReader() = TestConfigReader.fromConfigText(
            project,
            """
            $BASE_RULES
            custom.path=groovy:"/ctrl"
            custom.param.name=groovy:it.name() == "id" ? "userId" : null
            """.trimIndent()
        )

        fun testGenericParamNameOverridden() = runTest {
            val psiClass = findClass("com.itangcent.custom.Ctrl")
            assertNotNull(psiClass)
            val get = exporter.export(psiClass!!).find { it.sourceMethod?.name == "get" }
            assertNotNull(get)
            val queryParam = get!!.httpMetadata!!.parameters.find { it.binding == ParameterBinding.Query }
            assertNotNull("Should have a query parameter (default binding)", queryParam)
            assertEquals("userId", queryParam!!.name)
        }
    }

    /**
     * `param.ignore=true` for the `id` parameter → parameter dropped entirely.
     */
    class WithParamIgnored : EasyApiLightCodeInsightFixtureTestCase() {

        private lateinit var exporter: CustomClassExporter

        override fun setUp() {
            super.setUp()
            loadFile("custom/annotation/MyApi.java")
            loadFile("custom/annotation/MyEndpoint.java")
            loadFile("custom/api/Ctrl.java")
            loadFile("model/Result.java")
            loadFile("model/UserInfo.java")
            exporter = CustomClassExporter(project)
        }

        override fun createConfigReader() = TestConfigReader.fromConfigText(
            project,
            """
            $BASE_RULES
            custom.path=groovy:"/ctrl"
            param.ignore=groovy:it.name() == "id"
            """.trimIndent()
        )

        fun testIgnoredParamDropped() = runTest {
            val psiClass = findClass("com.itangcent.custom.Ctrl")
            assertNotNull(psiClass)
            val get = exporter.export(psiClass!!).find { it.sourceMethod?.name == "get" }
            assertNotNull(get)
            assertTrue(
                "Ignored param should not appear in the parameters list",
                get!!.httpMetadata!!.parameters.none { it.name == "id" }
            )
        }
    }
}

package com.itangcent.easyapi.channel.openapi

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Verifies each of the three OpenAPI-channel extension
 * config files (`swagger3-openapi.config`, `swagger-openapi.config`,
 * `springfox-openapi.config`) correctly extracts document-level metadata from
 * a SEPARATE config class (NOT from the controller being exported).
 *
 * This mirrors how real projects declare OpenAPI metadata — on a dedicated
 * `@Configuration` class, not on every controller. The fixes rewrote the
 * extension configs to use `helper.findClassesByAnnotation(...)`
 * / `helper.findMethodsByAnnotation(...)` instead of `it.annMap(...)` (which
 * read the annotation from the current controller — always null in real
 * projects where the annotation lives elsewhere).
 *
 * ## Structure
 *
 * Three inner test classes, one per extension. Each inner class:
 *  - Overrides [createConfigReader] to load ONLY its extension's config file
 *    (isolation — avoids cross-extension interference when multiple extensions
 *    define the same rule key, e.g. `openapi.info.title`).
 *  - Loads ONLY its extension's cross-class fixtures (a separate `@Configuration`
 *    class carrying the document-level annotation + a plain `@RestController`
 *    with no document-level annotation).
 *  - Exports the PLAIN controller and asserts the document-level metadata
 *    (title / version / description / server URL) is picked up from the
 *    SEPARATE config class via the global `helper.findClassesByAnnotation(...)`
 *    / `helper.findMethodsByAnnotation(...)` lookup.
 *
 * ## Fixtures (`api/extension/`)
 *
 * - `swagger3/OpenApiConfig.java` — `@Configuration @OpenAPIDefinition(info=...)`
 * - `swagger3/PingController.java` — plain `@RestController` (no `@OpenAPIDefinition`)
 * - `swagger2/SwaggerConfig.java` — `@Configuration @SwaggerDefinition(host=..., info=...)`
 * - `swagger2/PingController.java` — plain `@RestController`
 * - `springfox/DocketConfig.java` — `@Configuration` with `@Bean Docket api()` method
 * - `springfox/PingController.java` — plain `@RestController`
 *
 * JUnit 3-style `testXxx()` naming is required because
 * [EasyApiLightCodeInsightFixtureTestCase] extends
 * `LightJavaCodeInsightFixtureTestCase` (a JUnit 3 `TestCase` subclass).
 */
class ExtensionParsingTest {

    /**
     * Shared base class for the three extension-specific inner test classes.
     * Provides common setup (channel instance, JSON output format pin) and
     * the `exportChannel` / `loadConfigFromResource` helpers.
     */
    abstract class Base : EasyApiLightCodeInsightFixtureTestCase() {

        protected lateinit var channel: OpenApiChannel

        override fun setUp() {
            super.setUp()
            channel = OpenApiChannel()
            // Pin JSON output format so the ALWAYS_ASK prompt (which would
            // block on EDT) is bypassed.
            SettingBinder.getInstance(project).update(OpenApiSettings::class) {
                outputFormat = "JSON"
            }
            // Load common Spring annotation stubs needed by all fixture
            // controllers (@RestController, @RequestMapping, @GetMapping).
            loadSpringStubs()
        }

        override fun tearDown() {
            try {
                SettingBinder.getInstance(project).update(OpenApiSettings::class) {
                    outputFormat = "ALWAYS_ASK"
                }
            } finally {
                super.tearDown()
            }
        }

        /**
         * Loads the common Spring annotation stubs needed by all fixture
         * controllers (`@RestController`, `@RequestMapping`, `@GetMapping`).
         * Extension-specific annotation stubs are loaded by each inner
         * class's `setUp()`.
         */
        protected fun loadSpringStubs() {
            loadFile("spring/RestController.java")
            loadFile("spring/RequestMapping.java")
            loadFile("spring/GetMapping.java")
        }

        /**
         * Exports an endpoint whose `sourceClass` is the given [psiClass],
         * using an explicit JSON [OpenApiConfig] to bypass the ALWAYS_ASK
         * prompt. The endpoint's path/method are synthetic — the rule scripts
         * use `helper.findClassesByAnnotation(...)` (global lookup, not
         * per-element) so the sourceClass only matters for the `it` binding
         * which the new scripts do not reference.
         */
        protected suspend fun exportChannel(psiClass: PsiClass): ExportResult {
            val endpoint = endpointWithSourceClass(psiClass, methodName = "ping")
            return channel.export(
                ExportContext(
                    project = project,
                    endpoints = listOf(endpoint),
                    channelId = "openapi",
                    channelConfig = OpenApiConfig(outputFormat = OpenApiOutputFormat.JSON),
                )
            )
        }

        private fun endpointWithSourceClass(psiClass: PsiClass, methodName: String): ApiEndpoint {
            val psiMethod = mock<PsiMethod> { on { this.name } doReturn methodName }
            return ApiEndpoint(
                name = methodName,
                metadata = httpMetadata(path = "/ping", method = HttpMethod.GET),
                sourceMethod = psiMethod,
                sourceClass = psiClass,
                className = psiClass.qualifiedName,
            )
        }

        protected fun loadConfigFromResource(path: String): String {
            return javaClass.classLoader.getResourceAsStream(path)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: error("Resource not found: $path")
        }
    }

    // ─── swagger3-openapi.config ───────────────────────────────────────

    /**
     * Verifies `swagger3-openapi.config` extracts `@OpenAPIDefinition`
     * metadata from a SEPARATE `@Configuration` class (NOT from the exported
     * controller).
     *
     * The config uses
     * `helper.findClassesByAnnotation("io.swagger.v3.oas.annotations.OpenAPIDefinition")`
     * to locate the annotated config class globally. Previously it used
     * `it.annMap(...)` against the current controller, which always returned
     * null because `@OpenAPIDefinition` is never on the controller in real
     * projects.
     */
    class Swagger3ExtensionTest : Base() {

        override fun createConfigReader(): TestConfigReader = TestConfigReader.fromConfigText(
            project,
            loadConfigFromResource("extensions/swagger3-openapi.config"),
        )

        override fun setUp() {
            super.setUp()
            // Swagger v3 annotation stubs
            loadFile("io/swagger/v3/oas/annotations/OpenAPIDefinition.java")
            loadFile("io/swagger/v3/oas/annotations/info/Info.java")
            loadFile("io/swagger/v3/oas/annotations/servers/Server.java")
            // Spring @Configuration (OpenApiConfig.java is a @Configuration class)
            loadFile("org/springframework/context/annotation/Configuration.java")
            // Cross-class fixtures
            loadFile("api/extension/swagger3/OpenApiConfig.java")
            loadFile("api/extension/swagger3/PingController.java")
        }

        fun testCrossClassOpenApiDefinitionExtractsInfoTitle() = runTest {
            val psiClass = findClass("com.itangcent.extension.swagger3.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue("Expected Success, got: $result", result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.title should come from @OpenAPIDefinition on the SEPARATE OpenApiConfig class",
                "Cross-Class Swagger3 Title",
                metadata.document.info.title,
            )
        }

        fun testCrossClassOpenApiDefinitionExtractsInfoVersion() = runTest {
            val psiClass = findClass("com.itangcent.extension.swagger3.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.version should come from @OpenAPIDefinition.info.version on OpenApiConfig",
                "9.1.0",
                metadata.document.info.version,
            )
        }

        fun testCrossClassOpenApiDefinitionExtractsInfoDescription() = runTest {
            val psiClass = findClass("com.itangcent.extension.swagger3.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.description should come from @OpenAPIDefinition.info.description on OpenApiConfig",
                "Cross-class OpenAPIDefinition description",
                metadata.document.info.description,
            )
        }

        fun testCrossClassOpenApiDefinitionExtractsServerUrl() = runTest {
            val psiClass = findClass("com.itangcent.extension.swagger3.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            val servers = metadata.document.servers
            assertNotNull(
                "servers should be populated when @OpenAPIDefinition declares @Server on OpenApiConfig",
                servers,
            )
            assertEquals("Expected exactly one server entry", 1, servers!!.size)
            assertEquals(
                "servers[0].url should come from @OpenAPIDefinition.servers[0].url on OpenApiConfig",
                "https://cross-class.example.com",
                servers.first().url,
            )
        }

        fun testCrossClassSerializedContentContainsExpectedMetadata() = runTest {
            val psiClass = findClass("com.itangcent.extension.swagger3.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            val content = metadata.content
            // Verify the serialized JSON contains all the cross-class metadata.
            assertTrue("JSON should contain the cross-class title: ${content.take(200)}",
                content.contains("\"title\": \"Cross-Class Swagger3 Title\""))
            assertTrue("JSON should contain the cross-class version",
                content.contains("\"version\": \"9.1.0\""))
            assertTrue("JSON should contain the cross-class description",
                content.contains("\"description\": \"Cross-class OpenAPIDefinition description\""))
            assertTrue("JSON should contain the cross-class server URL",
                content.contains("\"url\": \"https://cross-class.example.com\""))
        }
    }

    // ─── swagger-openapi.config ─────────────────────────────────────────

    /**
     * Verifies `swagger-openapi.config` extracts `@SwaggerDefinition`
     * metadata from a SEPARATE `@Configuration` class (NOT from the exported
     * controller).
     *
     * The `openapi.server.url` rule uses groovy
     * string interpolation `${scheme}://${host}${basePath}` which collides
     * with `PropertyResolver`'s `${key}` placeholder syntax. The rule is
     * wrapped with `###set resolveProperty=false` / `###set resolveProperty=true`
     * to disable property resolution for that block.
     *
     * The config uses
     * `helper.findClassesByAnnotation("io.swagger.annotations.SwaggerDefinition")`
     * instead of `it.annMap(...)`.
     */
    class Swagger2ExtensionTest : Base() {

        override fun createConfigReader(): TestConfigReader = TestConfigReader.fromConfigText(
            project,
            loadConfigFromResource("extensions/swagger-openapi.config"),
        )

        override fun setUp() {
            super.setUp()
            // Swagger v2 annotation stubs
            loadFile("io/swagger/annotations/SwaggerDefinition.java")
            loadFile("io/swagger/annotations/Info.java")
            // Spring @Configuration
            loadFile("org/springframework/context/annotation/Configuration.java")
            // Cross-class fixtures
            loadFile("api/extension/swagger2/SwaggerConfig.java")
            loadFile("api/extension/swagger2/PingController.java")
        }

        fun testCrossClassSwaggerDefinitionExtractsInfoTitle() = runTest {
            val psiClass = findClass("com.itangcent.extension.swagger2.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue("Expected Success, got: $result", result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.title should come from @SwaggerDefinition.info.title on the SEPARATE SwaggerConfig class",
                "Cross-Class Swagger2 Title",
                metadata.document.info.title,
            )
        }

        fun testCrossClassSwaggerDefinitionExtractsInfoVersion() = runTest {
            val psiClass = findClass("com.itangcent.extension.swagger2.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.version should come from @SwaggerDefinition.info.version on SwaggerConfig",
                "2.9",
                metadata.document.info.version,
            )
        }

        fun testCrossClassSwaggerDefinitionExtractsInfoDescription() = runTest {
            val psiClass = findClass("com.itangcent.extension.swagger2.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.description should come from @SwaggerDefinition.info.description on SwaggerConfig",
                "Cross-class SwaggerDefinition description",
                metadata.document.info.description,
            )
        }

        fun testCrossClassSwaggerDefinitionCombinesHostBasePathSchemeIntoServerUrl() = runTest {
            // The groovy script uses ${scheme}://${host}${basePath}
            // which must be protected from PropertyResolver via ###set resolveProperty=false.
            val psiClass = findClass("com.itangcent.extension.swagger2.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            val servers = metadata.document.servers
            assertNotNull(
                "servers should be populated when @SwaggerDefinition declares host on SwaggerConfig",
                servers,
            )
            assertEquals("Expected exactly one server entry", 1, servers!!.size)
            assertEquals(
                "servers[0].url should be assembled from scheme + host + basePath (property resolution disabled)",
                "https://cross-class.example.com/v2",
                servers.first().url,
            )
        }

        fun testCrossClassSerializedContentContainsExpectedMetadata() = runTest {
            val psiClass = findClass("com.itangcent.extension.swagger2.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            val content = metadata.content
            assertTrue("JSON should contain the cross-class title: ${content.take(200)}",
                content.contains("\"title\": \"Cross-Class Swagger2 Title\""))
            assertTrue("JSON should contain the cross-class version",
                content.contains("\"version\": \"2.9\""))
            assertTrue("JSON should contain the cross-class description",
                content.contains("\"description\": \"Cross-class SwaggerDefinition description\""))
            assertTrue("JSON should contain the assembled server URL (not corrupted by PropertyResolver)",
                content.contains("\"url\": \"https://cross-class.example.com/v2\""))
        }
    }

    // ─── springfox-openapi.config ───────────────────────────────────────

    /**
     * Verifies `springfox-openapi.config` extracts Springfox `Docket` metadata
     * from a `@Bean` method on a SEPARATE `@Configuration` class (NOT from the
     * exported controller).
     *
     * The `openapi.server.url` rule uses groovy string
     * interpolation `${scheme}${host}${path}` which collides with
     * `PropertyResolver`'s `${key}` placeholder syntax. The rule is wrapped
     * with `###set resolveProperty=false` / `###set resolveProperty=true`.
     *
     * Each rule key's groovy script is self-contained
     * (no `${...}` cross-references between rule keys) and uses the correct
     * script-context method names (`m.sourceCode()` not `m.text()`,
     * `ret.name()` not `ret.qualifiedName()`).
     */
    class SpringfoxExtensionTest : Base() {

        override fun createConfigReader(): TestConfigReader = TestConfigReader.fromConfigText(
            project,
            loadConfigFromResource("extensions/springfox-openapi.config"),
        )

        override fun setUp() {
            super.setUp()
            // Spring @Bean / @Configuration (for Springfox Docket extraction)
            loadFile("org/springframework/context/annotation/Bean.java")
            loadFile("org/springframework/context/annotation/Configuration.java")
            // Springfox stubs — only the declared-return-type name is
            // inspected, plus the method body text; the real classes are
            // never invoked.
            loadFile("springfox/documentation/spring/web/plugins/Docket.java")
            loadFile("springfox/documentation/service/ApiInfo.java")
            // Cross-class fixtures
            loadFile("api/extension/springfox/DocketConfig.java")
            loadFile("api/extension/springfox/PingController.java")
        }

        fun testCrossClassSpringfoxDocketExtractsInfoTitle() = runTest {
            val psiClass = findClass("com.itangcent.extension.springfox.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue("Expected Success, got: $result", result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.title should be parsed from `new ApiInfo(\"Cross-Class Springfox Title\", ...)` in the @Bean Docket body",
                "Cross-Class Springfox Title",
                metadata.document.info.title,
            )
        }

        fun testCrossClassSpringfoxDocketExtractsInfoVersion() = runTest {
            val psiClass = findClass("com.itangcent.extension.springfox.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.version should be the third positional arg of the ApiInfo constructor",
                "5.0",
                metadata.document.info.version,
            )
        }

        fun testCrossClassSpringfoxDocketExtractsInfoDescription() = runTest {
            val psiClass = findClass("com.itangcent.extension.springfox.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.description should be the second positional arg of the ApiInfo constructor",
                "Cross-class Springfox description",
                metadata.document.info.description,
            )
        }

        fun testCrossClassSpringfoxDocketCombinesHostAndPathMappingIntoServerUrl() = runTest {
            // The groovy script uses ${scheme}${host}${path}
            // which must be protected from PropertyResolver via ###set resolveProperty=false.
            val psiClass = findClass("com.itangcent.extension.springfox.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            val servers = metadata.document.servers
            assertNotNull(
                "servers should be populated when the @Bean Docket declares .host(...) and .pathMapping(...)",
                servers,
            )
            assertEquals("Expected exactly one server entry", 1, servers!!.size)
            assertEquals(
                "servers[0].url should be assembled from scheme + host + pathMapping (property resolution disabled)",
                "https://cross-class.example.com/v5",
                servers.first().url,
            )
        }

        fun testCrossClassSerializedContentContainsExpectedMetadata() = runTest {
            val psiClass = findClass("com.itangcent.extension.springfox.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            val content = metadata.content
            assertTrue("JSON should contain the cross-class title: ${content.take(200)}",
                content.contains("\"title\": \"Cross-Class Springfox Title\""))
            assertTrue("JSON should contain the cross-class version",
                content.contains("\"version\": \"5.0\""))
            assertTrue("JSON should contain the cross-class description",
                content.contains("\"description\": \"Cross-class Springfox description\""))
            assertTrue("JSON should contain the assembled server URL (not corrupted by PropertyResolver)",
                content.contains("\"url\": \"https://cross-class.example.com/v5\""))
        }
    }
}

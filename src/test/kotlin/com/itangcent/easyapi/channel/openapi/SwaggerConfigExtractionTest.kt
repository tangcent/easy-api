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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Verifies that the dedicated OpenAPI-channel config files
 * (`swagger3-openapi.config`, `swagger-openapi.config`, `springfox-openapi.config`)
 * correctly map `@OpenAPIDefinition` (Swagger v3), `@SwaggerDefinition`
 * (Swagger v2), and Springfox `@Bean Docket` to the OpenApi channel's rule
 * keys (`openapi.info.title`, `openapi.info.version`,
 * `openapi.info.description`, `openapi.server.url`).
 *
 * The extension configs use `helper.findClassesByAnnotation(...)` /
 * `helper.findMethodsByAnnotation(...)` (global lookup) instead of
 * `it.annMap(...)` (per-controller). This means:
 *  - Each extension's rule keys search the WHOLE project for the annotated class,
 *    not just the controller being exported.
 *  - When multiple extensions define the same rule key (all 3 define
 *    `openapi.info.title`), loading them together causes interference — the
 *    first rule that returns a non-null value wins.
 *
 * To avoid interference, each extension is tested in its OWN inner class with
 * its OWN `createConfigReader()` loading ONLY that extension's config file.
 * Each inner class also loads ONLY the fixtures it needs — no cross-extension
 * fixture leakage.
 *
 * Document-level annotations (`@OpenAPIDefinition`, `@SwaggerDefinition`) are
 * placed on a SEPARATE `@Configuration` class, never on the exported
 * `@RestController`. This mirrors the real-world convention and ensures the
 * global lookup finds the annotation regardless of which controller is exported.
 *
 * ## Inner class layout
 *
 * - [Swagger3Test] — `swagger3-openapi.config` + `OpenApiConfig` (has
 *   `@OpenAPIDefinition`) + `PingController` (plain, exported).
 * - [Swagger3NoAnnotationTest] — `swagger3-openapi.config` +
 *   `NoOpenApiDefinitionController` (no `@OpenAPIDefinition` anywhere →
 *   verifies fall-through to defaults).
 * - [Swagger2Test] — `swagger-openapi.config` + `SwaggerConfig` (has
 *   `@SwaggerDefinition` with host) + `PingController` (plain, exported).
 * - [Swagger2InfoOnlyTest] — `swagger-openapi.config` + `SwaggerInfoOnlyConfig`
 *   (has `@SwaggerDefinition` without host → verifies `servers` is omitted but
 *   `info` is still extracted) + `PingController` (plain, exported).
 * - [SpringfoxTest] — `springfox-openapi.config` + `SpringfoxDocketConfig`
 *   (has `@Bean Docket` method).
 *
 * JUnit 3-style `testXxx()` naming is required because
 * [EasyApiLightCodeInsightFixtureTestCase] extends
 * `LightJavaCodeInsightFixtureTestCase` (a JUnit 3 `TestCase` subclass).
 */
class SwaggerConfigExtractionTest {

    /**
     * Shared base class for the extension-specific inner test classes.
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
         */
        protected fun loadSpringStubs() {
            loadFile("spring/RestController.java")
            loadFile("spring/RequestMapping.java")
            loadFile("spring/GetMapping.java")
        }

        /**
         * Exports an endpoint whose `sourceClass` is the given [psiClass],
         * using an explicit JSON [OpenApiConfig] to bypass the ALWAYS_ASK
         * prompt.
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
                metadata = httpMetadata(path = "/api/ping", method = HttpMethod.GET),
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

    // ─── swagger3-openapi.config — @OpenAPIDefinition on a config class ──

    /**
     * Verifies `swagger3-openapi.config` extracts `@OpenAPIDefinition` metadata
     * from a separate `@Configuration` class. The annotation is located globally
     * via `helper.findClassesByAnnotation("io.swagger.v3.oas.annotations.OpenAPIDefinition")`;
     * the exported `PingController` carries no such annotation.
     */
    class Swagger3Test : Base() {

        override fun createConfigReader(): TestConfigReader = TestConfigReader.fromConfigText(
            project,
            loadConfigFromResource("extensions/swagger3-openapi.config"),
        )

        override fun setUp() {
            super.setUp()
            // @Configuration stub (the doc-level annotation now lives on a config class)
            loadFile("org/springframework/context/annotation/Configuration.java")
            // Swagger v3 annotation stubs
            loadFile("io/swagger/v3/oas/annotations/OpenAPIDefinition.java")
            loadFile("io/swagger/v3/oas/annotations/info/Info.java")
            loadFile("io/swagger/v3/oas/annotations/servers/Server.java")
            // Config class carrying @OpenAPIDefinition (document-level metadata)
            loadFile("api/swagger3/OpenApiConfig.java")
            // Plain controller to export (no @OpenAPIDefinition)
            loadFile("api/swagger3/PingController.java")
        }

        fun testOpenApiDefinitionExtractsInfoTitle() = runTest {
            val psiClass = findClass("com.itangcent.swagger3.openapi.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue("Expected Success, got: $result", result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.title should be extracted from @OpenAPIDefinition.info.title",
                "OpenAPIDefinition Title",
                metadata.document.info.title,
            )
        }

        fun testOpenApiDefinitionExtractsInfoVersion() = runTest {
            val psiClass = findClass("com.itangcent.swagger3.openapi.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.version should be extracted from @OpenAPIDefinition.info.version",
                "2.1.0",
                metadata.document.info.version,
            )
        }

        fun testOpenApiDefinitionExtractsInfoDescription() = runTest {
            val psiClass = findClass("com.itangcent.swagger3.openapi.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.description should be extracted from @OpenAPIDefinition.info.description",
                "OpenAPIDefinition Description",
                metadata.document.info.description,
            )
        }

        fun testOpenApiDefinitionExtractsServerUrl() = runTest {
            val psiClass = findClass("com.itangcent.swagger3.openapi.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            val servers = metadata.document.servers
            assertNotNull("servers should be populated when @OpenAPIDefinition declares @Server", servers)
            assertEquals("Expected exactly one server entry", 1, servers!!.size)
            assertEquals(
                "servers[0].url should be extracted from @OpenAPIDefinition.servers[0].url",
                "https://test.example.com",
                servers.first().url,
            )
        }
    }

    // ─── swagger3-openapi.config — fall-through to defaults ─────────────

    /**
     * Verifies that when NO class in the project has `@OpenAPIDefinition`,
     * the channel falls through to built-in defaults (project name for title,
     * "1.0.0" for version, null for description / servers).
     *
     * Document-level rule keys are evaluated ONCE per export (without an
     * element context). When the global lookup finds no annotated class, the
     * rules return null → channel falls through to defaults.
     */
    class Swagger3NoAnnotationTest : Base() {

        override fun createConfigReader(): TestConfigReader = TestConfigReader.fromConfigText(
            project,
            loadConfigFromResource("extensions/swagger3-openapi.config"),
        )

        override fun setUp() {
            super.setUp()
            // Swagger v3 annotation stubs (needed so the @OpenAPIDefinition
            // annotation class is resolvable, even though no class uses it)
            loadFile("io/swagger/v3/oas/annotations/OpenAPIDefinition.java")
            loadFile("io/swagger/v3/oas/annotations/info/Info.java")
            loadFile("io/swagger/v3/oas/annotations/servers/Server.java")
            // Plain controller WITHOUT @OpenAPIDefinition
            loadFile("api/swagger3/NoOpenApiDefinitionController.java")
            // NOTE: OpenApiConfig is intentionally NOT loaded — otherwise the
            // global lookup would find its @OpenAPIDefinition and the
            // fall-through test would fail.
        }

        fun testNoOpenApiDefinitionFallsThroughToDefaults() = runTest {
            val psiClass = findClass("com.itangcent.swagger3.openapi.NoOpenApiDefinitionController")
            assertNotNull("Should find NoOpenApiDefinitionController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            // info.title falls through to project name (or "API"); version to "1.0.0".
            assertNotNull("info.title should have a default even without @OpenAPIDefinition", metadata.document.info.title)
            assertEquals(
                "info.version should fall through to default '1.0.0'",
                "1.0.0",
                metadata.document.info.version,
            )
            // No @Server → servers is null (no serverUrl resolved).
            assertNull(
                "servers should be null when no @OpenAPIDefinition provides @Server",
                metadata.document.servers,
            )
        }
    }

    // ─── swagger-openapi.config — @SwaggerDefinition with host ───────────

    /**
     * Verifies `swagger-openapi.config` extracts `@SwaggerDefinition` metadata
     * (with host) from a separate `@Configuration` class. The exported
     * `PingController` carries no such annotation.
     */
    class Swagger2Test : Base() {

        override fun createConfigReader(): TestConfigReader = TestConfigReader.fromConfigText(
            project,
            loadConfigFromResource("extensions/swagger-openapi.config"),
        )

        override fun setUp() {
            super.setUp()
            // @Configuration stub (the doc-level annotation now lives on a config class)
            loadFile("org/springframework/context/annotation/Configuration.java")
            // Swagger v2 annotation stubs
            loadFile("io/swagger/annotations/SwaggerDefinition.java")
            loadFile("io/swagger/annotations/Info.java")
            // Config class carrying @SwaggerDefinition (with host)
            loadFile("api/swagger2/SwaggerConfig.java")
            // Plain controller to export (no @SwaggerDefinition)
            loadFile("api/swagger2/PingController.java")
        }

        fun testSwaggerDefinitionExtractsInfoTitle() = runTest {
            val psiClass = findClass("com.itangcent.swagger2.openapi.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue("Expected Success, got: $result", result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.title should be extracted from @SwaggerDefinition.info.title",
                "V2 API",
                metadata.document.info.title,
            )
        }

        fun testSwaggerDefinitionExtractsInfoVersion() = runTest {
            val psiClass = findClass("com.itangcent.swagger2.openapi.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.version should be extracted from @SwaggerDefinition.info.version",
                "1.0",
                metadata.document.info.version,
            )
        }

        fun testSwaggerDefinitionExtractsInfoDescription() = runTest {
            val psiClass = findClass("com.itangcent.swagger2.openapi.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.description should be extracted from @SwaggerDefinition.info.description",
                "V2 desc",
                metadata.document.info.description,
            )
        }

        fun testSwaggerDefinitionCombinesHostBasePathAndSchemeIntoServerUrl() = runTest {
            val psiClass = findClass("com.itangcent.swagger2.openapi.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            val servers = metadata.document.servers
            assertNotNull("servers should be populated when @SwaggerDefinition declares host", servers)
            assertEquals("Expected exactly one server entry", 1, servers!!.size)
            assertEquals(
                "servers[0].url should be assembled from scheme + host + basePath",
                "https://api.example.com/v2",
                servers.first().url,
            )
        }
    }

    // ─── swagger-openapi.config — @SwaggerDefinition info-only ──────────

    /**
     * Verifies `swagger-openapi.config` when `@SwaggerDefinition` declares
     * only `info` (no `host`) — `servers` should be omitted, but `info` fields
     * should still be extracted.
     *
     * Isolated from [Swagger2Test] because both config classes have
     * `@SwaggerDefinition` — loading them together would cause the global
     * lookup to return whichever class is `cls[0]`, making the test flaky.
     */
    class Swagger2InfoOnlyTest : Base() {

        override fun createConfigReader(): TestConfigReader = TestConfigReader.fromConfigText(
            project,
            loadConfigFromResource("extensions/swagger-openapi.config"),
        )

        override fun setUp() {
            super.setUp()
            // @Configuration stub (the doc-level annotation now lives on a config class)
            loadFile("org/springframework/context/annotation/Configuration.java")
            // Swagger v2 annotation stubs
            loadFile("io/swagger/annotations/SwaggerDefinition.java")
            loadFile("io/swagger/annotations/Info.java")
            // Config class carrying @SwaggerDefinition (info only, no host)
            loadFile("api/swagger2/SwaggerInfoOnlyConfig.java")
            // Plain controller to export (no @SwaggerDefinition)
            loadFile("api/swagger2/PingController.java")
        }

        fun testSwaggerDefinitionWithoutHostOmitsServersButKeepsInfo() = runTest {
            val psiClass = findClass("com.itangcent.swagger2.openapi.PingController")
            assertNotNull("Should find PingController", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            // info.title is still extracted from @SwaggerDefinition.info.title.
            assertEquals(
                "info.title should still be extracted when host is absent",
                "Info Only API",
                metadata.document.info.title,
            )
            // No host → serverUrl is null → servers is omitted.
            assertNull(
                "servers should be null when @SwaggerDefinition has no host (groovy script returns null)",
                metadata.document.servers,
            )
        }
    }

    // ─── springfox-openapi.config — @Bean Docket ────────────────────────

    /**
     * Verifies `springfox-openapi.config` extracts Springfox `Docket` metadata
     * from a `@Bean` method on the controller being exported.
     *
     * Each rule key's groovy script is self-contained (no `${...}`
     * cross-references) and uses the correct script-context method names
     * (`m.sourceCode()` not `m.text()`, `ret.name()` not `ret.qualifiedName()`).
     */
    class SpringfoxTest : Base() {

        override fun createConfigReader(): TestConfigReader = TestConfigReader.fromConfigText(
            project,
            loadConfigFromResource("extensions/springfox-openapi.config"),
        )

        override fun setUp() {
            super.setUp()
            // Spring @Bean / @Configuration
            loadFile("org/springframework/context/annotation/Bean.java")
            loadFile("org/springframework/context/annotation/Configuration.java")
            // Springfox stubs
            loadFile("springfox/documentation/spring/web/plugins/Docket.java")
            loadFile("springfox/documentation/service/ApiInfo.java")
            // Springfox fixture controller (has @Bean Docket method)
            loadFile("api/springfox/SpringfoxDocketConfig.java")
        }

        fun testSpringfoxDocketExtractsInfoTitle() = runTest {
            val psiClass = findClass("com.itangcent.swagger2.openapi.SpringfoxDocketConfig")
            assertNotNull("Should find SpringfoxDocketConfig", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue("Expected Success, got: $result", result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.title should be parsed from `new ApiInfo(\"Springfox Title\", ...)` in the @Bean Docket body",
                "Springfox Title",
                metadata.document.info.title,
            )
        }

        fun testSpringfoxDocketExtractsInfoVersion() = runTest {
            val psiClass = findClass("com.itangcent.swagger2.openapi.SpringfoxDocketConfig")
            assertNotNull("Should find SpringfoxDocketConfig", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.version should be the third positional arg of the ApiInfo constructor",
                "2.3.0",
                metadata.document.info.version,
            )
        }

        fun testSpringfoxDocketExtractsInfoDescription() = runTest {
            val psiClass = findClass("com.itangcent.swagger2.openapi.SpringfoxDocketConfig")
            assertNotNull("Should find SpringfoxDocketConfig", psiClass)
            val result = exportChannel(psiClass!!)
            assertTrue(result is ExportResult.Success)
            val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
            assertEquals(
                "info.description should be the second positional arg of the ApiInfo constructor",
                "Springfox Desc",
                metadata.document.info.description,
            )
        }

        fun testSpringfoxDocketCombinesHostAndPathMappingIntoServerUrl() = runTest {
            val psiClass = findClass("com.itangcent.swagger2.openapi.SpringfoxDocketConfig")
            assertNotNull("Should find SpringfoxDocketConfig", psiClass)
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
                "servers[0].url should be assembled from scheme + host + pathMapping",
                "https://api.example.com/v3",
                servers.first().url,
            )
        }
    }
}

package com.itangcent.easyapi.channel.openapi

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.core.config.ConfigReader
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
 * Rule evaluation in `OpenApiChannel.export`.
 *
 * Pins the contract that `OpenApiChannel.export` evaluates the four
 * document-metadata rule keys (`openapi.info.title`, `openapi.info.version`,
 * `openapi.info.description`, `openapi.server.url`) against
 * `ApiEndpoint.sourceClass`, and fires the `openapi.format.after` event
 * with the built `OpenApiDocument` exposed via the rule context extension
 * `"document"`.
 *
 * The options-panel / settings tiers for `info.*` / `server.url` were
 * removed — those values vary per project and belong in rules only.
 * Precedence is now: **rules > built-in defaults**
 * (`projectName ?: "API"`, `"1.0.0"`, `null` for description, `null` for
 * server url). `openapi.host` is a legacy alias for `openapi.server.url`.
 *
 * Pattern: each test wires a [TestConfigReader] with literal rule values
 * (no groovy scripts needed — the [com.itangcent.easyapi.core.rule.parser.LiteralParser]
 * returns the literal as-is regardless of the context element). The source
 * class is a mock `PsiClass`; the source method is a mock `PsiMethod` so
 * `fireFormatAfterEvent` has a target.
 *
 * JUnit 3-style `testXxx()` naming is required because
 * [EasyApiLightCodeInsightFixtureTestCase] extends
 * `LightJavaCodeInsightFixtureTestCase` (a JUnit 3 `TestCase` subclass) —
 * `@Test` annotations are not picked up by the JUnit 3 runner.
 */
class OpenApiRuleEvaluationTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var channel: OpenApiChannel

    override fun setUp() {
        super.setUp()
        channel = OpenApiChannel()
    }

    override fun tearDown() {
        try {
            // Reset OpenApiSettings to defaults so tests don't bleed into each other.
            // Only `outputFormat` is a persisted field — envelope fields
            // were removed and live only in rules / defaults.
            runCatching {
                SettingBinder.getInstance(project).update(OpenApiSettings::class) {
                    outputFormat = "ALWAYS_ASK"
                }
            }
        } finally {
            super.tearDown()
        }
    }

    override fun createConfigReader(): ConfigReader = TestConfigReader.empty(project)

    /**
     * Builds an [ExportContext] with an explicit JSON [OpenApiConfig] so the
     * `ALWAYS_ASK` prompt (which would block on EDT) is bypassed — these tests
     * focus on rule-resolved envelope metadata, not format selection.
     */
    private fun exportContext(endpoints: List<ApiEndpoint>): ExportContext = ExportContext(
        project = project,
        endpoints = endpoints,
        channelId = "openapi",
        channelConfig = OpenApiConfig(outputFormat = OpenApiOutputFormat.JSON),
    )

    // ─── Rule resolves document metadata ───────────────────────────────────

    fun testInfoTitleRulePopulatesDocument() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "openapi.info.title" to "Rule-Resolved Title",
            )
        )
        val endpoint = httpEndpointWithSourceClass(
            path = "/users",
            method = HttpMethod.GET,
            methodName = "listUsers",
            className = "com.example.UserController",
        )
        val result = channel.export(exportContext(listOf(endpoint)))
        assertTrue("Expected Success, got: $result", result is ExportResult.Success)
        val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
        assertEquals("Rule-Resolved Title", metadata.document.info.title)
    }

    fun testInfoVersionRulePopulatesDocument() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "openapi.info.version" to "9.9.9",
            )
        )
        val endpoint = httpEndpointWithSourceClass(
            path = "/users",
            method = HttpMethod.GET,
            methodName = "listUsers",
            className = "com.example.UserController",
        )
        val result = channel.export(exportContext(listOf(endpoint)))
        assertTrue(result is ExportResult.Success)
        val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
        assertEquals("9.9.9", metadata.document.info.version)
    }

    fun testInfoDescriptionRulePopulatesDocument() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "openapi.info.description" to "Description from rule",
            )
        )
        val endpoint = httpEndpointWithSourceClass(
            path = "/users",
            method = HttpMethod.GET,
            methodName = "listUsers",
            className = "com.example.UserController",
        )
        val result = channel.export(exportContext(listOf(endpoint)))
        assertTrue(result is ExportResult.Success)
        val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
        assertEquals("Description from rule", metadata.document.info.description)
    }

    fun testServerUrlRulePopulatesServers() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "openapi.server.url" to "https://rule.example.com",
            )
        )
        val endpoint = httpEndpointWithSourceClass(
            path = "/users",
            method = HttpMethod.GET,
            methodName = "listUsers",
            className = "com.example.UserController",
        )
        val result = channel.export(exportContext(listOf(endpoint)))
        assertTrue(result is ExportResult.Success)
        val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
        assertEquals(
            "Expected exactly one server entry",
            1,
            metadata.document.servers?.size ?: 0,
        )
        assertEquals("https://rule.example.com", metadata.document.servers!!.first().url)
    }

    // ─── Legacy alias: openapi.host → openapi.server.url ──────────────────

    fun testHostRuleServesAsLegacyFallbackForServerUrl() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "openapi.host" to "https://legacy.example.com",
            )
        )
        val endpoint = httpEndpointWithSourceClass(
            path = "/users",
            method = HttpMethod.GET,
            methodName = "listUsers",
            className = "com.example.UserController",
        )
        val result = channel.export(exportContext(listOf(endpoint)))
        assertTrue(result is ExportResult.Success)
        val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
        assertEquals(
            "Expected the legacy openapi.host value to populate servers[0].url",
            "https://legacy.example.com",
            metadata.document.servers!!.first().url,
        )
    }

    // ─── Built-in defaults (no rules fire) ────────────────────────────────

    fun testNoRulesFallbackToBuiltInDefaults() = runTest {
        // No rules defined — rule evaluation returns null for every key.
        // The channel falls back to built-in defaults:
        //   infoTitle = projectName ?: "API"
        //   infoVersion = "1.0.0"
        //   infoDescription = null
        //   serverUrl = null
        val endpoint = httpEndpointWithSourceClass(
            path = "/users",
            method = HttpMethod.GET,
            methodName = "listUsers",
            className = "com.example.UserController",
        )
        val result = channel.export(exportContext(listOf(endpoint)))
        assertTrue(result is ExportResult.Success)
        val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
        // info.title falls back to project name (or "API" when null).
        assertNotNull("info.title should have a default even without rules", metadata.document.info.title)
        assertEquals(
            "info.version should fall back to default '1.0.0'",
            "1.0.0",
            metadata.document.info.version,
        )
        assertNull(
            "info.description should be null when no rule produces a value",
            metadata.document.info.description,
        )
        assertNull(
            "servers should be null when no rule produces a serverUrl",
            metadata.document.servers,
        )
    }

    // ─── openapi.format.after event ────────────────────────────────────────

    fun testFormatAfterEventFiresWithDocumentExposedInContext() = runTest {
        // A groovy script that mutates the document's paths map (LinkedHashMap
        // is mutable even though the property reference is val). If the event
        // fires and `document` is bound, paths will be cleared before
        // serialization — so the serialized output will have an empty paths
        // object `"paths":{}`.
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "openapi.format.after" to "groovy:document.paths.clear()",
            )
        )
        val endpoint = httpEndpointWithSourceClass(
            path = "/users",
            method = HttpMethod.GET,
            methodName = "listUsers",
            className = "com.example.UserController",
        )
        val result = channel.export(exportContext(listOf(endpoint)))
        assertTrue(result is ExportResult.Success)
        val metadata = (result as ExportResult.Success).metadata as OpenApiExportMetadata
        assertTrue(
            "format.after script should have cleared paths; got: ${metadata.document.paths}",
            metadata.document.paths.isEmpty(),
        )
        // The serialized content should contain an empty paths object.
        assertTrue(
            "Serialized output should contain empty paths after format.after cleared them; got: ${metadata.content}",
            metadata.content.contains("\"paths\"") && !metadata.content.contains("\"/users\""),
        )
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    /**
     * Builds an [ApiEndpoint] with both a mock [PsiMethod] (so `fireFormatAfterEvent`
     * has a target) and a mock [PsiClass] (so `resolveRuleBasedEnvelope` has a
     * source class to evaluate rules against).
     */
    private fun httpEndpointWithSourceClass(
        path: String,
        method: HttpMethod,
        methodName: String,
        className: String,
    ): ApiEndpoint {
        val psiMethod = mock<PsiMethod> { on { this.name } doReturn methodName }
        val psiClass = mock<PsiClass> { on { this.qualifiedName } doReturn className }
        return ApiEndpoint(
            name = methodName,
            metadata = httpMetadata(path = path, method = method),
            sourceMethod = psiMethod,
            sourceClass = psiClass,
            className = className,
        )
    }
}

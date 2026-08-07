package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.extension.BuiltInExtensionExecutionHarness
import com.itangcent.easyapi.core.extension.BuiltInExtensionScenarioLedger
import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.core.extension.ExtensionFixturePlan
import com.itangcent.easyapi.core.extension.ResolvedBuiltInExtensionScenario
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class BuiltInExtensionOpenApiScenarioTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var harness: BuiltInExtensionExecutionHarness

    override fun createConfigReader() = TestConfigReader.empty(project)

    override fun setUp() {
        super.setUp()
        ExtensionConfigRegistry.loadExtensions()
        harness = BuiltInExtensionExecutionHarness(
            project = project,
            loadPsiFile = { path, content -> loadFile(path, content) },
            waitForClass = { fqn -> waitForClass(fqn) }
        )
    }

    fun testDocumentExtensionsExportExpectedStructuredMetadata() = runTest {
        documentScenarios.forEach { descriptor ->
            assertDocumentScenario(descriptor)
        }
        harness.assertNoPriorScenarioState()
    }

    private suspend fun assertDocumentScenario(descriptor: OpenApiScenarioDescriptor) {
        val scenario = requireNotNull(BuiltInExtensionScenarioLedger.resolvedScenarios().singleOrNull {
            it.extension.code == descriptor.extensionCode
        }) {
            "Missing extension scenario: ${descriptor.extensionCode}"
        }
        val onClass = requireNotNull(scenario.extension.onClass) {
            scenarioMessage(scenario, "fixture", "on-class declaration for ${descriptor.id}")
        }

        harness.execute(scenario, fixturePlan(descriptor)) { session ->
            val availability = session.probeFqn(onClass)
            assertTrue(
                scenarioMessage(scenario, "fixture", "${descriptor.id} on-class ${availability.fqn}"),
                availability.resolvable
            )

            val reader = session.installIsolatedReader()
            documentRuleKeys.forEach { ruleKey ->
                assertTrue(
                    scenarioMessage(scenario, "reload", "${descriptor.id} resolved $ruleKey rule"),
                    ruleKey in reader.extensionRuleKeys
                )
                assertTrue(
                    scenarioMessage(scenario, "reload", "${descriptor.id} $ruleKey source=extension"),
                    reader.sourcesFor(ruleKey).any { source -> source.sourceId == EXTENSION_SOURCE_ID }
                )
            }

            val controller = requireNotNull(findClass(descriptor.controllerFqn)) {
                scenarioMessage(scenario, "fixture", "${descriptor.id} controller ${descriptor.controllerFqn}")
            }
            val endpoints = session.reacquireServices().springMvcExporter.export(controller)
            assertTrue(
                scenarioMessage(scenario, "export", "${descriptor.id} Spring MVC endpoint"),
                endpoints.isNotEmpty()
            )

            val result = OpenApiChannel().export(
                ExportContext(
                    project = project,
                    endpoints = endpoints,
                    channelId = "openapi",
                    channelConfig = OpenApiConfig(outputFormat = OpenApiOutputFormat.JSON)
                )
            )
            val success = requireNotNull(result as? ExportResult.Success) {
                scenarioMessage(scenario, "export", "${descriptor.id} OpenAPI success; actual=$result")
            }
            val metadata = requireNotNull(success.metadata as? OpenApiExportMetadata) {
                scenarioMessage(scenario, "export", "${descriptor.id} structured OpenAPI metadata")
            }
            val document = metadata.document

            assertEquals(
                scenarioMessage(scenario, "assert", "${descriptor.id} info.title"),
                descriptor.expectedTitle,
                document.info.title
            )
            assertEquals(
                scenarioMessage(scenario, "assert", "${descriptor.id} info.version"),
                descriptor.expectedVersion,
                document.info.version
            )
            assertEquals(
                scenarioMessage(scenario, "assert", "${descriptor.id} info.description"),
                descriptor.expectedDescription,
                document.info.description
            )
            assertEquals(
                scenarioMessage(scenario, "assert", "${descriptor.id} servers[0].url"),
                descriptor.expectedServerUrl,
                document.servers?.singleOrNull()?.url
            )
        }
    }

    private fun fixturePlan(descriptor: OpenApiScenarioDescriptor): ExtensionFixturePlan {
        val sources = linkedMapOf<String, String>()
        descriptor.fixtureResources.forEach { resource ->
            val content = resourceText(resource)
            sources[qualifiedName(content, resource)] = content
        }
        return ExtensionFixturePlan(psiStubs = sources)
    }

    private fun resourceText(resource: String): String {
        return requireNotNull(javaClass.classLoader.getResourceAsStream(resource)) {
            "Missing test fixture resource: $resource"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun qualifiedName(content: String, resource: String): String {
        val packageName = requireNotNull(packagePattern.find(content)?.groupValues?.get(1)) {
            "Fixture does not declare a package: $resource"
        }
        val typeName = requireNotNull(typePattern.find(content)?.groupValues?.get(1)) {
            "Fixture does not declare a top-level type: $resource"
        }
        return "$packageName.$typeName"
    }

    private fun scenarioMessage(
        scenario: ResolvedBuiltInExtensionScenario,
        phase: String,
        observable: String
    ): String {
        val defaultState = if (scenario.extension.defaultEnabled) "enabled" else "disabled"
        return "extension=${scenario.extension.code}; default=$defaultState; phase=$phase; " +
            "condition=${scenario.conditionInput}; observable=$observable"
    }

    private data class OpenApiScenarioDescriptor(
        val id: String,
        val extensionCode: String,
        val controllerFqn: String,
        val fixtureResources: List<String>,
        val expectedTitle: String,
        val expectedVersion: String,
        val expectedDescription: String,
        val expectedServerUrl: String
    )

    private companion object {
        const val EXTENSION_SOURCE_ID = "extension"

        val documentRuleKeys = setOf(
            OpenApiRuleKeys.OPENAPI_INFO_TITLE.name,
            OpenApiRuleKeys.OPENAPI_INFO_VERSION.name,
            OpenApiRuleKeys.OPENAPI_INFO_DESCRIPTION.name,
            OpenApiRuleKeys.OPENAPI_SERVER_URL.name
        )

        val commonSpringResources = listOf(
            "spring/RestController.java",
            "spring/RequestMapping.java",
            "spring/GetMapping.java"
        )

        val documentScenarios = listOf(
            OpenApiScenarioDescriptor(
                id = "springfox-docket",
                extensionCode = "springfox-openapi",
                controllerFqn = "com.itangcent.extension.springfox.PingController",
                fixtureResources = commonSpringResources + listOf(
                    "org/springframework/context/annotation/Bean.java",
                    "org/springframework/context/annotation/Configuration.java",
                    "springfox/documentation/spring/web/plugins/Docket.java",
                    "springfox/documentation/service/ApiInfo.java",
                    "api/extension/springfox/DocketConfig.java",
                    "api/extension/springfox/PingController.java"
                ),
                expectedTitle = "Cross-Class Springfox Title",
                expectedVersion = "5.0",
                expectedDescription = "Cross-class Springfox description",
                expectedServerUrl = "https://cross-class.example.com/v5"
            ),
            OpenApiScenarioDescriptor(
                id = "swagger-definition",
                extensionCode = "swagger-openapi",
                controllerFqn = "com.itangcent.extension.swagger2.PingController",
                fixtureResources = commonSpringResources + listOf(
                    "org/springframework/context/annotation/Configuration.java",
                    "io/swagger/annotations/SwaggerDefinition.java",
                    "io/swagger/annotations/Info.java",
                    "api/extension/swagger2/SwaggerConfig.java",
                    "api/extension/swagger2/PingController.java"
                ),
                expectedTitle = "Cross-Class Swagger2 Title",
                expectedVersion = "2.9",
                expectedDescription = "Cross-class SwaggerDefinition description",
                expectedServerUrl = "https://cross-class.example.com/v2"
            ),
            OpenApiScenarioDescriptor(
                id = "openapi-definition",
                extensionCode = "swagger3-openapi",
                controllerFqn = "com.itangcent.extension.swagger3.PingController",
                fixtureResources = commonSpringResources + listOf(
                    "org/springframework/context/annotation/Configuration.java",
                    "io/swagger/v3/oas/annotations/OpenAPIDefinition.java",
                    "io/swagger/v3/oas/annotations/info/Info.java",
                    "io/swagger/v3/oas/annotations/servers/Server.java",
                    "api/extension/swagger3/OpenApiConfig.java",
                    "api/extension/swagger3/PingController.java"
                ),
                expectedTitle = "Cross-Class Swagger3 Title",
                expectedVersion = "9.1.0",
                expectedDescription = "Cross-class OpenAPIDefinition description",
                expectedServerUrl = "https://cross-class.example.com"
            )
        )

        val packagePattern = Regex("""package\s+([A-Za-z_][\w.]*)\s*;""")
        val typePattern = Regex("""(?m)^\s*(?:public\s+)?(?:class|interface|enum|@interface)\s+([A-Za-z_]\w*)""")
    }
}

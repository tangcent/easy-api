package com.itangcent.easyapi.core.extension

import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.core.rule.RuleProvider
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import java.nio.file.Files

class BuiltInExtensionExecutionHarnessTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var harness: BuiltInExtensionExecutionHarness

    override fun setUp() {
        super.setUp()
        ExtensionConfigRegistry.loadExtensions()
        harness = BuiltInExtensionExecutionHarness(
            project = project,
            loadPsiFile = { path, content -> loadFile(path, content) },
            waitForClass = { fqn -> waitForClass(fqn) }
        )
    }

    fun testPositiveOnClassProbePreparesFixtureBeforeExtensionCollection() = runTest {
        val scenario = scenario("spring-webflux")
        val onClass = requireNotNull(scenario.extension.onClass)
        var releasedResourceRoot = null as java.nio.file.Path?

        harness.execute(
            scenario = scenario,
            fixturePlan = ExtensionFixturePlan(
                psiStubs = mapOf(onClass to genericClassStub(onClass)),
                physicalResources = mapOf(
                    "src/main/resources/application.properties" to "fixture.marker=webflux"
                )
            )
        ) { session ->
            val probe = session.probeFqn(onClass)
            assertTrue("Expected prepared on-class FQN to resolve: ${probe.fqn}", probe.resolvable)
            assertTrue(
                "Expected physical fixture resource under the resolved module root",
                Files.isRegularFile(session.resourceRoot.resolve("src/main/resources/application.properties"))
            )

            val reader = session.installIsolatedReader()
            val targetRuleKey = reader.extensionRuleKeys.firstOrNull {
                it.startsWith("json.rule.convert")
            }
            assertNotNull(
                "Expected WebFlux extension rules to be loaded through the isolated reader",
                targetRuleKey
            )
            assertTrue(
                "Expected the target rule source to be extension",
                reader.sourcesFor(targetRuleKey!!).all { it.sourceId == "extension" }
            )

            val services = session.reacquireServices()
            assertNotNull("Expected a fresh rule provider", services.ruleProvider)
            assertNotNull("Expected a fresh rule engine", services.ruleEngine)
            assertNotNull("Expected a fresh PSI helper", services.psiClassHelper)
            assertNotNull("Expected a fresh Spring MVC exporter", services.springMvcExporter)
            assertTrue(
                "Expected fresh rules after the reader reload",
                services.ruleProvider.getRules(RuleKeys.JSON_RULE_CONVERT).isNotEmpty()
            )

            session.setSessionMarker()
            releasedResourceRoot = session.resourceRoot
        }

        assertFalse("Expected temporary physical resources to be released", Files.exists(releasedResourceRoot!!))
        harness.assertNoPriorScenarioState()
        assertTrue(
            "Expected rule cache to reflect the restored baseline reader",
            RuleProvider.getInstance(project).getRules(RuleKeys.JSON_RULE_CONVERT).isEmpty()
        )
    }

    fun testNegativeOnClassProbePreventsExtensionRulesFromEnteringReader() = runTest {
        val scenario = scenario("spring-webflux")
        val onClass = requireNotNull(scenario.extension.onClass)

        harness.execute(scenario) { session ->
            val probe = session.probeFqn(onClass)
            assertFalse("Expected missing on-class FQN to remain unresolved: ${probe.fqn}", probe.resolvable)

            val reader = session.installIsolatedReader()
            assertTrue(
                "Expected no WebFlux rules when its on-class FQN is absent",
                reader.extensionRuleKeys.isEmpty()
            )
            assertTrue(
                "Expected no extension source metadata for the missing on-class rule",
                reader.sourcesFor("json.rule.convert").isEmpty()
            )
        }
    }

    fun testCleanupVerifiesPriorExtensionSourceAndSessionBeforeNextScenario() = runTest {
        val webfluxScenario = scenario("spring-webflux")
        val webfluxOnClass = requireNotNull(webfluxScenario.extension.onClass)
        val convertsScenario = scenario("converts")
        var firstResourceRoot = null as java.nio.file.Path?

        harness.execute(
            scenario = webfluxScenario,
            fixturePlan = ExtensionFixturePlan(
                psiStubs = mapOf(webfluxOnClass to genericClassStub(webfluxOnClass)),
                physicalResources = mapOf("src/main/resources/first.properties" to "fixture.marker=first")
            )
        ) { session ->
            assertTrue(session.probeFqn(webfluxOnClass).resolvable)
            assertTrue(session.installIsolatedReader().extensionRuleKeys.isNotEmpty())
            session.setSessionMarker()
            firstResourceRoot = session.resourceRoot
        }

        harness.execute(
            scenario = convertsScenario,
            fixturePlan = ExtensionFixturePlan(
                physicalResources = mapOf("src/main/resources/second.properties" to "fixture.marker=second")
            )
        ) { session ->
            assertTrue(
                "Expected the previous extension source and session marker to be absent before this scenario",
                session.priorScenarioStateWasClean
            )
            assertTrue(session.installIsolatedReader().extensionRuleKeys.isNotEmpty())
        }

        assertFalse("Expected the first scenario resources to be released", Files.exists(firstResourceRoot!!))
        harness.assertNoPriorScenarioState()
    }

    private fun scenario(code: String): ResolvedBuiltInExtensionScenario {
        return requireNotNull(BuiltInExtensionScenarioLedger.resolvedScenarios().singleOrNull {
            it.extension.code == code
        }) {
            "Missing scenario for extension '$code'"
        }
    }

    private fun genericClassStub(fqn: String): String {
        val packageName = fqn.substringBeforeLast('.')
        val className = fqn.substringAfterLast('.')
        return "package $packageName; public class $className<T> {}"
    }
}

package com.itangcent.easyapi.core.extension

import com.itangcent.easyapi.core.psi.JsonOption
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class BuiltInExtensionFieldOrderScenarioTest : EasyApiLightCodeInsightFixtureTestCase() {

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

    fun testAlphabeticallyDescendingScenarioOrdersFieldsFromZToA() = runTest {
        assertFieldOrder(
            extensionCode = "field-order-alphabetically-desc",
            fixtureResources = arrayOf("api/fieldorder/OrderDTO.java"),
            className = "com.itangcent.fieldorder.OrderDTO",
            expectedFieldNames = listOf("zebra", "mango", "banana", "apple")
        )
    }

    fun testAlphabeticallyScenarioOrdersFieldsFromAToZ() = runTest {
        assertFieldOrder(
            extensionCode = "field-order-alphabetically",
            fixtureResources = arrayOf("api/fieldorder/OrderDTO.java"),
            className = "com.itangcent.fieldorder.OrderDTO",
            expectedFieldNames = listOf("apple", "banana", "mango", "zebra")
        )
    }

    fun testChildFirstScenarioOrdersChildFieldsBeforeParentFields() = runTest {
        assertFieldOrder(
            extensionCode = "field-order-child-first",
            fixtureResources = arrayOf(
                "api/fieldorder/ParentDTO.java",
                "api/fieldorder/ChildDTO.java"
            ),
            className = "com.itangcent.fieldorder.ChildDTO",
            expectedFieldNames = listOf("childField", "parentField")
        )
    }

    fun testParentFirstScenarioOrdersParentFieldsBeforeChildFields() = runTest {
        assertFieldOrder(
            extensionCode = "field-order-parent-first",
            fixtureResources = arrayOf(
                "api/fieldorder/ParentDTO.java",
                "api/fieldorder/ChildDTO.java"
            ),
            className = "com.itangcent.fieldorder.ChildDTO",
            expectedFieldNames = listOf("parentField", "childField")
        )
    }

    private suspend fun assertFieldOrder(
        extensionCode: String,
        fixtureResources: Array<String>,
        className: String,
        expectedFieldNames: List<String>
    ) {
        val scenario = scenario(extensionCode)
        harness.execute(scenario, fieldOrderPlan(*fixtureResources)) { session ->
            val reader = session.installIsolatedReader()
            val services = session.reacquireServices()
            assertUndeclaredDefaultRulesAreExcluded(scenario, reader, services)

            val psiClass = requireNotNull(findClass(className)) {
                scenarioMessage(scenario, "fixture", "resolvable field-order DTO $className")
            }
            val model = requireNotNull(services.psiClassHelper.buildObjectModel(psiClass, JsonOption.ALL)?.asObject()) {
                scenarioMessage(scenario, "assert", "field-order DTO object model")
            }

            assertEquals(
                scenarioMessage(scenario, "assert", "ordered field names"),
                expectedFieldNames,
                model.fields.keys.toList()
            )
        }
    }

    private fun assertUndeclaredDefaultRulesAreExcluded(
        scenario: ResolvedBuiltInExtensionScenario,
        reader: InstalledExtensionReader,
        services: ExtensionExecutionServices
    ) {
        val undeclaredDefaultExtensions = ExtensionConfigRegistry.allExtensions()
            .filter { extension ->
                extension.defaultEnabled && extension.code !in scenario.declaredExtensionCodes
            }
        val selection = BuiltInExtensionScenarioLedger.isolatedSelection(scenario).toSet()
        val fieldOrderSources = reader.sourcesFor(RuleKeys.FIELD_ORDER_WITH.name)

        assertTrue(
            scenarioMessage(scenario, "isolation", "undeclared default extensions"),
            undeclaredDefaultExtensions.isNotEmpty()
        )
        assertTrue(
            scenarioMessage(scenario, "isolation", "excluded undeclared default extensions"),
            undeclaredDefaultExtensions.all { extension -> "-${extension.code}" in selection }
        )
        assertEquals(
            scenarioMessage(scenario, "reload", "resolved extension rule keys"),
            setOf(RuleKeys.FIELD_ORDER_WITH.name),
            reader.extensionRuleKeys
        )
        assertEquals(
            scenarioMessage(scenario, "reload", "resolved field-order rule source count"),
            1,
            fieldOrderSources.size
        )
        assertTrue(
            scenarioMessage(scenario, "reload", "resolved field-order rule source"),
            fieldOrderSources.all { source -> source.sourceId == "extension" }
        )
        assertEquals(
            scenarioMessage(scenario, "rule", "resolved field-order rule count"),
            1,
            services.ruleProvider.getRules(RuleKeys.FIELD_ORDER_WITH).size
        )
    }

    private fun scenario(code: String): ResolvedBuiltInExtensionScenario {
        return requireNotNull(BuiltInExtensionScenarioLedger.resolvedScenarios().singleOrNull {
            it.extension.code == code
        }) {
            "Missing extension scenario: $code"
        }
    }

    private fun fieldOrderPlan(vararg resources: String): ExtensionFixturePlan {
        val stubs = linkedMapOf<String, String>()
        resources.forEach { resource ->
            val content = resourceText(resource)
            stubs[qualifiedName(content, resource)] = content
        }
        return ExtensionFixturePlan(psiStubs = stubs)
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

    private companion object {
        val packagePattern = Regex("""package\s+([A-Za-z_][\w.]*)\s*;""")
        val typePattern = Regex("""(?m)^\s*(?:public\s+)?(?:class|interface|enum|@interface)\s+([A-Za-z_]\w*)""")
    }
}

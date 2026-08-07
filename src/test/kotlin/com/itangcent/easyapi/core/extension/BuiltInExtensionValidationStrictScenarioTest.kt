package com.itangcent.easyapi.core.extension

import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.core.util.storage.SessionStorage
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class BuiltInExtensionValidationStrictScenarioTest : EasyApiLightCodeInsightFixtureTestCase() {

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

    fun testStrictValidationGroupsAndNamespacesStayIsolated() = runTest {
        assertStrictValidation(StrictValidationFixture("javax-validation-strict", "javax.validation"))
        assertStrictValidation(StrictValidationFixture("jakarta-validation-strict", "jakarta.validation"))
        harness.assertNoPriorScenarioState()
    }

    private suspend fun assertStrictValidation(fixture: StrictValidationFixture) {
        val scenario = requireNotNull(BuiltInExtensionScenarioLedger.resolvedScenarios().singleOrNull {
            it.extension.code == fixture.extensionCode
        }) {
            "Missing extension scenario: ${fixture.extensionCode}"
        }
        val onClass = requireNotNull(scenario.extension.onClass)

        assertFalse(
            failureMessage(scenario, "selection", "strict validation extension is disabled by default"),
            scenario.extension.defaultEnabled
        )

        for (endpointScenario in strictEndpointScenarios) {
            assertStrictGroupScenario(scenario, fixture, onClass, endpointScenario)
            assertNoValidatedSentinel(scenario, fixture, onClass, endpointScenario)
        }
    }

    private suspend fun assertStrictGroupScenario(
        scenario: ResolvedBuiltInExtensionScenario,
        fixture: StrictValidationFixture,
        onClass: String,
        endpointScenario: StrictEndpointScenario
    ) {
        harness.execute(scenario, strictFixturePlan(fixture, endpointScenario)) { session ->
            assertOnClassAvailability(session, scenario, onClass, endpointScenario)
            val services = installStrictServices(session, scenario, endpointScenario)
            val controller = requireNotNull(findClass(fixture.controllerFqn)) {
                failureMessage(
                    scenario,
                    "fixture",
                    "resolvable strict validation controller for ${endpointScenario.id}"
                )
            }
            val endpoint = requireNotNull(services.springMvcExporter.export(controller).singleOrNull {
                it.httpMetadata?.path == fixture.pathFor(endpointScenario) && it.httpMetadata?.method == HttpMethod.POST
            }) {
                failureMessage(scenario, "export", "POST endpoint ${fixture.pathFor(endpointScenario)}")
            }

            assertRequestRequiredFlags(scenario, endpointScenario, endpoint)
            assertNull(
                failureMessage(scenario, "cleanup", "json-group after ${endpointScenario.id} request export"),
                SessionStorage.getInstance(project).get(SESSION_GROUP_KEY)
            )
        }
    }

    private suspend fun assertNoValidatedSentinel(
        scenario: ResolvedBuiltInExtensionScenario,
        fixture: StrictValidationFixture,
        onClass: String,
        previousEndpointScenario: StrictEndpointScenario
    ) {
        harness.execute(scenario, strictFixturePlan(fixture, sentinelEndpointScenario)) { session ->
            assertOnClassAvailability(session, scenario, onClass, sentinelEndpointScenario)
            val services = installStrictServices(session, scenario, sentinelEndpointScenario)
            val controller = requireNotNull(findClass(fixture.controllerFqn)) {
                failureMessage(
                    scenario,
                    "fixture",
                    "unvalidated sentinel after ${previousEndpointScenario.id} controller"
                )
            }
            val endpoint = requireNotNull(services.springMvcExporter.export(controller).singleOrNull {
                it.httpMetadata?.path == fixture.pathFor(sentinelEndpointScenario) &&
                        it.httpMetadata?.method == HttpMethod.POST
            }) {
                failureMessage(
                    scenario,
                    "export",
                    "unvalidated sentinel after ${previousEndpointScenario.id} endpoint"
                )
            }

            assertRequestRequiredFlags(scenario, sentinelEndpointScenario, endpoint)
            assertNull(
                failureMessage(
                    scenario,
                    "cleanup",
                    "json-group after ${previousEndpointScenario.id}; sentinel=${sentinelEndpointScenario.id}"
                ),
                SessionStorage.getInstance(project).get(SESSION_GROUP_KEY)
            )
        }
    }

    private suspend fun assertOnClassAvailability(
        session: BuiltInExtensionExecutionHarness.ExtensionScenarioSession,
        scenario: ResolvedBuiltInExtensionScenario,
        onClass: String,
        endpointScenario: StrictEndpointScenario
    ) {
        val probe = session.probeFqn(onClass)
        assertTrue(
            failureMessage(
                scenario,
                "fixture",
                "resolvable exact on-class ${probe.fqn} for ${endpointScenario.id}"
            ),
            probe.resolvable
        )
    }

    private suspend fun installStrictServices(
        session: BuiltInExtensionExecutionHarness.ExtensionScenarioSession,
        scenario: ResolvedBuiltInExtensionScenario,
        endpointScenario: StrictEndpointScenario
    ): ExtensionExecutionServices {
        val installedReader = session.installIsolatedReader()
        val requiredRuleKeys = installedReader.extensionRuleKeys.filter {
            it == RuleKeys.FIELD_REQUIRED.name || it.startsWith("${RuleKeys.FIELD_REQUIRED.name}[")
        }
        assertTrue(
            failureMessage(scenario, "reload", "strict required field rules for ${endpointScenario.id}"),
            requiredRuleKeys.isNotEmpty()
        )
        assertTrue(
            failureMessage(scenario, "reload", "strict required field rule source for ${endpointScenario.id}"),
            requiredRuleKeys.all { key ->
                installedReader.sourcesFor(key).any { source -> source.sourceId == "extension" }
            }
        )
        return session.reacquireServices()
    }

    private fun assertRequestRequiredFlags(
        scenario: ResolvedBuiltInExtensionScenario,
        endpointScenario: StrictEndpointScenario,
        endpoint: com.itangcent.easyapi.core.export.ApiEndpoint
    ) {
        val requestBody = requireNotNull(endpoint.httpMetadata?.body as? ObjectModel.Object) {
            failureMessage(scenario, "assert", "object request body for ${endpointScenario.id}")
        }

        assertEquals(
            failureMessage(scenario, "assert", "request fields for ${endpointScenario.id}"),
            endpointScenario.expectedRequiredFlags.keys,
            requestBody.fields.keys
        )
        endpointScenario.expectedRequiredFlags.forEach { (fieldName, required) ->
            assertEquals(
                failureMessage(scenario, "assert", "required=$required for $fieldName in ${endpointScenario.id}"),
                required,
                requestBody.fields.getValue(fieldName).required
            )
        }
    }

    private fun strictFixturePlan(
        fixture: StrictValidationFixture,
        endpointScenario: StrictEndpointScenario
    ): ExtensionFixturePlan {
        val sources = linkedMapOf<String, String>()
        springMvcFixtureResources.forEach { (fqn, resource) ->
            sources[fqn] = resourceText(resource)
        }
        sources["org.springframework.validation.annotation.Validated"] = validatedSource
        sources["${fixture.validationPackage}.groups.Default"] = defaultGroupSource(fixture.validationPackage)
        listOf("NotBlank", "NotNull", "NotEmpty").forEach { constraint ->
            sources["${fixture.validationPackage}.constraints.$constraint"] =
                constraintSource(fixture.validationPackage, constraint)
        }
        sources["${fixture.fixturePackage}.CreateGroup"] = groupSource(fixture, "CreateGroup")
        sources["${fixture.fixturePackage}.OtherGroup"] = groupSource(fixture, "OtherGroup")
        sources[fixture.requestFqn] = requestSource(fixture)
        sources[fixture.controllerFqn] = controllerSource(fixture, endpointScenario)
        return ExtensionFixturePlan(psiStubs = sources)
    }

    private fun resourceText(resource: String): String {
        return requireNotNull(javaClass.classLoader.getResourceAsStream(resource)) {
            "Missing test fixture resource: $resource"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun defaultGroupSource(validationPackage: String): String = """
        package $validationPackage.groups;

        public interface Default {
        }
    """.trimIndent()

    private fun constraintSource(validationPackage: String, name: String): String = """
        package $validationPackage.constraints;

        public @interface $name {
            Class<?>[] groups() default {};
        }
    """.trimIndent()

    private fun groupSource(fixture: StrictValidationFixture, name: String): String = """
        package ${fixture.fixturePackage};

        public interface $name {
        }
    """.trimIndent()

    private fun requestSource(fixture: StrictValidationFixture): String = """
        package ${fixture.fixturePackage};

        import ${fixture.fixturePackage}.CreateGroup;
        import ${fixture.fixturePackage}.OtherGroup;
        import ${fixture.validationPackage}.constraints.NotBlank;
        import ${fixture.validationPackage}.constraints.NotEmpty;
        import ${fixture.validationPackage}.constraints.NotNull;

        public class StrictRequest {
            @NotBlank
            private String defaultValue;

            @NotNull(groups = CreateGroup.class)
            private String createValue;

            @NotEmpty(groups = OtherGroup.class)
            private String otherValue;

            private String optionalValue;

            public String getDefaultValue() { return defaultValue; }
            public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
            public String getCreateValue() { return createValue; }
            public void setCreateValue(String createValue) { this.createValue = createValue; }
            public String getOtherValue() { return otherValue; }
            public void setOtherValue(String otherValue) { this.otherValue = otherValue; }
            public String getOptionalValue() { return optionalValue; }
            public void setOptionalValue(String optionalValue) { this.optionalValue = optionalValue; }
        }
    """.trimIndent()

    private fun controllerSource(
        fixture: StrictValidationFixture,
        endpointScenario: StrictEndpointScenario
    ): String {
        val validatedAnnotation = endpointScenario.validatedAnnotation?.plus(" ").orEmpty()
        return """
            package ${fixture.fixturePackage};

            import ${fixture.fixturePackage}.CreateGroup;
            import ${fixture.fixturePackage}.OtherGroup;
            import ${fixture.fixturePackage}.StrictRequest;
            import org.springframework.validation.annotation.Validated;
            import org.springframework.web.bind.annotation.PostMapping;
            import org.springframework.web.bind.annotation.RequestBody;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;

            @RestController
            @RequestMapping("${fixture.endpointPrefix}")
            public class StrictValidationController {
                @PostMapping("${endpointScenario.path}")
                public StrictRequest ${endpointScenario.methodName}(${validatedAnnotation}@RequestBody StrictRequest request) {
                    return request;
                }
            }
        """.trimIndent()
    }

    private fun failureMessage(
        scenario: ResolvedBuiltInExtensionScenario,
        phase: String,
        observable: String
    ): String {
        val defaultState = if (scenario.extension.defaultEnabled) "enabled" else "disabled"
        return "extension=${scenario.extension.code}; default=$defaultState; phase=$phase; " +
                "condition=${scenario.conditionInput}; observable=$observable"
    }

    private data class StrictValidationFixture(
        val extensionCode: String,
        val validationPackage: String
    ) {
        private val namespace: String
            get() = validationPackage.substringBefore('.')

        val fixturePackage: String
            get() = "com.itangcent.validation.strict.$namespace"

        val requestFqn: String
            get() = "$fixturePackage.StrictRequest"

        val controllerFqn: String
            get() = "$fixturePackage.StrictValidationController"

        val endpointPrefix: String
            get() = "/strict/$namespace"

        fun pathFor(endpointScenario: StrictEndpointScenario): String = "$endpointPrefix${endpointScenario.path}"
    }

    private data class StrictEndpointScenario(
        val id: String,
        val path: String,
        val methodName: String,
        val validatedAnnotation: String?,
        val expectedRequiredFlags: Map<String, Boolean>
    )

    private companion object {
        const val SESSION_GROUP_KEY = "json-group"

        val springMvcFixtureResources = linkedMapOf(
            "org.springframework.web.bind.annotation.RequestMapping" to "spring/RequestMapping.java",
            "org.springframework.web.bind.annotation.PostMapping" to "spring/PostMapping.java",
            "org.springframework.web.bind.annotation.RequestBody" to "spring/RequestBody.java",
            "org.springframework.web.bind.annotation.RestController" to "spring/RestController.java"
        )

        val strictEndpointScenarios = listOf(
            StrictEndpointScenario(
                id = "default",
                path = "/default",
                methodName = "defaultGroup",
                validatedAnnotation = "@Validated",
                expectedRequiredFlags = requiredFlags(defaultValue = true)
            ),
            StrictEndpointScenario(
                id = "create",
                path = "/create",
                methodName = "createGroup",
                validatedAnnotation = "@Validated(CreateGroup.class)",
                expectedRequiredFlags = requiredFlags(createValue = true)
            ),
            StrictEndpointScenario(
                id = "other",
                path = "/other",
                methodName = "otherGroup",
                validatedAnnotation = "@Validated(OtherGroup.class)",
                expectedRequiredFlags = requiredFlags(otherValue = true)
            )
        )

        val sentinelEndpointScenario = StrictEndpointScenario(
            id = "unvalidated",
            path = "/unvalidated",
            methodName = "unvalidated",
            validatedAnnotation = null,
            expectedRequiredFlags = requiredFlags()
        )

        val validatedSource = """
            package org.springframework.validation.annotation;

            public @interface Validated {
                Class<?>[] value() default {};
            }
        """.trimIndent()

        private fun requiredFlags(
            defaultValue: Boolean = false,
            createValue: Boolean = false,
            otherValue: Boolean = false
        ): Map<String, Boolean> = linkedMapOf(
            "defaultValue" to defaultValue,
            "createValue" to createValue,
            "otherValue" to otherValue,
            "optionalValue" to false
        )
    }
}

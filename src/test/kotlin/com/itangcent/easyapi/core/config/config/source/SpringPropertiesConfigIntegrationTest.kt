package com.itangcent.easyapi.core.config.source

import com.itangcent.easyapi.core.config.parser.ConfigTextParser
import com.itangcent.easyapi.core.config.resource.ConfigResourceLoader
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.extension.BuiltInExtensionExecutionHarness
import com.itangcent.easyapi.core.extension.BuiltInExtensionScenarioLedger
import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.core.extension.ExtensionFixturePlan
import com.itangcent.easyapi.core.extension.ResolvedBuiltInExtensionScenario
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import java.nio.file.Files
import java.nio.file.Path

class SpringPropertiesConfigIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

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

    fun testPropertiesContextPathIsAppliedThroughModulePathInclude() = runTest {
        assertContextPathIsApplied(
            resourceName = "application.properties",
            contextPath = "/from-properties"
        )
    }

    fun testYamlContextPathIsAppliedThroughModulePathInclude() = runTest {
        assertContextPathIsApplied(
            resourceName = "application.yml",
            contextPath = "/from-yaml"
        )
    }

    fun testUnreadableModulePathIncludeFailsWithDiagnosticsAndLeavesNextScenarioClean() = runTest {
        val rawInclude = "$MODULE_PATH_VARIABLE/src/main/resources/missing-module-path-resource.properties"
        val projectBase = Path.of(requireNotNull(project.basePath)).toAbsolutePath().normalize()
        val expectedMissingResource = projectBase
            .resolve("src/main/resources/missing-module-path-resource.properties")
            .normalize()
        assertFalse(
            "The unreadable include case requires the target resource to be absent",
            Files.exists(expectedMissingResource)
        )

        val failure = try {
            harness.execute(scenario()) {
                ConfigTextParser.getInstance(project)
                    .parse(
                        "###set ignoreNotFoundFile=false\n###include $rawInclude",
                        EXTENSION_SOURCE_ID
                    )
                    .toList()
            }
            throw AssertionError("Expected an unreadable module-path include to fail")
        } catch (error: IllegalStateException) {
            error
        }

        assertStableUnreadableIncludeContext(failure, rawInclude, expectedMissingResource, projectBase)
        harness.assertNoPriorScenarioState()

        assertContextPathIsApplied(
            resourceName = "application.properties",
            contextPath = "/after-unreadable-include"
        )
        harness.assertNoPriorScenarioState()
    }

    private suspend fun assertContextPathIsApplied(resourceName: String, contextPath: String) {
        val scenario = scenario()
        harness.execute(scenario, controllerFixturePlan()) { session ->
            val resources = TemporaryModuleResources.create(
                moduleRoot = session.moduleRoot,
                resourceName = resourceName,
                contextPath = contextPath
            )
            try {
                assertTrue(
                    "Expected a real Spring resource under the fixture module root: ${resources.resourcePath}",
                    Files.isRegularFile(resources.resourcePath)
                )

                val includeAudit = auditRealResourceInclude(
                    scenario = scenario,
                    resourceName = resourceName,
                    moduleRoot = session.moduleRoot,
                    resources = resources
                )
                val reader = session.installIsolatedReader()
                val contextPathSource = reader.sourcesFor(CONTEXT_PATH_PROPERTY)
                    .singleOrNull { it.value == contextPath }
                val actualContextPath = reader.reader.getFirst(CONTEXT_PATH_PROPERTY)
                val actualPrefix = reader.reader.getFirst(RuleKeys.CLASS_PREFIX_PATH.name)
                val prefixSourceId = reader.sourcesFor(RuleKeys.CLASS_PREFIX_PATH.name)
                    .singleOrNull { it.value == contextPath }
                    ?.sourceId
                    ?: "<missing>"
                val controller = requireNotNull(findClass(CONTROLLER_FQN)) {
                    "Expected Spring MVC controller fixture to resolve: $CONTROLLER_FQN"
                }
                val endpointPath = session.reacquireServices()
                    .springMvcExporter
                    .export(controller)
                    .singleOrNull()
                    ?.httpMetadata
                    ?.path
                val expectedEndpointPath = "$contextPath$MAPPING_PATH"
                val diagnostic = IncludeDiagnostic(
                    rawInclude = includeAudit.rawInclude,
                    expandedPath = includeAudit.expandedPath,
                    baseDir = includeAudit.baseDir,
                    moduleBase = session.moduleRoot,
                    projectBase = Path.of(requireNotNull(project.basePath)).toAbsolutePath().normalize(),
                    sourceId = prefixSourceId
                )

                assertEquals(
                    diagnostic.message(
                        phase = "assert",
                        condition = "module-path resource present",
                        observable = "included context-path property",
                        expected = "$CONTEXT_PATH_PROPERTY=$contextPath from $EXTENSION_SOURCE_ID",
                        actual = "$CONTEXT_PATH_PROPERTY=${actualContextPath ?: "<missing>"}; " +
                            "sourceId=${contextPathSource?.sourceId ?: "<missing>"}"
                    ),
                    contextPath,
                    actualContextPath
                )
                assertEquals(
                    diagnostic.message(
                        phase = "assert",
                        condition = "module-path resource present",
                        observable = "included resource source",
                        expected = EXTENSION_SOURCE_ID,
                        actual = contextPathSource?.sourceId ?: "<missing>"
                    ),
                    EXTENSION_SOURCE_ID,
                    contextPathSource?.sourceId
                )
                assertEquals(
                    diagnostic.message(
                        phase = "assert",
                        condition = "module-path resource present",
                        observable = "class.prefix.path",
                        expected = contextPath,
                        actual = actualPrefix ?: "<missing>"
                    ),
                    contextPath,
                    actualPrefix
                )
                assertEquals(
                    diagnostic.message(
                        phase = "assert",
                        condition = "module-path resource present",
                        observable = "class.prefix.path source",
                        expected = EXTENSION_SOURCE_ID,
                        actual = prefixSourceId
                    ),
                    EXTENSION_SOURCE_ID,
                    prefixSourceId
                )
                assertEquals(
                    diagnostic.message(
                        phase = "export",
                        condition = "module-path resource present",
                        observable = "endpoint path",
                        expected = expectedEndpointPath,
                        actual = endpointPath ?: "<missing>"
                    ),
                    expectedEndpointPath,
                    endpointPath
                )
                assertEquals(
                    diagnostic.message(
                        phase = "export",
                        condition = "module-path resource present",
                        observable = "class.prefix.path occurrence count",
                        expected = "1",
                        actual = (endpointPath?.countOccurrences(contextPath) ?: 0).toString()
                    ),
                    1,
                    endpointPath?.countOccurrences(contextPath) ?: 0
                )
                assertTrue(
                    diagnostic.message(
                        phase = "export",
                        condition = "module-path resource present",
                        observable = "retained mapping path",
                        expected = MAPPING_PATH,
                        actual = endpointPath ?: "<missing>"
                    ),
                    endpointPath?.endsWith(MAPPING_PATH) == true
                )
            } finally {
                resources.close()
            }
        }
    }

    private suspend fun auditRealResourceInclude(
        scenario: ResolvedBuiltInExtensionScenario,
        resourceName: String,
        moduleRoot: Path,
        resources: TemporaryModuleResources
    ): IncludeAudit {
        val rawInclude = includePath(scenario, resourceName)
        assertTrue(
            "Spring-properties include must use the module-path variable: $rawInclude",
            rawInclude.contains(MODULE_PATH_VARIABLE)
        )
        val expandedPath = Path.of(rawInclude.replace(MODULE_PATH_VARIABLE, moduleRoot.toString()))
            .toAbsolutePath()
            .normalize()
        assertEquals(
            "The module-path include must resolve to the physical fixture resource",
            resources.resourcePath,
            expandedPath
        )

        val loadedResource = requireNotNull(
            ConfigResourceLoader.getInstance(project).load(expandedPath.toString(), null)
        ) {
            "Expected the real resource loader to read included resource: $expandedPath"
        }
        assertEquals(
            "The real resource loader must preserve the included resource content",
            resources.content,
            loadedResource.content
        )
        assertEquals(
            "The real resource loader must report the included resource directory",
            resources.resourcePath.parent.toString(),
            loadedResource.baseDir
        )
        return IncludeAudit(rawInclude, expandedPath, loadedResource.baseDir)
    }

    private fun assertStableUnreadableIncludeContext(
        error: IllegalStateException,
        rawInclude: String,
        expectedMissingResource: Path,
        projectBase: Path
    ) {
        val expectedMessage = "Cannot resolve include: $rawInclude; " +
            "rawInclude=$rawInclude; " +
            "expandedPath=$expectedMissingResource; " +
            "baseDir=<none>; " +
            "moduleBase=<none>; " +
            "projectBase=$projectBase; " +
            "sourceId=$EXTENSION_SOURCE_ID"
        assertEquals(
            "Unreadable module-path includes must retain stable diagnostic context",
            expectedMessage,
            error.message
        )
    }

    private fun scenario(): ResolvedBuiltInExtensionScenario {
        return requireNotNull(BuiltInExtensionScenarioLedger.resolvedScenarios().singleOrNull {
            it.extension.code == "spring-properties"
        }) {
            "Missing scenario for spring-properties"
        }
    }

    private fun includePath(scenario: ResolvedBuiltInExtensionScenario, resourceName: String): String {
        val prefix = "properties.additional="
        val suffix = "/src/main/resources/$resourceName"
        val includeLine = requireNotNull(
            scenario.extension.content.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith(prefix) && it.endsWith(suffix) }
        ) {
            "Missing spring-properties include for $resourceName"
        }
        return includeLine.removePrefix(prefix)
    }

    private fun controllerFixturePlan(): ExtensionFixturePlan {
        return ExtensionFixturePlan(
            psiStubs = linkedMapOf(
                REST_CONTROLLER_FQN to REST_CONTROLLER_STUB,
                REQUEST_MAPPING_FQN to REQUEST_MAPPING_STUB,
                CONTROLLER_FQN to CONTROLLER_STUB
            )
        )
    }

    private fun String.countOccurrences(value: String): Int {
        if (value.isEmpty()) return 0
        var count = 0
        var startIndex = 0
        while (true) {
            val occurrenceIndex = indexOf(value, startIndex)
            if (occurrenceIndex < 0) return count
            count++
            startIndex = occurrenceIndex + value.length
        }
    }

    private data class IncludeAudit(
        val rawInclude: String,
        val expandedPath: Path,
        val baseDir: String?
    )

    private data class IncludeDiagnostic(
        val rawInclude: String,
        val expandedPath: Path,
        val baseDir: String?,
        val moduleBase: Path,
        val projectBase: Path,
        val sourceId: String
    ) {
        fun message(
            phase: String,
            condition: String,
            observable: String,
            expected: String,
            actual: String
        ): String {
            return "extension=spring-properties; phase=$phase; condition=$condition; " +
                "observable=$observable; rawInclude=$rawInclude; expandedPath=$expandedPath; " +
                "baseDir=${baseDir ?: "<none>"}; moduleBase=$moduleBase; projectBase=$projectBase; " +
                "sourceId=$sourceId; expected=$expected; actual=$actual"
        }
    }

    private class TemporaryModuleResources private constructor(
        val resourcePath: Path,
        val content: String,
        private val createdDirectories: List<Path>
    ) : AutoCloseable {

        override fun close() {
            Files.deleteIfExists(resourcePath)
            createdDirectories.sortedByDescending { it.nameCount }.forEach { directory ->
                if (Files.isDirectory(directory)) {
                    Files.list(directory).use { entries ->
                        if (!entries.findAny().isPresent) {
                            Files.deleteIfExists(directory)
                        }
                    }
                }
            }
        }

        companion object {
            fun create(moduleRoot: Path, resourceName: String, contextPath: String): TemporaryModuleResources {
                val normalizedModuleRoot = moduleRoot.toAbsolutePath().normalize()
                val resourcePath = normalizedModuleRoot
                    .resolve("src/main/resources/$resourceName")
                    .normalize()
                require(resourcePath.startsWith(normalizedModuleRoot)) {
                    "Spring resource must remain inside the module root: $resourceName"
                }
                require(!Files.exists(resourcePath)) {
                    "Spring resource already exists in the fixture module root: $resourcePath"
                }

                val parent = requireNotNull(resourcePath.parent)
                val createdDirectories = missingDirectories(parent)
                val content = propertyContent(resourceName, contextPath)
                Files.createDirectories(parent)
                Files.writeString(resourcePath, content)
                return TemporaryModuleResources(resourcePath, content, createdDirectories)
            }

            private fun missingDirectories(path: Path): List<Path> {
                val missing = ArrayList<Path>()
                var current: Path? = path
                while (current != null && !Files.exists(current)) {
                    missing.add(current)
                    current = current.parent
                }
                return missing
            }

            private fun propertyContent(resourceName: String, contextPath: String): String {
                return when (resourceName) {
                    "application.properties" -> "$CONTEXT_PATH_PROPERTY=$contextPath\n"
                    "application.yml" -> "$CONTEXT_PATH_PROPERTY: $contextPath\n"
                    else -> error("Unsupported Spring resource: $resourceName")
                }
            }
        }
    }

    private companion object {
        const val EXTENSION_SOURCE_ID = "extension"
        const val MODULE_PATH_VARIABLE = "\${module_path}"
        const val CONTEXT_PATH_PROPERTY = "server.servlet.context-path"
        const val REST_CONTROLLER_FQN = "org.springframework.web.bind.annotation.RestController"
        const val REQUEST_MAPPING_FQN = "org.springframework.web.bind.annotation.RequestMapping"
        const val CONTROLLER_FQN = "com.itangcent.springproperties.SpringPropertiesController"
        const val CONTROLLER_PATH = "/spring-properties"
        const val METHOD_PATH = "/status"
        const val MAPPING_PATH = "/spring-properties/status"

        val REST_CONTROLLER_STUB = """
            package org.springframework.web.bind.annotation;
            public @interface RestController {}
        """.trimIndent()

        val REQUEST_MAPPING_STUB = """
            package org.springframework.web.bind.annotation;
            public @interface RequestMapping {
                String[] value() default {};
                String[] path() default {};
            }
        """.trimIndent()

        val CONTROLLER_STUB = """
            package com.itangcent.springproperties;
            import org.springframework.web.bind.annotation.RequestMapping;
            import org.springframework.web.bind.annotation.RestController;
            @RestController
            @RequestMapping("/spring-properties")
            public class SpringPropertiesController {
                @RequestMapping("/status")
                public String status() {
                    return "ok";
                }
            }
        """.trimIndent()
    }
}

package com.itangcent.easyapi.core.extension

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.BranchChangeListener
import com.intellij.psi.PsiFile
import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.ConfigReloadListener
import com.itangcent.easyapi.core.config.LayeredConfigReader
import com.itangcent.easyapi.core.config.SourceValue
import com.itangcent.easyapi.core.config.model.ConfigSource
import com.itangcent.easyapi.core.config.parser.ConfigTextParser
import com.itangcent.easyapi.core.config.source.ExtensionConfigSource
import com.itangcent.easyapi.core.internal.event.ActionCompletedTopic
import com.itangcent.easyapi.core.internal.event.ActionCompletedTopic.Companion.syncPublish
import com.itangcent.easyapi.core.psi.PsiClassHelper
import com.itangcent.easyapi.core.rule.RuleProvider
import com.itangcent.easyapi.core.rule.engine.RuleEngine
import com.itangcent.easyapi.core.util.ide.ProjectClassAvailabilityService
import com.itangcent.easyapi.core.util.storage.SessionStorage
import com.itangcent.easyapi.framework.springmvc.SpringMvcClassExporter
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

internal data class ExtensionFixturePlan(
    val psiStubs: Map<String, String> = emptyMap(),
    val physicalResources: Map<String, String> = emptyMap(),
    val awaitedClasses: Set<String> = psiStubs.keys
)

internal data class FqnResolutionProbe(
    val fqn: String,
    val resolvable: Boolean
)

internal data class ExtensionExecutionServices(
    val ruleProvider: RuleProvider,
    val ruleEngine: RuleEngine,
    val psiClassHelper: PsiClassHelper,
    val springMvcExporter: SpringMvcClassExporter
)

internal data class InstalledExtensionReader(
    val reader: LayeredConfigReader,
    val extensionRuleKeys: Set<String>
) {
    fun sourcesFor(key: String): List<SourceValue> = reader.sourcesForKey(key)
}

internal class BuiltInExtensionExecutionHarness(
    private val project: Project,
    private val loadPsiFile: (String, String) -> PsiFile,
    private val waitForClass: suspend (String) -> Unit
) {
    private val baselineReader = ConfigReader.getInstance(project)
    private val classAvailability = ProjectClassAvailabilityService.getInstance(project)
    private val sessionStorage = SessionStorage.getInstance(project)

    private var previousScenario: CompletedScenario? = null

    var lastReleasedResourceRoot: Path? = null
        private set

    suspend fun <T> execute(
        scenario: ResolvedBuiltInExtensionScenario,
        fixturePlan: ExtensionFixturePlan = ExtensionFixturePlan(),
        block: suspend (ExtensionScenarioSession) -> T
    ): T {
        val priorScenarioStateWasClean = assertPreviousScenarioStateIsClean()
        val fixture = prepareFixture(fixturePlan)
        val session = ExtensionScenarioSession(
            scenario = scenario,
            fixture = fixture,
            priorScenarioStateWasClean = priorScenarioStateWasClean
        )

        try {
            return block(session)
        } finally {
            cleanupScenario(session)
        }
    }

    fun assertNoPriorScenarioState() {
        assertPreviousScenarioStateIsClean()
    }

    private suspend fun prepareFixture(plan: ExtensionFixturePlan): PreparedFixture {
        val moduleRoot = resolveModuleRoot()
        val resourceRoot = Files.createTempDirectory(moduleRoot, "easyapi-extension-fixture-")
        val psiFiles = ArrayList<PsiFile>()

        try {
            plan.physicalResources.forEach { (relativePath, content) ->
                val target = resourceRoot.resolve(relativePath).normalize()
                require(target.startsWith(resourceRoot)) {
                    "Fixture resource must stay inside its module root: $relativePath"
                }
                Files.createDirectories(target.parent)
                Files.writeString(target, content)
            }

            plan.psiStubs.forEach { (fqn, content) ->
                psiFiles += loadPsiFile("${fqn.replace('.', '/')}.java", content)
            }
            plan.awaitedClasses.forEach { waitForClass(it) }
            invalidateClassAvailability("fixture-ready")

            return PreparedFixture(moduleRoot, resourceRoot, psiFiles)
        } catch (error: Throwable) {
            PreparedFixture(moduleRoot, resourceRoot, psiFiles).release()
            throw error
        }
    }

    private fun resolveModuleRoot(): Path {
        val basePath = requireNotNull(project.basePath) {
            "A physical project base path is required for extension fixture resources"
        }
        return Path.of(basePath).toAbsolutePath().normalize().also { root ->
            Files.createDirectories(root)
        }
    }

    private suspend fun cleanupScenario(session: ExtensionScenarioSession) {
        session.fixture.release()
        lastReleasedResourceRoot = session.fixture.resourceRoot

        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = baselineReader
        )
        baselineReader.reload()
        publishReloadAndClasspathInvalidation("scenario-cleanup")
        sessionStorage.clear()
        project.syncPublish(ActionCompletedTopic.TOPIC)

        previousScenario = CompletedScenario(
            extensionCode = session.scenario.extension.code,
            ruleKeys = session.installedReader?.extensionRuleKeys.orEmpty(),
            resourceRoot = session.fixture.resourceRoot
        )
    }

    private fun assertPreviousScenarioStateIsClean(): Boolean {
        val previous = previousScenario ?: return true
        val reader = ConfigReader.getInstance(project)
        val leakingRuleKeys = previous.ruleKeys.filter { key ->
            reader.sourcesForKey(key).any { it.sourceId == EXTENSION_SOURCE_ID }
        }

        check(leakingRuleKeys.isEmpty()) {
            "extension=${previous.extensionCode}; phase=precondition; " +
                    "observable=previous extension source; leakingKeys=$leakingRuleKeys"
        }
        check(sessionStorage.get(SESSION_MARKER) == null) {
            "extension=${previous.extensionCode}; phase=precondition; " +
                    "observable=session marker; marker=${sessionStorage.get(SESSION_MARKER)}"
        }
        check(!Files.exists(previous.resourceRoot)) {
            "extension=${previous.extensionCode}; phase=precondition; " +
                    "observable=temporary resource root; root=${previous.resourceRoot}"
        }
        return true
    }

    private fun publishReloadAndClasspathInvalidation(phase: String) {
        project.messageBus.syncPublisher(ConfigReloadListener.TOPIC).onConfigReloaded()
        invalidateClassAvailability(phase)
    }

    private fun invalidateClassAvailability(phase: String) {
        classAvailability.clearCache()
        project.messageBus.syncPublisher(BranchChangeListener.VCS_BRANCH_CHANGED)
            .branchHasChanged("extension-$phase")
    }

    internal inner class PreparedFixture(
        val moduleRoot: Path,
        val resourceRoot: Path,
        private val psiFiles: List<PsiFile>
    ) {
        fun release() {
            ApplicationManager.getApplication().runWriteAction {
                psiFiles.asReversed().forEach { psiFile ->
                    val virtualFile = psiFile.virtualFile
                    if (virtualFile.isValid) {
                        virtualFile.delete(this@BuiltInExtensionExecutionHarness)
                    }
                }
            }
            if (Files.exists(resourceRoot)) {
                Files.walk(resourceRoot).use { paths ->
                    paths.sorted(Comparator.reverseOrder()).forEach { path ->
                        Files.deleteIfExists(path)
                    }
                }
            }
            invalidateClassAvailability("fixture-released")
        }
    }

    internal inner class ExtensionScenarioSession internal constructor(
        val scenario: ResolvedBuiltInExtensionScenario,
        internal val fixture: PreparedFixture,
        val priorScenarioStateWasClean: Boolean
    ) {
        var installedReader: InstalledExtensionReader? = null
            private set

        val moduleRoot: Path
            get() = fixture.moduleRoot

        val resourceRoot: Path
            get() = fixture.resourceRoot

        suspend fun probeFqn(fqn: String): FqnResolutionProbe {
            return FqnResolutionProbe(fqn, classAvailability.hasClassInProject(fqn))
        }

        suspend fun installIsolatedReader(
            baseSources: List<ConfigSource> = emptyList()
        ): InstalledExtensionReader {
            val parser = ConfigTextParser.getInstance(project)
            val source = ExtensionConfigSource(
                project = project,
                selectedCodes = BuiltInExtensionScenarioLedger.isolatedSelection(scenario),
                configTextParser = parser
            )
            val reader = LayeredConfigReader(baseSources + source)

            project.registerServiceInstance(
                serviceInterface = ConfigReader::class.java,
                instance = reader
            )
            reader.reload()
            publishReloadAndClasspathInvalidation("reader-installed")

            val extensionRuleKeys = linkedSetOf<String>()
            reader.foreach { key, _ ->
                if (reader.sourcesForKey(key).any { it.sourceId == source.sourceId }) {
                    extensionRuleKeys += key
                }
            }
            return InstalledExtensionReader(reader, extensionRuleKeys).also { installedReader = it }
        }

        fun reacquireServices(): ExtensionExecutionServices {
            return ExtensionExecutionServices(
                ruleProvider = RuleProvider.getInstance(project),
                ruleEngine = RuleEngine.getInstance(project),
                psiClassHelper = PsiClassHelper.getInstance(project),
                springMvcExporter = SpringMvcClassExporter(project)
            )
        }

        fun setSessionMarker() {
            sessionStorage.set(SESSION_MARKER, scenario.extension.code)
        }
    }

    private data class CompletedScenario(
        val extensionCode: String,
        val ruleKeys: Set<String>,
        val resourceRoot: Path
    )

    private companion object {
        const val EXTENSION_SOURCE_ID = "extension"
        const val SESSION_MARKER = "built-in-extension.execution-marker"
    }
}

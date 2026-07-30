package com.itangcent.easyapi.channel.markdown

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.ui.components.JBTextField
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.internal.threading.IdeDispatchers
import com.itangcent.easyapi.core.internal.threading.background
import com.itangcent.easyapi.core.internal.threading.swing
import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.channel.spi.ChannelOptionsPanel
import com.itangcent.easyapi.channel.markdown.MarkdownExportMetadata
import com.itangcent.easyapi.channel.curl.CurlSettings
import com.itangcent.easyapi.channel.markdown.template.MarkdownTemplateRenderer
import com.itangcent.easyapi.channel.markdown.template.MarkdownTemplateResolver
import com.itangcent.easyapi.channel.markdown.template.RemoteTemplateFetcher
import com.itangcent.easyapi.channel.markdown.template.RenderContext
import com.itangcent.easyapi.channel.markdown.template.TemplateConfig
import com.itangcent.easyapi.channel.markdown.template.TemplateModelBuilder
import com.itangcent.easyapi.core.http.HttpClientProvider
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.ide.support.NotificationUtils
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.core.settings.ui.SettingsPanel
import kotlinx.coroutines.CancellationException
import kotlin.reflect.KClass
import java.awt.BorderLayout
import java.awt.CardLayout
import java.io.File
import javax.swing.*

/**
 * [Channel] that exports API endpoints as Markdown documentation.
 *
 * Supports both HTTP and gRPC endpoints. Exposes a top-level IDE action
 * for quick access.
 *
 * @see Channel
 * @see MarkdownTemplateResolver
 * @see MarkdownTemplateRenderer
 */
class MarkdownChannel : Channel, IdeaLog {

    override val id: String = "markdown"
    override val displayName: String = "Markdown"
    override val supportsGrpc: Boolean = true
    override val exposeAsAction: Boolean = true
    override val actionText: String = "Export to Markdown"

    override val settingsType: KClass<out Settings> = MarkdownSettings::class
    override val settingsTabOrder: Int = 120

    override fun createOptionsPanel(project: Project): ChannelOptionsPanel {
        return MarkdownOptionsPanel(project)
    }

    override fun createSettingsPanel(project: Project): SettingsPanel<*>? =
        MarkdownSettingsPanel(project)

    override suspend fun export(context: ExportContext): ExportResult {
        LOG.info("MarkdownChannel.export: endpoints=${context.endpointsToExport.size}")
        val project = context.project
        val markdownConfig = context.channelConfig as? MarkdownConfig
        val templateLanguage = markdownConfig?.templateLanguage
            ?: project.settings<MarkdownSettings>().templateLanguage.takeIf { it != "en" }
        val templateConfig = TemplateConfig(
            templateInline = markdownConfig?.templateInline,
            templatePath = markdownConfig?.templatePath,
            templateUrl = markdownConfig?.templateUrl,
            templateLanguage = templateLanguage,
        )
        val configReader = ConfigReader.getInstance(project)
        val httpClient = HttpClientProvider.getInstance(project).getClient(httpTimeOut = 10)

        // Tuning keys : bounded cache TTL + response size cap, read via ConfigReader.
        val ttlSeconds = configReader.getFirst("markdown.template.url.ttl.seconds")
            ?.toLongOrNull()
            ?: RemoteTemplateFetcher.DEFAULT_TTL_SECONDS
        val maxBytes = configReader.getFirst("markdown.template.url.max.bytes")
            ?.toLongOrNull()
            ?: RemoteTemplateFetcher.DEFAULT_MAX_BYTES

        val resolved = MarkdownTemplateResolver.resolve(
            config = templateConfig,
            configReader = configReader,
            projectBasePath = project.basePath,
            fileReader = { path ->
                try {
                    File(path).takeIf { it.exists() && it.isFile }?.readText()
                } catch (t: Throwable) {
                    LOG.warn("Failed to read template file: $path", t)
                    null
                }
            },
            urlFetcher = { url ->
                RemoteTemplateFetcher.fetch(
                    url = url,
                    httpClient = httpClient,
                    ttlSeconds = ttlSeconds,
                    maxBytes = maxBytes,
                    dispatcher = IdeDispatchers.Background,
                )
            },
        )

        // Translate per-tier resolution warnings to user-visible notifications .
        for (warning in resolved.warnings) {
            LOG.warn("Template resolution warning [${warning.tier}]: ${warning.message}", warning.throwable)
            NotificationUtils.notifyWarning(
                project = project,
                title = "Markdown Template Resolution",
                content = warning.message,
                t = warning.throwable,
            )
        }

        LOG.info("Markdown template resolved from tier: ${resolved.source}")

        // Host + format options for `{{{api.http.curl()}}}`:
        //  - `markdown.curl.host` config key (blank → `CurlBuilder.DEFAULT_HOST` placeholder).
        //  - Format flags from the cURL settings tab (`CurlSettings.toFormatOptions()`),
        //    so the user's cURL formatting preferences flow into Markdown-generated curls.
        //    Markdown render path pins `runPreScripts = false`.
        val curlHost = configReader.getFirst("markdown.curl.host").orEmpty()
        val curlFormatOptions = project.settings<CurlSettings>().toFormatOptions()
        val model = TemplateModelBuilder.build(
            context.endpointsToExport,
            "API Documentation",
            host = curlHost,
            formatOptions = curlFormatOptions,
        )
        val ctx = RenderContext.production(projectName = project.name ?: "", pluginVersion = "")
        val content = MarkdownTemplateRenderer.renderWithFallback(resolved.templateText, model, ctx)
        return ExportResult.Success(
            count = context.endpointsToExport.size,
            target = "Markdown",
            metadata = MarkdownExportMetadata(content = content)
        )
    }

    override suspend fun handleResult(
        project: Project,
        result: ExportResult.Success,
        config: ChannelConfig
    ): Boolean {
        val metadata = result.metadata as? MarkdownExportMetadata ?: return false

        val targetFile = resolveTargetFile(project, config, "api_documentation.md")
            ?: throw CancellationException("User cancelled file selection")

        background {
            targetFile.writeText(metadata.content)
        }
        LOG.info("Markdown exported to ${targetFile.absolutePath}")

        swing {
            com.intellij.openapi.ui.Messages.showInfoMessage(
                project,
                "Successfully exported ${result.count} endpoints to ${targetFile.absolutePath}",
                "Export API"
            )
        }
        return true
    }

    /**
     * Resolves the target output file from the channel config.
     *
     * Accepts either a [MarkdownConfig] (channel-specific) or a
     * [ChannelConfig.FileConfig] (SPI base) for the [outputDir][MarkdownConfig.outputDir]
     * / [fileName][MarkdownConfig.fileName] fields — so callers that don't want
     * to import `channel.markdown.*` can use the SPI base class.
     */
    private suspend fun resolveTargetFile(
        project: Project,
        config: ChannelConfig?,
        defaultFileName: String
    ): File? {
        val outputDir: String? = when (config) {
            is MarkdownConfig -> config.outputDir
            is ChannelConfig.FileConfig -> config.outputDir
            else -> null
        }
        val fileName: String? = when (config) {
            is MarkdownConfig -> config.fileName
            is ChannelConfig.FileConfig -> config.fileName
            else -> null
        }
        if (!outputDir.isNullOrBlank()) {
            val dir = File(outputDir)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val name = if (!fileName.isNullOrBlank()) "$fileName.md" else defaultFileName
            return File(dir, name)
        }
        return selectTargetFile(project, defaultFileName)
    }

    private suspend fun selectTargetFile(project: Project, defaultFileName: String): File? {
        return swing {
            val descriptor = FileSaverDescriptor(
                "Save Markdown Documentation",
                "Choose where to save the Markdown file"
            )
            val saver = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
            val wrapper: VirtualFileWrapper? = saver.save(null as VirtualFile?, defaultFileName)
            wrapper?.file
        }
    }
}

private class MarkdownOptionsPanel(private val project: Project) : ChannelOptionsPanel, IdeaLog {

    private val outputDirField = TextFieldWithBrowseButton().apply {
        text = project.basePath ?: ""
        addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
                .withTitle("Select Output Directory")
                .withDescription("Choose the directory to export API files to")
        )
    }

    private val fileNameField = JBTextField().apply {
        text = "api_export"
        columns = 30
    }

    override val component: JComponent = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(JPanel(BorderLayout()).apply {
            add(JLabel("Output Directory:"), BorderLayout.WEST)
            add(outputDirField, BorderLayout.CENTER)
        })
        add(Box.createVerticalStrut(5))
        add(JPanel(BorderLayout()).apply {
            add(JLabel("File Name (without extension):"), BorderLayout.WEST)
            add(fileNameField, BorderLayout.CENTER)
        })
    }

    override fun buildConfig(): MarkdownConfig {
        return MarkdownConfig(
            outputDir = outputDirField.text.takeIf { it.isNotBlank() },
            fileName = fileNameField.text.takeIf { it.isNotBlank() },
        )
    }
}

package com.itangcent.easyapi.channel.markdown

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.openapi.ui.ComboBox
import com.itangcent.easyapi.channel.markdown.template.BundledLanguageTemplates
import com.itangcent.easyapi.channel.markdown.template.DefaultMarkdownTemplate
import com.itangcent.easyapi.core.internal.threading.backgroundAsync
import com.itangcent.easyapi.core.internal.threading.swing
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.settings
import com.itangcent.easyapi.core.settings.update
import com.itangcent.easyapi.core.settings.ui.SettingsPanel
import java.awt.BorderLayout
import javax.swing.*

/**
 * Persistent settings panel for the Markdown channel.
 *
 * Hosts template-source fields that are too complex for the per-export
 * [MarkdownOptionsPanel]: local template file path, remote template URL,
 * copy-default-template button, and inline template editor.
 *
 * Typed as `SettingsPanel<Settings>` (not `SettingsPanel<MarkdownSettings>`)
 * so the configurable's `ChannelPanelEntry.panel` unchecked cast at
 * [com.itangcent.easyapi.core.settings.ui.EasyApiSettingsConfigurable]
 * works for every channel without per-channel `Settings` subtype awareness.
 * The panel still reads/writes its own [MarkdownSettings] module internally
 * via [SettingBinder].
 *
 * @param project the IntelliJ project context
 * @see MarkdownSettings for the persisted module
 * @see MarkdownOptionsPanel for the per-export counterpart
 */
class MarkdownSettingsPanel(private val project: Project) : SettingsPanel<Settings>, IdeaLog {

    private val templateFileField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            project,
            FileChooserDescriptor(true, false, false, false, false, false)
                .withTitle("Select Template File")
                .withDescription("Choose a Markdown template file (.tpl or .md.tpl)")
        )
    }

    private val templateUrlField = JBTextField().apply {
        columns = 40
        toolTipText = "<html>http(s) URL to a remote Markdown template. " +
            "Fetched over the network on each export (cached for 10 min).<br>" +
            "Only http/https are allowed; redirects are not followed.</html>"
    }

    private val copyDefaultButton = JButton("Copy default template").apply {
        toolTipText = "Save the bundled default template to a file and open it for editing"
        addActionListener {
            val descriptor = com.intellij.openapi.fileChooser.FileSaverDescriptor(
                "Save Default Template",
                "Choose where to save the bundled default Markdown template"
            )
            val saver = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
            val wrapper = saver.save(null as com.intellij.openapi.vfs.VirtualFile?, "default.md.tpl") ?: return@addActionListener
            val targetFile = wrapper.file
            backgroundAsync {
                try {
                    targetFile.writeText(DefaultMarkdownTemplate.get())
                    swing {
                        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile)
                        if (vFile != null) {
                            FileEditorManager.getInstance(project).openFile(vFile)
                        }
                        templateFileField.text = targetFile.absolutePath
                    }
                } catch (t: Throwable) {
                    LOG.warn("Failed to save default template to ${targetFile.absolutePath}", t)
                    swing {
                        com.intellij.openapi.ui.Messages.showErrorDialog(
                            project,
                            "Failed to save default template: ${t.message}",
                            "Save Error"
                        )
                    }
                }
            }
        }
    }

    private val inlineToggle = JToggleButton("Show inline template").apply {
        toolTipText = "Toggle the inline template editor (overrides file template when non-blank)"
    }

    private val inlineArea = JBTextArea().apply {
        rows = 12
        columns = 60
        lineWrap = true
        wrapStyleWord = true
    }

    private val inlineScroll = JBScrollPane(inlineArea).apply {
        isVisible = false
    }

    private val languageCombo = ComboBox(BundledLanguageTemplates.availableLocales().toTypedArray()).apply {
        toolTipText = "Select a bundled language template (en uses the default template)"
    }

    init {
        inlineToggle.addActionListener {
            inlineToggle.text = if (inlineToggle.isSelected) "Hide inline template" else "Show inline template"
            inlineScroll.isVisible = inlineToggle.isSelected
            component.revalidate()
            component.repaint()
        }
    }

    override val component: JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("Template File:", templateFileField)
        .addLabeledComponent("Template URL:", templateUrlField)
        .addLabeledComponent("Language:", languageCombo)
        .addComponent(JPanel(BorderLayout()).apply {
            add(inlineToggle, BorderLayout.WEST)
            add(copyDefaultButton, BorderLayout.EAST)
        })
        .addComponent(inlineScroll)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetFrom(settings: Settings?) {
        val s = project.settings<MarkdownSettings>()
        templateFileField.text = s.templateFile
        templateUrlField.text = s.templateUrl
        inlineArea.text = s.templateInlineContent
        inlineToggle.isSelected = s.templateInlineContent.isNotBlank()
        inlineToggle.text = if (inlineToggle.isSelected) "Hide inline template" else "Show inline template"
        inlineScroll.isVisible = inlineToggle.isSelected
        languageCombo.selectedItem = s.templateLanguage.ifBlank { "en" }
    }

    override fun applyTo(settings: Settings) {
        SettingBinder.getInstance(project).update(MarkdownSettings::class) {
            templateFile = templateFileField.text.trim()
            templateUrl = templateUrlField.text.trim()
            templateInlineContent = inlineArea.text
            templateLanguage = (languageCombo.selectedItem as? String) ?: "en"
        }
    }

    override fun isModified(settings: Settings?): Boolean {
        val s = project.settings<MarkdownSettings>()
        if (templateFileField.text.trim() != s.templateFile) return true
        if (templateUrlField.text.trim() != s.templateUrl) return true
        if (inlineArea.text != s.templateInlineContent) return true
        if ((languageCombo.selectedItem as? String) != s.templateLanguage.ifBlank { "en" }) return true
        return false
    }

    // --- Test-visible accessors (mirror the CurlSettingsPanel internal-accessor pattern) ---

    internal fun templateFilePath(): String = templateFileField.text.trim()

    internal fun setTemplateFile(path: String) {
        templateFileField.text = path
    }

    internal fun templateUrlText(): String = templateUrlField.text.trim()

    internal fun setTemplateUrl(url: String) {
        templateUrlField.text = url
    }

    internal fun inlineContent(): String = inlineArea.text

    internal fun setInlineContent(content: String) {
        inlineArea.text = content
        inlineToggle.isSelected = content.isNotBlank()
        inlineToggle.text = if (inlineToggle.isSelected) "Hide inline template" else "Show inline template"
        inlineScroll.isVisible = inlineToggle.isSelected
    }

    internal fun isInlineVisible(): Boolean = inlineScroll.isVisible

    internal fun templateLanguage(): String = (languageCombo.selectedItem as? String) ?: "en"

    internal fun setTemplateLanguage(locale: String) {
        languageCombo.selectedItem = locale
    }
}

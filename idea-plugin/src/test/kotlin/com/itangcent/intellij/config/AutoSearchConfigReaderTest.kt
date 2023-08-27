package com.itangcent.intellij.config

import com.google.inject.Inject
import com.intellij.openapi.module.Module
import com.itangcent.common.utils.forceMkdirParent
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.psi.ContextSwitchListener
import com.itangcent.mock.AdvancedContextTest
import com.itangcent.utils.Initializable
import com.itangcent.common.utils.ResourceUtils
import org.mockito.Mockito
import java.io.File
import kotlin.reflect.KClass

/**
 * Test case of [AutoSearchConfigReader]
 */
internal abstract class AutoSearchConfigReaderTest : AdvancedContextTest() {

    @Inject
    protected lateinit var configReader: ConfigReader

    protected abstract val configReaderClass: KClass<out ConfigReader>

    override fun afterBind(actionContext: ActionContext) {
        super.afterBind(actionContext)
        //load configs from resource to tempDir as files in module
        for (file in loadConfigs()) {
            File("$tempDir/$file")
                    .also { it.forceMkdirParent() }
                    .also { it.createNewFile() }
                    .writeBytes(ResourceUtils.findResource(file)!!.readBytes())
        }
        (configReader as? Initializable)?.init()
    }

    protected open fun loadConfigs(): Array<String> {
        return arrayOf(
                "config/.easy.api.config",
                "config/.easy.api.yml",
                "config/.easy.api.yaml",
                "config/a/.easy.api.config",
                "config/a/.easy.api.yml",
                "config/a/.easy.api.yaml",
        )
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(ConfigReader::class) { it.with(this.configReaderClass) }

        //mock mockContextSwitchListener
        val mockModule = Mockito.mock(Module::class.java)
        Mockito.`when`(mockModule.moduleFilePath).thenReturn("$tempDir/config/a/a.iml")
        val mockContextSwitchListener = Mockito.mock(ContextSwitchListener::class.java)
        Mockito.`when`(mockContextSwitchListener.getModule()).thenReturn(mockModule)
        builder.bind(ContextSwitchListener::class.java) { it.toInstance(mockContextSwitchListener) }
    }
}
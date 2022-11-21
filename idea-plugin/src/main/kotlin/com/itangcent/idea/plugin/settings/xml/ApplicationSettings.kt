package com.itangcent.idea.plugin.settings.xml

import com.itangcent.idea.plugin.settings.MarkdownFormatType
import com.itangcent.idea.plugin.settings.PostmanJson5FormatType
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.helper.RecommendConfigLoader
import com.itangcent.idea.utils.Charsets

interface ApplicationSettingsSupport {
    var methodDocEnable: Boolean
    var genericEnable: Boolean
    var feignEnable: Boolean
    var jaxrsEnable: Boolean
    var actuatorEnable: Boolean
    var pullNewestDataBefore: Boolean
    var postmanToken: String?
    var wrapCollection: Boolean
    var autoMergeScript: Boolean
    var postmanJson5FormatType: String
    var queryExpanded: Boolean
    var formExpanded: Boolean
    var readGetter: Boolean
    var readSetter: Boolean
    var inferEnable: Boolean
    var inferMaxDeep: Int
    var selectedOnly: Boolean

    //unit:s
    var httpTimeOut: Int
    var trustHosts: Array<String>

    //enable to use recommend config
    var useRecommendConfig: Boolean
    var recommendConfigs: String
    var logLevel: Int
    var logCharset: String
    var outputDemo: Boolean
    var outputCharset: String
    var markdownFormatType: String
    var builtInConfig: String?

    fun copyTo(newSetting: ApplicationSettingsSupport) {
        newSetting.postmanToken = this.postmanToken
        newSetting.wrapCollection = this.wrapCollection
        newSetting.autoMergeScript = this.autoMergeScript
        newSetting.postmanJson5FormatType = this.postmanJson5FormatType
        newSetting.pullNewestDataBefore = this.pullNewestDataBefore
        newSetting.methodDocEnable = this.methodDocEnable
        newSetting.genericEnable = this.genericEnable
        newSetting.feignEnable = this.feignEnable
        newSetting.jaxrsEnable = this.jaxrsEnable
        newSetting.actuatorEnable = this.actuatorEnable
        newSetting.queryExpanded = this.queryExpanded
        newSetting.formExpanded = this.formExpanded
        newSetting.readGetter = this.readGetter
        newSetting.readSetter = this.readSetter
        newSetting.inferEnable = this.inferEnable
        newSetting.inferMaxDeep = this.inferMaxDeep
        newSetting.selectedOnly = this.selectedOnly
        newSetting.httpTimeOut = this.httpTimeOut
        newSetting.useRecommendConfig = this.useRecommendConfig
        newSetting.recommendConfigs = this.recommendConfigs
        newSetting.logLevel = this.logLevel
        newSetting.logCharset = this.logCharset
        newSetting.outputDemo = this.outputDemo
        newSetting.outputCharset = this.outputCharset
        newSetting.markdownFormatType = this.markdownFormatType
        newSetting.builtInConfig = this.builtInConfig
        newSetting.trustHosts = this.trustHosts
    }
}

class ApplicationSettings : ApplicationSettingsSupport {

    override var methodDocEnable: Boolean = false

    override var genericEnable: Boolean = false

    override var feignEnable: Boolean = false

    override var jaxrsEnable: Boolean = true

    override var actuatorEnable: Boolean = false

    //postman

    override var pullNewestDataBefore: Boolean = false

    override var postmanToken: String? = null

    override var wrapCollection: Boolean = false

    override var autoMergeScript: Boolean = false

    override var postmanJson5FormatType: String = PostmanJson5FormatType.EXAMPLE_ONLY.name

    //region intelligent

    override var queryExpanded: Boolean = true

    override var formExpanded: Boolean = true

    override var readGetter: Boolean = false

    override var readSetter: Boolean = false

    override var inferEnable: Boolean = false

    override var inferMaxDeep: Int = Settings.DEFAULT_INFER_MAX_DEEP

    override var selectedOnly: Boolean = false

    //endregion

    //region http--------------------------

    //unit:s
    override var httpTimeOut: Int = 5

    override var trustHosts: Array<String> = Settings.DEFAULT_TRUST_HOSTS

    //endregion

    //enable to use recommend config
    override var useRecommendConfig: Boolean = true

    override var recommendConfigs: String = RecommendConfigLoader.defaultCodes()

    override var logLevel: Int = 50

    /**
     * Charset for out put file
     */
    override var logCharset: String = Charsets.UTF_8.displayName()

    // markdown

    override var outputDemo: Boolean = true

    override var outputCharset: String = Charsets.UTF_8.displayName()

    override var markdownFormatType: String = MarkdownFormatType.SIMPLE.name

    override var builtInConfig: String? = null

    fun copy(): ApplicationSettings {
        val applicationSettings = ApplicationSettings()
        this.copyTo(applicationSettings)
        return applicationSettings
    }

}
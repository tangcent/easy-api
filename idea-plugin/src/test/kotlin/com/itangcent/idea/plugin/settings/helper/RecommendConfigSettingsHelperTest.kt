package com.itangcent.idea.plugin.settings.helper

import com.google.inject.Inject
import com.itangcent.mock.toUnixString
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test case of [RecommendConfigSettingsHelper]
 */
internal class RecommendConfigSettingsHelperTest : SettingsHelperTest() {

    @Inject
    private lateinit var recommendConfigSettingsHelper: RecommendConfigSettingsHelper

    @Test
    fun testUseRecommendConfig() {
        settings.useRecommendConfig = false
        assertFalse(recommendConfigSettingsHelper.useRecommendConfig())
        settings.useRecommendConfig = true
        assertTrue(recommendConfigSettingsHelper.useRecommendConfig())
    }

    @Test
    fun testLoadRecommendConfig() {
        settings.recommendConfigs =
            "-Jackson_JsonIgnoreProperties,-converts,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),-support_mock_for_javax_validation"
        assertEquals(
            "#Get the module from the comment,group the apis\n" +
                    "module=#module\n" +
                    "#Ignore class/api\n" +
                    "ignore=#ignore\n" +
                    "#ignore fields from java.lang system classes\n" +
                    "field.ignore=groovy:!it.containingClass().name().startsWith(\"java.lang\")&&it.defineClass().name().startsWith(\"java.lang\")\n" +
                    "#Support for Jackson annotations\n" +
                    "field.name=@com.fasterxml.jackson.annotation.JsonProperty#value\n" +
                    "field.ignore=@com.fasterxml.jackson.annotation.JsonIgnore#value\n" +
                    "#Support for Gson annotations\n" +
                    "field.name=@com.google.gson.annotations.SerializedName#value\n" +
                    "field.ignore=!@com.google.gson.annotations.Expose#serialize\n" +
                    "#ignore transient field\n" +
                    "field.ignore=groovy:it.hasModifier(\"transient\")\n" +
                    "#Support spring.validations\n" +
                    "field.required=@org.springframework.lang.NonNull\n" +
                    "param.ignore=groovy:it.type().isExtend(\"org.springframework.validation.BindingResult\")\n" +
                    "#Support for jakarta.validation annotations\n" +
                    "param.required=@jakarta.validation.constraints.NotBlank\n" +
                    "field.required=@jakarta.validation.constraints.NotBlank\n" +
                    "param.required=@jakarta.validation.constraints.NotNull\n" +
                    "field.required=@jakarta.validation.constraints.NotNull\n" +
                    "param.required=@jakarta.validation.constraints.NotEmpty\n" +
                    "field.required=@jakarta.validation.constraints.NotEmpty\n" +
                    "#Support spring file\n" +
                    "type.is_file=groovy:it.isExtend(\"org.springframework.web.multipart.MultipartFile\")\n" +
                    "#ignore serialVersionUID\n" +
                    "constant.field.ignore=groovy:it.name()==\"serialVersionUID\"\n" +
                    "# @ConfigurationProperties\n" +
                    "properties.prefix=@org.springframework.boot.context.properties.ConfigurationProperties\n" +
                    "properties.prefix=@org.springframework.boot.context.properties.ConfigurationProperties#prefix\n" +
                    "#Support for Fastjson annotations\n" +
                    "field.name=@com.alibaba.fastjson.annotation.JSONField#value\n" +
                    "#Auto map enum to a type matched field in it\n" +
                    "enum.use.by.type=true\n" +
                    "json.rule.enum.convert=~#name()\n" +
                    "#ignore some common classes\n" +
                    "ignored.classes_or_packages=```\n" +
                    "    java.lang.Class,java.lang.ClassLoader,java.lang.Module,java.lang.module,java.lang.annotation,\n" +
                    "    java.lang.security,java.lang.invoke,java.lang.reflect,jdk.internal,java.util.jar,java.util.function,\n" +
                    "    java.util.stream,java.util.logging,java.util.regex,java.util.zip,java.util.concurrent.locks,\n" +
                    "    org.jooq\n" +
                    "```\n" +
                    "###set resolveProperty = false\n" +
                    "field.ignore=groovy:```\n" +
                    "    def prefixList = it.type().name().tokenize(/[<>,]/).collect{\n" +
                    "     it.tokenize('.').inject([]) { acc, val -> acc << (acc ? \"\${acc.last()}.\${val}\" : val) }\n" +
                    "    }.flatten()\n" +
                    "    def ignored = config.getValues(\"ignored.classes_or_packages\").collect{\n" +
                    "         it.tokenize(',').collect { it.trim() }.findAll { it }\n" +
                    "    }.flatten()\n" +
                    "    return !prefixList.intersect(ignored).isEmpty()\n" +
                    "```\n" +
                    "###set resolveProperty = true",
            recommendConfigSettingsHelper.loadRecommendConfig().toUnixString()
        )
    }
}
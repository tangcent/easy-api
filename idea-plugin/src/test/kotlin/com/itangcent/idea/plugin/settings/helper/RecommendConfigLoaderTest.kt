package com.itangcent.idea.plugin.settings.helper

import com.itangcent.mock.toUnixString
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test case of [RecommendConfigLoader]
 */
internal class RecommendConfigLoaderTest {

    @Test
    fun testPlaint() {
        assertTrue(RecommendConfigLoader.plaint().contains("module=#module"))
    }

    @Test
    fun testBuildRecommendConfig() {
        assertEquals(
            "#Get the module from the comment,group the apis\n" +
                    "module=#module\n" +
                    "#Ignore class/api\n" +
                    "ignore=#ignore\n" +
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
                    "#Support spring file\n" +
                    "type.is_file=groovy:it.isExtend(\"org.springframework.web.multipart.MultipartFile\")\n" +
                    "#ignore serialVersionUID\n" +
                    "constant.field.ignore=groovy:it.name()==\"serialVersionUID\"",
            RecommendConfigLoader.buildRecommendConfig("-Jackson_JsonIgnoreProperties,-converts,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped)")
                .toUnixString()
        )
    }

    @Test
    fun testAddSelectedConfig() {
        assertEquals(
            "-deprecated_java,-javax.validation(grouped),-deprecated_kotlin,-spring_webflux,-spring.ui,-spring_Entity,-javax.validation,import_spring_properties,-Jackson_JsonIgnoreProperties,-converts,-support_mock_for_general",
            RecommendConfigLoader.addSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),import_spring_properties",
                "import_spring_properties"
            )
        )
        assertEquals(
            "-import_spring_properties,-deprecated_java,module,-javax.validation(grouped),-deprecated_kotlin,-spring_webflux,-spring.ui,-spring_Entity,-javax.validation,import_spring_properties,-Jackson_JsonIgnoreProperties,-converts,-support_mock_for_general",
            RecommendConfigLoader.addSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),import_spring_properties",
                "module"
            )
        )
        assertEquals(
            "-import_spring_properties,support_mock_for_javax_validation,-deprecated_java,-javax.validation(grouped),-deprecated_kotlin,-spring_webflux,-spring.ui,-spring_Entity,-javax.validation,import_spring_properties,-Jackson_JsonIgnoreProperties,-converts,-support_mock_for_general",
            RecommendConfigLoader.addSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),import_spring_properties",
                "support_mock_for_javax_validation"
            )
        )
    }

    @Test
    fun testRemoveSelectedConfig() {
        assertEquals(
            "-import_spring_properties,-deprecated_java,-javax.validation(grouped),-deprecated_kotlin,-spring_webflux,-spring.ui,-spring_Entity,-javax.validation,-Jackson_JsonIgnoreProperties,-converts,-support_mock_for_general",
            RecommendConfigLoader.removeSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),import_spring_properties",
                "import_spring_properties"
            )
        )
        assertEquals(
            "-import_spring_properties,-deprecated_java,-javax.validation(grouped),-deprecated_kotlin,-spring_webflux,-spring.ui,-spring_Entity,-javax.validation,import_spring_properties,-Jackson_JsonIgnoreProperties,-module,-converts,-support_mock_for_general",
            RecommendConfigLoader.removeSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),import_spring_properties",
                "module"
            )
        )
        assertEquals(
            "-import_spring_properties,-deprecated_java,-javax.validation(grouped),-deprecated_kotlin,-spring_webflux,-spring.ui,-spring_Entity,-javax.validation,import_spring_properties,-Jackson_JsonIgnoreProperties,-converts,-support_mock_for_javax_validation,-support_mock_for_general",
            RecommendConfigLoader.removeSelectedConfig(
                "-Jackson_JsonIgnoreProperties,-converts,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),import_spring_properties",
                "support_mock_for_javax_validation"
            )
        )

    }

    @Test
    fun testCodes() {
        assertEquals(
            "[module, ignore, deprecated_java, deprecated_kotlin, Jackson, Jackson_JsonIgnoreProperties, Jackson_JsonUnwrapped, Gson, ignore_transient_field, converts, spring_Entity, spring_webflux, spring.validations, spring.ui, javax.validation, javax.validation(grouped), is_file, import_spring_properties, resolve_spring_properties, ignore_serialVersionUID, private_protected_field_only, not_ignore_static_final_field, Jackson_JsonNaming, Jackson_UpperCamelCaseStrategy, Jackson_SnakeCaseStrategy, Jackson_LowerCaseStrategy, Jackson_KebabCaseStrategy, Jackson_LowerDotCaseStrategy]",
            RecommendConfigLoader.codes().contentToString()
        )

    }

    @Test
    fun testSelectedCodes() {
        assertEquals(
            "[module, ignore, Jackson, Gson, ignore_transient_field, spring.validations, is_file, import_spring_properties, ignore_serialVersionUID]",
            RecommendConfigLoader.selectedCodes("-Jackson_JsonIgnoreProperties,-converts,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),import_spring_properties")
                .contentToString()
        )
    }

    @Test
    fun testDefaultCodes() {
        assertEquals(
            "module,ignore,deprecated_java,deprecated_kotlin,Jackson,Gson,ignore_transient_field,converts,spring_Entity,spring.validations,spring.ui,javax.validation,is_file,ignore_serialVersionUID",
            RecommendConfigLoader.defaultCodes()
        )
    }

    @Test
    fun testGet() {
        assertEquals(
            "#Get the module from the comment,group the apis\n" +
                    "module=#module", RecommendConfigLoader["module"]?.toUnixString()
        )
        assertEquals("module", RecommendConfigLoader[0])
    }
}
package com.itangcent.easyapi.channel.openapi

import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Regression test for the `build.gradle.kts` YAML dependency addition.
 *
 * Asserts the two new dependencies — `jackson-dataformat-yaml:2.12.2`
 * (used by [OpenApiSerializer.toYaml]) and `snakeyaml:1.33` (explicit
 * override of the transitive 1.27 to silence CVE-2022-1471 scanners) —
 * are on the test classpath. A thin-wrapper test per spec-driven
 * best-practices "When to Skip" — `build.gradle.kts` is a configuration
 * edit with no business logic.
 */
class OpenApiYamlDependencyTest {

    @Test
    fun jacksonYamlMapperIsOnClasspath() {
        val clazz = Class.forName("com.fasterxml.jackson.dataformat.yaml.YAMLMapper")
        assertNotNull(clazz)
    }

    @Test
    fun snakeyamlIsOnClasspath() {
        val clazz = Class.forName("org.yaml.snakeyaml.Yaml")
        assertNotNull(clazz)
    }
}

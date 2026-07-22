package com.itangcent.extension.springfox;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.service.ApiInfo;

/**
 * Realistic fixture: Springfox `Docket` is configured via a `@Bean` method on
 * a SEPARATE `@Configuration` class, NOT on the controller.
 *
 * Used by `ExtensionParsingTest.SpringfoxExtensionTest` to verify that
 * `springfox-openapi.config` uses `helper.findMethodsByAnnotation(...)` to
 * locate the `@Bean Docket` method globally and extract the document-level
 * metadata from its body.
 */
@Configuration
public class DocketConfig {

    @Bean
    public Docket api() {
        return new Docket()
            .apiInfo(new ApiInfo(
                "Cross-Class Springfox Title",
                "Cross-class Springfox description",
                "5.0",
                "", "", "", ""
            ))
            .host("cross-class.example.com")
            .pathMapping("/v5");
    }
}

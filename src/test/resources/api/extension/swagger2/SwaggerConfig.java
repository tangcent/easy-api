package com.itangcent.extension.swagger2;

import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Info;
import org.springframework.context.annotation.Configuration;

/**
 * Realistic fixture: `@SwaggerDefinition` lives on a SEPARATE
 * `@Configuration` class, NOT on the controller.
 *
 * Used by `ExtensionParsingTest.Swagger2ExtensionTest` to verify that
 * `swagger-openapi.config` uses `helper.findClassesByAnnotation(...)` to
 * locate this class globally and extract the document-level metadata.
 */
@Configuration
@SwaggerDefinition(
    host = "cross-class.example.com",
    basePath = "/v2",
    schemes = {SwaggerDefinition.Scheme.HTTPS},
    info = @Info(title = "Cross-Class Swagger2 Title", version = "2.9", description = "Cross-class SwaggerDefinition description")
)
public class SwaggerConfig {
}

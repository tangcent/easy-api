package com.itangcent.extension.swagger3;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Realistic fixture: `@OpenAPIDefinition` lives on a SEPARATE `@Configuration`
 * class, NOT on the controller. This mirrors how real projects declare
 * OpenAPI metadata — on a dedicated config class, not on every controller.
 *
 * Used by `ExtensionParsingTest.Swagger3ExtensionTest` to verify that
 * `swagger3-openapi.config` uses `helper.findClassesByAnnotation(...)` to
 * locate this class globally and extract the document-level metadata.
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Cross-Class Swagger3 Title",
        version = "9.1.0",
        description = "Cross-class OpenAPIDefinition description"
    ),
    servers = {@Server(url = "https://cross-class.example.com")}
)
public class OpenApiConfig {
}

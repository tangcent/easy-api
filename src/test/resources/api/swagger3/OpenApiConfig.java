package com.itangcent.swagger3.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * Document-level OpenAPI metadata lives on a SEPARATE `@Configuration` class,
 * NOT on the controller. This mirrors how real Spring Boot projects declare
 * OpenAPI metadata — on a dedicated config class, not on every controller.
 *
 * Used by `SwaggerConfigExtractionTest.Swagger3Test` to verify that
 * `swagger3-openapi.config` uses `helper.findClassesByAnnotation(...)` to
 * locate this class globally and extract the document-level metadata.
 */
@Configuration
@OpenAPIDefinition(
    info = @Info(title = "OpenAPIDefinition Title", version = "2.1.0", description = "OpenAPIDefinition Description"),
    servers = {@Server(url = "https://test.example.com", description = "Test server")}
)
public class OpenApiConfig {
}

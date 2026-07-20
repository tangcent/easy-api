package com.itangcent.swagger2.openapi;

import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Info;
import org.springframework.context.annotation.Configuration;

/**
 * Document-level Swagger v2 metadata lives on a SEPARATE `@Configuration`
 * class, NOT on the controller. This mirrors how real Spring Boot projects
 * declare Swagger metadata — on a dedicated config class, not on every
 * controller.
 *
 * Used by `SwaggerConfigExtractionTest.Swagger2Test` to verify that
 * `swagger-openapi.config` uses `helper.findClassesByAnnotation(...)` to
 * locate this class globally and extract the document-level metadata.
 */
@Configuration
@SwaggerDefinition(
    host = "api.example.com",
    basePath = "/v2",
    schemes = {SwaggerDefinition.Scheme.HTTPS},
    info = @Info(title = "V2 API", version = "1.0", description = "V2 desc")
)
public class SwaggerConfig {
}

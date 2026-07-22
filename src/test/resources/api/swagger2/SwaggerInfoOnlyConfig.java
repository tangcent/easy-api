package com.itangcent.swagger2.openapi;

import io.swagger.annotations.SwaggerDefinition;
import io.swagger.annotations.Info;
import org.springframework.context.annotation.Configuration;

/**
 * Document-level Swagger v2 metadata (info only, no host) lives on a SEPARATE
 * `@Configuration` class, NOT on the controller.
 *
 * Used by `SwaggerConfigExtractionTest.Swagger2InfoOnlyTest` to verify that
 * `servers` is omitted when `host` is absent, but `info` fields are still
 * extracted via `helper.findClassesByAnnotation(...)`.
 */
@Configuration
@SwaggerDefinition(
    info = @Info(title = "Info Only API", version = "2.0", description = "Info only desc")
)
public class SwaggerInfoOnlyConfig {
}

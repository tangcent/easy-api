package com.itangcent.swagger3.openapi;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plain controller WITHOUT `@OpenAPIDefinition`.
 *
 * Used by `SwaggerConfigExtractionTest.Swagger3Test` to verify that exporting
 * THIS controller still picks up `@OpenAPIDefinition` from the separate
 * `OpenApiConfig` class via `helper.findClassesByAnnotation(...)`.
 */
@RestController
@RequestMapping("/api")
public class PingController {

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}

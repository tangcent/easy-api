package com.itangcent.extension.swagger3;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plain controller WITHOUT `@OpenAPIDefinition`.
 *
 * Used by `ExtensionParsingTest.Swagger3ExtensionTest` to verify that
 * exporting THIS controller still picks up `@OpenAPIDefinition` from the
 * separate `OpenApiConfig` class via `helper.findClassesByAnnotation(...)`.
 */
@RestController
@RequestMapping("/ping")
public class PingController {

    @GetMapping
    public String ping() {
        return "pong";
    }
}

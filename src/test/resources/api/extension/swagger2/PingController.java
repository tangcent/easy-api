package com.itangcent.extension.swagger2;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plain controller WITHOUT `@SwaggerDefinition`.
 *
 * Used by `ExtensionParsingTest.Swagger2ExtensionTest` to verify that
 * exporting THIS controller still picks up `@SwaggerDefinition` from the
 * separate `SwaggerConfig` class via `helper.findClassesByAnnotation(...)`.
 */
@RestController
@RequestMapping("/ping")
public class PingController {

    @GetMapping
    public String ping() {
        return "pong";
    }
}

package com.itangcent.swagger2.openapi;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plain controller WITHOUT `@SwaggerDefinition`.
 *
 * Used by `SwaggerConfigExtractionTest.Swagger2Test` and `Swagger2InfoOnlyTest`
 * to verify that exporting THIS controller still picks up `@SwaggerDefinition`
 * from the separate config class via `helper.findClassesByAnnotation(...)`.
 */
@RestController
@RequestMapping("/api")
public class PingController {

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}

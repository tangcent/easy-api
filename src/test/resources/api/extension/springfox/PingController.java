package com.itangcent.extension.springfox;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plain controller WITHOUT any `@Bean Docket` method.
 *
 * Used by `ExtensionParsingTest.SpringfoxExtensionTest` to verify that
 * exporting THIS controller still picks up the `Docket` bean from the
 * separate `DocketConfig` class via `helper.findMethodsByAnnotation(...)`.
 */
@RestController
@RequestMapping("/ping")
public class PingController {

    @GetMapping
    public String ping() {
        return "pong";
    }
}

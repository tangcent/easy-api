package com.itangcent.swagger3.openapi;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller without @OpenAPIDefinition — used to verify the channel falls
 * through to settings/defaults cleanly when the annotation is absent.
 */
@RestController
@RequestMapping("/api")
public class NoOpenApiDefinitionController {

    @GetMapping("/status")
    public String status() {
        return "ok";
    }
}

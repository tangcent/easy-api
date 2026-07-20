package com.itangcent.swagger2.openapi;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.service.ApiInfo;

/**
 * Springfox Docket configuration fixture for the springfox-openapi.config
 * extractor test (see SwaggerConfigExtractionTest#testSpringfox*).
 *
 * The @Bean method {@link #api()} returns {@link Docket} and walks both the
 * constructor form of {@code ApiInfo(...)} (covers title/version/description)
 * and the {@code .host(...)} / {@code .pathMapping(...)} calls (covers
 * server.url).
 */
@Configuration
@RestController
@RequestMapping("/api")
public class SpringfoxDocketConfig {

    @Bean
    public Docket api() {
        return new Docket()
            .apiInfo(new ApiInfo("Springfox Title", "Springfox Desc", "2.3.0",
                "", "", "", ""))
            .host("api.example.com")
            .pathMapping("/v3");
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}

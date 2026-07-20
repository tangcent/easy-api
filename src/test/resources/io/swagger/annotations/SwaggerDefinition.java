package io.swagger.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SwaggerDefinition {
    String host() default "";
    String basePath() default "";
    Scheme[] schemes() default {};
    Info info() default @Info;

    /**
     * Enumeration of supported Swagger 2.x transport schemes.
     */
    enum Scheme {
        HTTP,
        HTTPS,
        WS,
        WSS
    }
}

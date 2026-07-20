package io.swagger.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Swagger 2.x nested @Info annotation used inside @SwaggerDefinition.
 *
 * Note: This is the v2 {@code io.swagger.annotations.Info}, distinct from the
 * v3 {@code io.swagger.v3.oas.annotations.info.Info}.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Info {
    String title() default "";
    String version() default "";
    String description() default "";
}

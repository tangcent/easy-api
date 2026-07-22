package springfox.documentation.spring.web.plugins;

/**
 * Minimal stub of Springfox's {@code Docket} class for config-extraction
 * tests. The groovy extractor only inspects the {@code @Bean} method's
 * declared return type name (must end with "Docket") and the method body
 * text — neither requires the real Springfox implementation.
 */
public class Docket {
    public Docket() {}
}

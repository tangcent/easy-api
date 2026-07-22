package springfox.documentation.service;

/**
 * Minimal stub of Springfox's {@code ApiInfo} class for config-extraction
 * tests. The groovy extractor parses the constructor-arg literal from the
 * source text of the @Bean method (e.g. {@code new ApiInfo("title", "desc",
 * "version", ...)}) — the real ApiInfo class is never loaded.
 */
public class ApiInfo {
    public ApiInfo(String title, String description, String version,
                   String terms, String contact, String license, String licenseUrl) {}
}

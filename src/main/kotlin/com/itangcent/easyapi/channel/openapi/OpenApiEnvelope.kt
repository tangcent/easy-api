package com.itangcent.easyapi.channel.openapi

/**
 * Resolved envelope metadata for the document (info + servers). Built by
 * `OpenApiChannel.resolveRuleBasedEnvelope` from rule evaluation; consumed
 * by [OpenApiFormatter.format].
 *
 * NOT a [com.itangcent.easyapi.channel.spi.ChannelConfig] subtype — it is
 * `internal` to the channel package and not exposed through the SPI.
 *
 * Replaces the v1 `OpenApiConfig.infoTitle` /
 * `infoVersion` / `infoDescription` / `serverUrl` fields that were removed.
 * Those values vary per project and belong in rule scripts (see
 * `swagger3-openapi.config` / `swagger-openapi.config` /
 * `springfox-openapi.config`); the channel resolves them via the rule engine
 * into this envelope, then hands the envelope to the pure formatter.
 *
 * @param infoTitle resolved from `openapi.info.title` rules; falls back to
 *   the project name (or `"API"` when the project is unnamed) when no rule
 *   produces a value — never `null`.
 * @param infoVersion resolved from `openapi.info.version` rules; falls back
 *   to `"1.0.0"` when no rule produces a value — never `null`.
 * @param infoDescription resolved from `openapi.info.description` rules;
 *   `null` when no rule produces a value (formatter omits the field).
 * @param serverUrl resolved from `openapi.server.url` / `openapi.host`
 *   rules; `null` when no rule produces a value (formatter omits `servers`).
 */
internal data class OpenApiEnvelope(
    val infoTitle: String,
    val infoVersion: String,
    val infoDescription: String?,
    val serverUrl: String?,
)

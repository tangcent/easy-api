package com.itangcent.easyapi.channel.openapi

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * Channel-local serializer for [OpenApiDocument].
 *
 * JSON output uses a channel-local [Gson] instance with `setPrettyPrinting()`,
 * `disableHtmlEscaping()`, `serializeNulls()` OFF so optional
 * fields are omitted, not nulled. The instance is NOT the shared `GsonUtils`
 * — channel isolation prevents formatting drift across channels (mirrors
 * `PostmanGson.pretty` / `hoppscotchGson()`).
 *
 * YAML output uses Jackson's [YAMLMapper] with
 * `serializationInclusion(NON_NULL)` so optional fields vanish,
 * and `WRITE_DOC_START_MARKER` disabled so the output begins with
 * `openapi: "3.0.3"` on line 1 (no leading `---`). Jackson auto-quotes
 * strings that would otherwise parse as a number, so `openapi: "3.0.3"`
 * is emitted with quotes.
 *
 * Both mappers are lazily initialized
 * — the YAML mapper is not built when the user picks JSON, and vice versa.
 *
 * Determinism: both mappers preserve `LinkedHashMap` insertion
 * order for `paths`, `components.schemas`, `tags`, `properties`,
 * `responses`, `content` — see [OpenApiDocument] and the formatter.
 */
object OpenApiSerializer {

    private val gson: Gson by lazy {
        GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create() // serializeNulls() OFF → optional fields omitted, not nulled
    }

    private val yamlMapper: ObjectMapper by lazy {
        YAMLMapper.builder()
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .build()
    }

    /** Serializes [doc] to a pretty-printed JSON string. */
    fun toJson(doc: OpenApiDocument): String = gson.toJson(doc)

    /** Serializes [doc] to a YAML string with no leading `---`. */
    fun toYaml(doc: OpenApiDocument): String = yamlMapper.writeValueAsString(doc)
}

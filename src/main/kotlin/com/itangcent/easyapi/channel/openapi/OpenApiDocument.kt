package com.itangcent.easyapi.channel.openapi

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.annotations.SerializedName
import com.itangcent.easyapi.core.export.HttpMethod

/**
 * OpenAPI 3.0.3 document tree (data-class subset emitted by EasyApi's
 * OpenAPI export channel).
 *
 * Designed for clean serialization via both Gson (JSON output) and Jackson
 * `YAMLMapper` (YAML output). All fields are nullable except the OAS-required
 * ones; optional fields are suppressed on the wire via
 * `serializeNulls()` off (Gson) and `@JsonInclude(NON_NULL)` (Jackson).
 *
 * [paths], [OperationObject.responses], [RequestBodyObject.content],
 * [MediaTypeObject.content]-less shapes, [ComponentsObject.schemas], and
 * [SchemaObject.properties] use [LinkedHashMap] to preserve insertion order
 * for byte-stable output across runs.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class OpenApiDocument(
    val openapi: String = "3.0.3",
    val info: InfoObject,
    val servers: List<ServerObject>? = null,
    val tags: List<TagObject>? = null,
    val paths: LinkedHashMap<String, PathItemObject>,
    val components: ComponentsObject? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class InfoObject(
    val title: String,
    val version: String,
    val description: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ServerObject(
    val url: String,
    val description: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TagObject(
    val name: String,
    val description: String? = null,
)

/**
 * OAS Path Item Object. Each HTTP method is a nullable field; absent methods
 * are omitted from serialized output (with `serializeNulls()` off / NON_NULL).
 *
 * `withMethod` returns a `copy(...)` so the [PathItemObject] is effectively
 * immutable from the formatter's perspective.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PathItemObject(
    val get: OperationObject? = null,
    val post: OperationObject? = null,
    val put: OperationObject? = null,
    val delete: OperationObject? = null,
    val patch: OperationObject? = null,
    val head: OperationObject? = null,
    val options: OperationObject? = null,
) {
    /** Returns a copy of this [PathItemObject] with [method] set to [op]. */
    fun withMethod(method: HttpMethod, op: OperationObject): PathItemObject = when (method) {
        HttpMethod.GET -> copy(get = op)
        HttpMethod.POST -> copy(post = op)
        HttpMethod.PUT -> copy(put = op)
        HttpMethod.DELETE -> copy(delete = op)
        HttpMethod.PATCH -> copy(patch = op)
        HttpMethod.HEAD -> copy(head = op)
        HttpMethod.OPTIONS -> copy(options = op)
        HttpMethod.NO_METHOD -> this
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class OperationObject(
    val tags: List<String>? = null,
    val summary: String? = null,
    val description: String? = null,
    val operationId: String,
    val parameters: List<ParameterObject>? = null,
    val requestBody: RequestBodyObject? = null,
    val responses: LinkedHashMap<String, ResponseObject>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ParameterObject(
    val name: String,
    @param:JsonProperty("in") @get:JsonProperty("in") val `in`: String,
    val description: String? = null,
    val required: Boolean = false,
    val schema: SchemaObject,
    val example: Any? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RequestBodyObject(
    val description: String? = null,
    val content: LinkedHashMap<String, MediaTypeObject>,
    val required: Boolean = false,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class MediaTypeObject(
    val schema: SchemaObject,
    val example: Any? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ResponseObject(
    val description: String,
    val content: LinkedHashMap<String, MediaTypeObject>? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ComponentsObject(
    val schemas: LinkedHashMap<String, SchemaObject>? = null,
)

/**
 * OAS Schema Object. Holds EITHER a `$ref` OR inline fields — never both.
 *
 * Field-name workaround:
 * - `$ref`, `enum`, `x-enumDescriptions` contain characters that are illegal
 *   in plain Kotlin identifiers; they are stored under Kotlin-safe source
 *   names (`ref` / `enumValues` / `xEnumDescriptions`) and aliased to their
 *   OAS wire names via `@SerializedName` (Gson) and `@JsonProperty` (Jackson).
 *
 * Note: Kotlin data-class constructor parameters don't auto-propagate
 * `@JsonProperty` to the getter without `jackson-module-kotlin` registered.
 * The `@get:JsonProperty(...)` use-site target is added alongside so Jackson
 * serialization picks up the OAS wire names (`$ref`/`enum`/`x-enumDescriptions`)
 * rather than the Kotlin-safe source identifiers.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SchemaObject(
    @SerializedName("\$ref") @get:JsonProperty("\$ref") val `$ref`: String? = null,
    val type: String? = null,
    val format: String? = null,
    val description: String? = null,
    val properties: LinkedHashMap<String, SchemaObject>? = null,
    val additionalProperties: SchemaObject? = null,
    val items: SchemaObject? = null,
    val required: List<String>? = null,
    @SerializedName("enum") @get:JsonProperty("enum") val enumValues: List<Any>? = null,
    val example: Any? = null,
    @SerializedName("x-enumDescriptions") @get:JsonProperty("x-enumDescriptions") val xEnumDescriptions: Map<String, String>? = null,
)

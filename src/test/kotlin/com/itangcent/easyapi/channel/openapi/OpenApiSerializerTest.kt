package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.testFramework.ResourceLoader
import com.itangcent.easyapi.testFramework.ResultLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Byte-parity golden-file tests for [OpenApiSerializer].
 *
 * Three fixtures exercise an increasing surface of the document model:
 *  - `minimal` — one GET endpoint, void response (no components, no servers).
 *  - `withSchema` — POST with request + response body referencing a `User`
 *    schema in `components.schemas` (exercises `$ref` + `enum` + required
 *    fields + nested `properties`).
 *  - `allFeatures` — every feature exercised: query/path/header/cookie
 *    params with enums and examples, request body (json + form + multipart),
 *    200/204 responses, tags, server URL.
 *
 * JSON comparison uses [ResultLoader.load] (trailing-trimmed) — the
 * golden files live at
 * `result/com.itangcent.easyapi.channel.openapi.OpenApiSerializerTest.<name>.txt`.
 *
 * YAML comparison uses [ResourceLoader.readRaw] (byte-strict) because
 * YAML's leading whitespace and trailing newline matter.
 */
class OpenApiSerializerTest {

    // ─── minimal: one GET endpoint, void response ──────────────────────────

    @Test
    fun minimalJson() {
        val json = OpenApiSerializer.toJson(minimalDoc())
        assertEquals(ResultLoader.load("minimalJson"), json)
    }

    @Test
    fun minimalYaml() {
        val yaml = OpenApiSerializer.toYaml(minimalDoc())
        assertEquals(
            ResourceLoader.readRaw(resourcePath("minimalYaml")),
            yaml,
        )
    }

    // ─── withSchema: POST with request + response body referencing User ────

    @Test
    fun withSchemaJson() {
        val json = OpenApiSerializer.toJson(withSchemaDoc())
        assertEquals(ResultLoader.load("withSchemaJson"), json)
    }

    @Test
    fun withSchemaYaml() {
        val yaml = OpenApiSerializer.toYaml(withSchemaDoc())
        assertEquals(
            ResourceLoader.readRaw(resourcePath("withSchemaYaml")),
            yaml,
        )
    }

    // ─── allFeatures: every feature exercised ──────────────────────────────

    @Test
    fun allFeaturesJson() {
        val json = OpenApiSerializer.toJson(allFeaturesDoc())
        assertEquals(ResultLoader.load("allFeaturesJson"), json)
    }

    @Test
    fun allFeaturesYaml() {
        val yaml = OpenApiSerializer.toYaml(allFeaturesDoc())
        assertEquals(
            ResourceLoader.readRaw(resourcePath("allFeaturesYaml")),
            yaml,
        )
    }

    // ─── Critical structural assertions ───────────────

    @Test
    fun jsonRendersOpenApi303AsQuotedString() {
        val json = OpenApiSerializer.toJson(minimalDoc())
        // JSON output has `"openapi": "3.0.3"`.
        assertTrue(
            "Expected JSON to contain \"openapi\": \"3.0.3\" — got: $json",
            json.contains("\"openapi\": \"3.0.3\""),
        )
        assertFalse(
            "JSON must not contain \"openapi\": 3.0.3 (unquoted number) — got: $json",
            json.contains("\"openapi\": 3.0.3"),
        )
    }

    @Test
    fun yamlRendersOpenApi303AsQuotedString() {
        val yaml = OpenApiSerializer.toYaml(minimalDoc())
        // YAML output has `openapi: "3.0.3"` quoted (not `openapi: 3.0.3`).
        assertTrue(
            "Expected YAML to contain openapi: \"3.0.3\" (quoted) — got: $yaml",
            yaml.contains("openapi: \"3.0.3\""),
        )
        assertFalse(
            "YAML must not contain openapi: 3.0.3 (unquoted) — got: $yaml",
            yaml.contains("openapi: 3.0.3"),
        )
    }

    @Test
    fun yamlHasNoLeadingDocumentStartMarker() {
        val yaml = OpenApiSerializer.toYaml(minimalDoc())
        // WRITE_DOC_START_MARKER disabled — no leading `---`.
        assertFalse(
            "YAML must not start with '---' — got: ${yaml.take(20)}",
            yaml.startsWith("---"),
        )
    }

    @Test
    fun jsonOmitsNullForAbsentOptionalFields() {
        val json = OpenApiSerializer.toJson(minimalDoc())
        // serializeNulls() OFF → no `"field": null` in the output.
        assertFalse(
            "JSON must not contain \": null\" for absent optional fields — got: $json",
            json.contains(": null"),
        )
    }

    @Test
    fun yamlOmitsNullForAbsentOptionalFields() {
        val yaml = OpenApiSerializer.toYaml(minimalDoc())
        // NON_NULL inclusion → no `: null` in the output.
        assertFalse(
            "YAML must not contain \": null\" for absent optional fields — got: $yaml",
            yaml.contains(": null"),
        )
    }

    @Test
    fun jsonSerializesRefEnumXEnumDescriptionsUnderWireNames() {
        val json = OpenApiSerializer.toJson(withSchemaDoc())
        // The Kotlin-safe identifiers (`$ref`, `enumValues`, `xEnumDescriptions`)
        // must serialize under their OAS wire names: `$ref`, `enum`,
        // `x-enumDescriptions`.
        assertTrue(
            "JSON should contain \"\$ref\": \"#/components/schemas/User\" — got: $json",
            json.contains("\$ref"),
        )
        // `enumValues` field name MUST NOT leak through (Gson uses @SerializedName).
        assertFalse(
            "JSON must not contain 'enumValues' (wire name is 'enum') — got: $json",
            json.contains("enumValues"),
        )
        // `xEnumDescriptions` field name MUST NOT leak through.
        assertFalse(
            "JSON must not contain 'xEnumDescriptions' (wire name is 'x-enumDescriptions') — got: $json",
            json.contains("xEnumDescriptions"),
        )
    }

    @Test
    fun yamlSerializesRefEnumXEnumDescriptionsUnderWireNames() {
        val yaml = OpenApiSerializer.toYaml(withSchemaDoc())
        assertTrue(
            "YAML should contain \$ref — got: $yaml",
            yaml.contains("\$ref"),
        )
        assertFalse(
            "YAML must not contain 'enumValues' (wire name is 'enum') — got: $yaml",
            yaml.contains("enumValues"),
        )
        assertFalse(
            "YAML must not contain 'xEnumDescriptions' (wire name is 'x-enumDescriptions') — got: $yaml",
            yaml.contains("xEnumDescriptions"),
        )
    }

    @Test
    fun jsonPreservesInsertionOrderOfPathsSchemasTags() {
        val json = OpenApiSerializer.toJson(allFeaturesDoc())
        // Paths must appear in insertion order, not alphabetical.
        val usersIdx = json.indexOf("\"/users\"")
        val usersByIdx = json.indexOf("\"/users/{id}\"")
        val formsIdx = json.indexOf("\"/forms/{id}\"")
        val avatarIdx = json.indexOf("\"/users/{id}/avatar\"")
        assertTrue("Expected /users before /users/{id} — got: $json", usersIdx < usersByIdx)
        assertTrue("Expected /users/{id} before /forms/{id} — got: $json", usersByIdx < formsIdx)
        assertTrue("Expected /forms/{id} before /users/{id}/avatar — got: $json", formsIdx < avatarIdx)

        // Tags array must preserve insertion order: Users, Forms, Uploads.
        val usersTagIdx = json.indexOf("\"name\": \"Users\"")
        val formsTagIdx = json.indexOf("\"name\": \"Forms\"")
        val uploadsTagIdx = json.indexOf("\"name\": \"Uploads\"")
        assertTrue(
            "Expected tag order Users < Forms < Uploads — got: $json",
            usersTagIdx < formsTagIdx && formsTagIdx < uploadsTagIdx,
        )
    }

    @Test
    fun yamlPreservesInsertionOrderOfPathsSchemasTags() {
        val yaml = OpenApiSerializer.toYaml(allFeaturesDoc())
        // Paths in insertion order.
        val usersIdx = yaml.indexOf("/users:")
        val formsIdx = yaml.indexOf("/forms/{id}:")
        val avatarIdx = yaml.indexOf("/users/{id}/avatar:")
        assertTrue("Expected /users before /forms/{id} — got: $yaml", usersIdx < formsIdx)
        assertTrue("Expected /forms/{id} before /users/{id}/avatar — got: $yaml", formsIdx < avatarIdx)
    }

    // ─── Determinism ────────────────────────────────────────────────

    @Test
    fun jsonOutputIsDeterministicAcrossMultipleRuns() {
        val doc = allFeaturesDoc()
        val first = OpenApiSerializer.toJson(doc)
        val second = OpenApiSerializer.toJson(doc)
        val third = OpenApiSerializer.toJson(doc)
        assertEquals("JSON output must be byte-stable across runs (run 1 vs 2)", first, second)
        assertEquals("JSON output must be byte-stable across runs (run 2 vs 3)", second, third)
    }

    @Test
    fun yamlOutputIsDeterministicAcrossMultipleRuns() {
        val doc = allFeaturesDoc()
        val first = OpenApiSerializer.toYaml(doc)
        val second = OpenApiSerializer.toYaml(doc)
        val third = OpenApiSerializer.toYaml(doc)
        assertEquals("YAML output must be byte-stable across runs (run 1 vs 2)", first, second)
        assertEquals("YAML output must be byte-stable across runs (run 2 vs 3)", second, third)
    }

    // ─── Fixture builders ───────────────────────────────────────────────────

    private fun resourcePath(name: String): String {
        val className = "com.itangcent.easyapi.channel.openapi.OpenApiSerializerTest"
        return "result/$className.$name.txt"
    }

    private fun minimalDoc(): OpenApiDocument {
        // One GET endpoint, void response (204), no components, no servers, no tags.
        return OpenApiDocument(
            info = InfoObject(title = "API", version = "1.0.0"),
            paths = linkedMapOf(
                "/users" to PathItemObject(
                    get = OperationObject(
                        operationId = "listUsers",
                        responses = linkedMapOf(
                            "204" to ResponseObject(description = "No content"),
                        ),
                    ),
                ),
            ),
        )
    }

    private fun withSchemaDoc(): OpenApiDocument {
        // POST with request + response body referencing a User schema in components.schemas.
        val userSchema = SchemaObject(
            type = "object",
            properties = linkedMapOf(
                "id" to SchemaObject(type = "integer", format = "int64"),
                "name" to SchemaObject(type = "string", description = "User name"),
                "status" to SchemaObject(
                    type = "string",
                    enumValues = listOf("ACTIVE", "INACTIVE"),
                    xEnumDescriptions = mapOf(
                        "ACTIVE" to "Active user",
                        "INACTIVE" to "Inactive user",
                    ),
                ),
            ),
            required = listOf("id", "name"),
        )
        val userRef = SchemaObject(`$ref` = "#/components/schemas/User")
        return OpenApiDocument(
            info = InfoObject(title = "API", version = "1.0.0"),
            paths = linkedMapOf(
                "/users" to PathItemObject(
                    post = OperationObject(
                        operationId = "createUser",
                        requestBody = RequestBodyObject(
                            content = linkedMapOf(
                                "application/json" to MediaTypeObject(schema = userRef),
                            ),
                        ),
                        responses = linkedMapOf(
                            "200" to ResponseObject(
                                description = "OK",
                                content = linkedMapOf(
                                    "application/json" to MediaTypeObject(schema = userRef),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            components = ComponentsObject(
                schemas = linkedMapOf("User" to userSchema),
            ),
        )
    }

    private fun allFeaturesDoc(): OpenApiDocument {
        val userSchema = SchemaObject(
            type = "object",
            properties = linkedMapOf(
                "id" to SchemaObject(type = "integer", format = "int64"),
                "name" to SchemaObject(type = "string", description = "User name"),
                "status" to SchemaObject(
                    type = "string",
                    enumValues = listOf("ACTIVE", "INACTIVE"),
                    xEnumDescriptions = mapOf(
                        "ACTIVE" to "Active user",
                        "INACTIVE" to "Inactive user",
                    ),
                ),
            ),
            required = listOf("id", "name"),
        )
        val userRef = SchemaObject(`$ref` = "#/components/schemas/User")

        return OpenApiDocument(
            info = InfoObject(
                title = "Demo API",
                version = "2.0.0",
                description = "A demo API for golden-file testing",
            ),
            servers = listOf(ServerObject(url = "https://api.example.com")),
            tags = listOf(
                TagObject(name = "Users"),
                TagObject(name = "Forms"),
                TagObject(name = "Uploads"),
            ),
            paths = linkedMapOf(
                "/users" to PathItemObject(
                    get = OperationObject(
                        tags = listOf("Users"),
                        summary = "List users",
                        operationId = "listUsers",
                        parameters = listOf(
                            ParameterObject(
                                name = "status",
                                `in` = "query",
                                description = "Filter by status",
                                schema = SchemaObject(
                                    type = "string",
                                    enumValues = listOf("ACTIVE", "INACTIVE"),
                                ),
                                example = "ACTIVE",
                            ),
                            ParameterObject(
                                name = "X-Request-Id",
                                `in` = "header",
                                description = "Request ID",
                                schema = SchemaObject(type = "string"),
                                example = "abc-123",
                            ),
                            ParameterObject(
                                name = "session",
                                `in` = "cookie",
                                description = "Session cookie",
                                schema = SchemaObject(type = "string"),
                            ),
                        ),
                        responses = linkedMapOf(
                            "200" to ResponseObject(
                                description = "OK",
                                content = linkedMapOf(
                                    "application/json" to MediaTypeObject(schema = userRef),
                                ),
                            ),
                        ),
                    ),
                    post = OperationObject(
                        tags = listOf("Users"),
                        summary = "Create user",
                        operationId = "createUser",
                        requestBody = RequestBodyObject(
                            content = linkedMapOf(
                                "application/json" to MediaTypeObject(schema = userRef),
                            ),
                        ),
                        responses = linkedMapOf(
                            "200" to ResponseObject(
                                description = "OK",
                                content = linkedMapOf(
                                    "application/json" to MediaTypeObject(schema = userRef),
                                ),
                            ),
                        ),
                    ),
                ),
                "/users/{id}" to PathItemObject(
                    get = OperationObject(
                        tags = listOf("Users"),
                        summary = "Get user by id",
                        operationId = "getUser",
                        parameters = listOf(
                            ParameterObject(
                                name = "id",
                                `in` = "path",
                                required = true,
                                description = "User ID",
                                schema = SchemaObject(type = "integer", format = "int64"),
                                example = 42,
                            ),
                        ),
                        responses = linkedMapOf(
                            "200" to ResponseObject(
                                description = "OK",
                                content = linkedMapOf(
                                    "application/json" to MediaTypeObject(schema = userRef),
                                ),
                            ),
                        ),
                    ),
                    delete = OperationObject(
                        tags = listOf("Users"),
                        summary = "Delete user",
                        operationId = "deleteUser",
                        parameters = listOf(
                            ParameterObject(
                                name = "id",
                                `in` = "path",
                                required = true,
                                schema = SchemaObject(type = "integer", format = "int64"),
                            ),
                        ),
                        responses = linkedMapOf(
                            "204" to ResponseObject(description = "No content"),
                        ),
                    ),
                ),
                "/forms/{id}" to PathItemObject(
                    post = OperationObject(
                        tags = listOf("Forms"),
                        summary = "Submit form",
                        operationId = "submitForm",
                        parameters = listOf(
                            ParameterObject(
                                name = "id",
                                `in` = "path",
                                required = true,
                                schema = SchemaObject(type = "string"),
                            ),
                        ),
                        requestBody = RequestBodyObject(
                            content = linkedMapOf(
                                "application/x-www-form-urlencoded" to MediaTypeObject(
                                    schema = SchemaObject(
                                        type = "object",
                                        properties = linkedMapOf(
                                            "username" to SchemaObject(type = "string"),
                                            "email" to SchemaObject(type = "string"),
                                        ),
                                        required = listOf("username"),
                                    ),
                                ),
                            ),
                            required = true,
                        ),
                        responses = linkedMapOf(
                            "200" to ResponseObject(description = "OK"),
                        ),
                    ),
                ),
                "/users/{id}/avatar" to PathItemObject(
                    post = OperationObject(
                        tags = listOf("Uploads"),
                        summary = "Upload avatar",
                        operationId = "uploadAvatar",
                        parameters = listOf(
                            ParameterObject(
                                name = "id",
                                `in` = "path",
                                required = true,
                                schema = SchemaObject(type = "integer", format = "int64"),
                            ),
                        ),
                        requestBody = RequestBodyObject(
                            content = linkedMapOf(
                                "multipart/form-data" to MediaTypeObject(
                                    schema = SchemaObject(
                                        type = "object",
                                        properties = linkedMapOf(
                                            "avatar" to SchemaObject(type = "string", format = "binary"),
                                        ),
                                        required = listOf("avatar"),
                                    ),
                                ),
                            ),
                            required = true,
                        ),
                        responses = linkedMapOf(
                            "204" to ResponseObject(description = "No content"),
                        ),
                    ),
                ),
            ),
            components = ComponentsObject(
                schemas = linkedMapOf("User" to userSchema),
            ),
        )
    }
}

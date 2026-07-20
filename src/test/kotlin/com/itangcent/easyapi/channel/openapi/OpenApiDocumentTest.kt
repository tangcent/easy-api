package com.itangcent.easyapi.channel.openapi

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.itangcent.easyapi.core.export.HttpMethod
import org.junit.Assert.*
import org.junit.Test

/**
 * Smoke test for [OpenApiDocument] and its nested data classes.
 *
 * Covers the `openapi` constant, `LinkedHashMap` ordering,
 * `withMethod` copy-based collapse, and the keyword-alias
 * workaround (`$ref`/`enum`/`x-enumDescriptions` `@SerializedName` aliases).
 */
class OpenApiDocumentTest {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    @Test
    fun openapiConstantIs303() {
        val doc = minimalDocument()
        assertEquals("3.0.3", doc.openapi)
    }

    @Test
    fun pathsPreservesInsertionOrder() {
        val paths = linkedMapOf<String, PathItemObject>()
        // Insert in deliberately non-alphabetical order.
        paths["/zebra"] = PathItemObject()
        paths["/alpha"] = PathItemObject()
        paths["/middle"] = PathItemObject()

        val doc = OpenApiDocument(
            info = InfoObject(title = "T", version = "1.0.0"),
            paths = paths,
        )

        val keys = doc.paths.keys.toList()
        assertEquals(listOf("/zebra", "/alpha", "/middle"), keys)
    }

    @Test
    fun responsesPreservesInsertionOrder() {
        val responses = linkedMapOf<String, ResponseObject>()
        responses["204"] = ResponseObject(description = "No content")
        responses["200"] = ResponseObject(description = "OK")

        val op = OperationObject(operationId = "x", responses = responses)
        assertEquals(listOf("204", "200"), op.responses.keys.toList())
    }

    @Test
    fun pathItemWithMethodReplacesOnlyTargetMethod() {
        val original = PathItemObject()
        val getOp = OperationObject(operationId = "getUser", responses = linkedMapOf())
        val postOp = OperationObject(operationId = "addUser", responses = linkedMapOf())

        val withGet = original.withMethod(HttpMethod.GET, getOp)
        assertSame(getOp, withGet.get)
        assertNull(withGet.post)
        assertNull(withGet.put)

        val withPost = withGet.withMethod(HttpMethod.POST, postOp)
        assertSame(getOp, withPost.get)
        assertSame(postOp, withPost.post)
        assertNull(withPost.put)
    }

    @Test
    fun pathItemWithMethodIsImmutable() {
        val original = PathItemObject()
        val getOp = OperationObject(operationId = "getUser", responses = linkedMapOf())
        val mutated = original.withMethod(HttpMethod.GET, getOp)

        // The original instance should not have been mutated.
        assertNull(original.get)
        assertSame(getOp, mutated.get)
    }

    @Test
    fun schemaObjectRefFieldSerializesAsDollarRef() {
        val schema = SchemaObject(`$ref` = "#/components/schemas/User")
        val json = gson.toJson(schema)
        val parsed = JsonParser.parseString(json).asJsonObject
        assertTrue(parsed.has("\$ref"))
        assertEquals("#/components/schemas/User", parsed.get("\$ref").asString)
        // The Kotlin-safe identifier `$ref` MUST NOT leak as a field name.
        assertFalse(parsed.has("ref"))
    }

    @Test
    fun schemaObjectEnumValuesSerializesAsEnum() {
        val schema = SchemaObject(
            type = "string",
            enumValues = listOf("ACTIVE", "INACTIVE"),
        )
        val json = gson.toJson(schema)
        val parsed = JsonParser.parseString(json).asJsonObject
        assertTrue(parsed.has("enum"))
        assertEquals(listOf("ACTIVE", "INACTIVE"), parsed.get("enum").asJsonArray.map { it.asString })
        // The Kotlin-safe identifier `enumValues` MUST NOT leak.
        assertFalse(parsed.has("enumValues"))
    }

    @Test
    fun schemaObjectXEnumDescriptionsSerializesAsXEnumDescriptions() {
        val schema = SchemaObject(
            type = "string",
            enumValues = listOf("ACTIVE", "INACTIVE"),
            xEnumDescriptions = linkedMapOf(
                "ACTIVE" to "User is active",
                "INACTIVE" to "User is inactive",
            ),
        )
        val json = gson.toJson(schema)
        val parsed = JsonParser.parseString(json).asJsonObject
        assertTrue(parsed.has("x-enumDescriptions"))
        val descriptions = parsed.getAsJsonObject("x-enumDescriptions")
        assertEquals("User is active", descriptions.get("ACTIVE").asString)
        assertEquals("User is inactive", descriptions.get("INACTIVE").asString)
        // The Kotlin-safe identifier `xEnumDescriptions` MUST NOT leak.
        assertFalse(parsed.has("xEnumDescriptions"))
    }

    @Test
    fun schemaObjectRoundTripsAllKeywordAliasesAtOnce() {
        val schema = SchemaObject(
            `$ref` = "#/components/schemas/Status",
            enumValues = listOf("A", "B"),
            xEnumDescriptions = linkedMapOf("A" to "alpha"),
        )
        val json = gson.toJson(schema)
        val parsed = JsonParser.parseString(json).asJsonObject
        assertTrue(parsed.has("\$ref"))
        assertTrue(parsed.has("enum"))
        assertTrue(parsed.has("x-enumDescriptions"))
        assertFalse(parsed.has("ref"))
        assertFalse(parsed.has("enumValues"))
        assertFalse(parsed.has("xEnumDescriptions"))
    }

    @Test
    fun serializeNullsOffOmitsAbsentOptionalFields() {
        val schema = SchemaObject(type = "string")
        val json = gson.toJson(schema)
        val parsed = JsonParser.parseString(json).asJsonObject
        assertEquals("string", parsed.get("type").asString)
        // Optional/nullable fields should be omitted, not `null`.
        assertFalse(parsed.has("\$ref"))
        assertFalse(parsed.has("format"))
        assertFalse(parsed.has("description"))
        assertFalse(parsed.has("properties"))
        assertFalse(parsed.has("additionalProperties"))
        assertFalse(parsed.has("items"))
        assertFalse(parsed.has("required"))
        assertFalse(parsed.has("enum"))
        assertFalse(parsed.has("example"))
        assertFalse(parsed.has("x-enumDescriptions"))
    }

    @Test
    fun fullDocumentSerializesCleanly() {
        val schema = SchemaObject(
            type = "object",
            properties = linkedMapOf(
                "id" to SchemaObject(type = "integer", format = "int64"),
                "name" to SchemaObject(type = "string"),
            ),
            required = listOf("id"),
        )
        val components = ComponentsObject(schemas = linkedMapOf("User" to schema))
        val op = OperationObject(
            tags = listOf("users"),
            summary = "Get user",
            operationId = "getUser",
            parameters = listOf(
                ParameterObject(
                    name = "id",
                    `in` = "path",
                    required = true,
                    schema = SchemaObject(type = "integer", format = "int64"),
                ),
            ),
            responses = linkedMapOf(
                "200" to ResponseObject(
                    description = "OK",
                    content = linkedMapOf(
                        "application/json" to MediaTypeObject(
                            schema = SchemaObject(`$ref` = "#/components/schemas/User"),
                        ),
                    ),
                ),
            ),
        )
        val doc = OpenApiDocument(
            info = InfoObject(title = "Demo API", version = "1.0.0"),
            paths = linkedMapOf("/users/{id}" to PathItemObject(get = op)),
            components = components,
        )

        val json = gson.toJson(doc)
        val parsed = JsonParser.parseString(json).asJsonObject

        assertEquals("3.0.3", parsed.get("openapi").asString)
        val info = parsed.getAsJsonObject("info")
        assertEquals("Demo API", info.get("title").asString)
        assertEquals("1.0.0", info.get("version").asString)

        val paths = parsed.getAsJsonObject("paths")
        assertTrue(paths.has("/users/{id}"))
        val getOp = paths.getAsJsonObject("/users/{id}").getAsJsonObject("get")
        assertEquals("getUser", getOp.get("operationId").asString)

        val componentsJson = parsed.getAsJsonObject("components")
        val schemas = componentsJson.getAsJsonObject("schemas")
        val userSchema = schemas.getAsJsonObject("User")
        assertEquals("object", userSchema.get("type").asString)
        val required = userObjAsList(userSchema.getAsJsonArray("required"))
        assertEquals(listOf("id"), required)
    }

    private fun userObjAsList(arr: com.google.gson.JsonArray): List<String> =
        arr.map { it.asString }

    private fun minimalDocument(): OpenApiDocument {
        val op = OperationObject(
            operationId = "getUser",
            responses = linkedMapOf("200" to ResponseObject(description = "OK")),
        )
        return OpenApiDocument(
            info = InfoObject(title = "Test", version = "1.0.0"),
            paths = linkedMapOf("/users/{id}" to PathItemObject(get = op)),
        )
    }
}

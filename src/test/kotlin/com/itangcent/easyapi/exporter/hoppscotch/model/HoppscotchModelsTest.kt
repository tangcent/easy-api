package com.itangcent.easyapi.exporter.hoppscotch.model

import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test

class HoppCollectionTest {

    @Test
    fun testHoppCollectionCreation() {
        val collection = HoppCollection(name = "Test API")
        assertEquals(12, collection.v)
        assertEquals("Test API", collection.name)
        assertTrue(collection.folders.isEmpty())
        assertTrue(collection.requests.isEmpty())
        assertEquals("inherit", collection.auth.authType)
        assertTrue(collection.headers.isEmpty())
        assertTrue(collection.variables.isEmpty())
        assertNull(collection.description)
        assertEquals("", collection.preRequestScript)
        assertEquals("", collection.testScript)
    }

    @Test
    fun testHoppCollectionWithFoldersAndRequests() {
        val request = HoppRESTRequest(name = "Get Users", method = "GET", endpoint = "/users")
        val folder = HoppCollection(name = "Users", requests = listOf(request))
        val collection = HoppCollection(name = "API", folders = listOf(folder), requests = listOf(request))
        assertEquals(1, collection.folders.size)
        assertEquals(1, collection.requests.size)
        assertEquals("Users", collection.folders[0].name)
    }

    @Test
    fun testHoppCollectionEquality() {
        val refId = generateUniqueRefId("coll")
        val c1 = HoppCollection(name = "API", _ref_id = refId)
        val c2 = HoppCollection(name = "API", _ref_id = refId)
        assertEquals(c1, c2)
    }

    @Test
    fun testHoppCollectionCopy() {
        val original = HoppCollection(name = "API")
        val copy = original.copy(name = "New API")
        assertEquals("New API", copy.name)
        assertEquals("API", original.name)
    }
}

class HoppRESTRequestTest {

    @Test
    fun testHoppRESTRequestCreation() {
        val request = HoppRESTRequest(
            name = "Create User",
            method = "POST",
            endpoint = "/users",
            params = listOf(HoppKeyValue("id", "1")),
            headers = listOf(HoppKeyValue("Content-Type", "application/json")),
            body = HoppRequestBody(contentType = "application/json", body = "{}"),
            preRequestScript = "pw.console.log('pre')",
            testScript = "pw.expect(pw.response.status).toBe(200)",
            description = "Creates a new user"
        )
        assertEquals("17", request.v)
        assertEquals("Create User", request.name)
        assertEquals("POST", request.method)
        assertEquals("/users", request.endpoint)
        assertEquals(1, request.params.size)
        assertEquals(1, request.headers.size)
        assertEquals("application/json", request.body.contentType)
        assertEquals("pw.console.log('pre')", request.preRequestScript)
        assertEquals("pw.expect(pw.response.status).toBe(200)", request.testScript)
        assertEquals("Creates a new user", request.description)
    }

    @Test
    fun testHoppRESTRequestWithDefaults() {
        val request = HoppRESTRequest(name = "Test", method = "GET", endpoint = "/test")
        assertEquals("17", request.v)
        assertTrue(request.params.isEmpty())
        assertTrue(request.headers.isEmpty())
        assertEquals("inherit", request.auth.authType)
        assertNull(request.body.contentType)
        assertNull(request.body.body)
        assertEquals("", request.preRequestScript)
        assertEquals("", request.testScript)
        assertTrue(request.requestVariables.isEmpty())
        assertTrue(request.responses.isEmpty())
        assertNull(request.description)
    }
}

class HoppKeyValueTest {

    @Test
    fun testHoppKeyValueCreation() {
        val kv = HoppKeyValue(key = "Authorization", value = "Bearer token", active = true, description = "Auth header")
        assertEquals("Authorization", kv.key)
        assertEquals("Bearer token", kv.value)
        assertTrue(kv.active)
        assertEquals("Auth header", kv.description)
    }

    @Test
    fun testHoppKeyValueWithDefaults() {
        val kv = HoppKeyValue(key = "Content-Type")
        assertEquals("Content-Type", kv.key)
        assertEquals("", kv.value)
        assertTrue(kv.active)
        assertNull(kv.description)
    }
}

class HoppAuthTest {

    @Test
    fun testHoppAuthDefaults() {
        val auth = HoppAuth()
        assertEquals("inherit", auth.authType)
        assertTrue(auth.authActive)
    }

    @Test
    fun testHoppAuthBearer() {
        val auth = HoppAuth(authType = "bearer", authActive = true)
        assertEquals("bearer", auth.authType)
        assertTrue(auth.authActive)
    }
}

class HoppRequestBodyTest {

    @Test
    fun testHoppRequestBodyDefaults() {
        val body = HoppRequestBody()
        assertNull(body.contentType)
        assertNull(body.body)
    }

    @Test
    fun testHoppRequestBodyJson() {
        val body = HoppRequestBody(contentType = "application/json", body = "{\"name\":\"test\"}")
        assertEquals("application/json", body.contentType)
        assertEquals("{\"name\":\"test\"}", body.body)
    }

    @Test
    fun testHoppRequestBodyFormData() {
        val entries = listOf(HoppFormDataEntry(key = "file", value = "test.txt"))
        val body = HoppRequestBody(contentType = "multipart/form-data", body = entries)
        assertEquals("multipart/form-data", body.contentType)
        assertNotNull(body.body)
    }
}

class HoppCollectionVariableTest {

    @Test
    fun testHoppCollectionVariableCreation() {
        val variable = HoppCollectionVariable(
            key = "baseUrl",
            initialValue = "http://localhost:8080",
            currentValue = "http://localhost:8080"
        )
        assertEquals("baseUrl", variable.key)
        assertEquals("http://localhost:8080", variable.initialValue)
        assertEquals("http://localhost:8080", variable.currentValue)
        assertFalse(variable.secret)
    }
}

class HoppRequestVariableTest {

    @Test
    fun testHoppRequestVariableCreation() {
        val variable = HoppRequestVariable(key = "token", value = "abc123", active = true)
        assertEquals("token", variable.key)
        assertEquals("abc123", variable.value)
        assertTrue(variable.active)
    }
}

class HoppFormDataEntryTest {

    @Test
    fun testHoppFormDataEntryCreation() {
        val entry = HoppFormDataEntry(key = "avatar", value = "photo.jpg", active = true, isFile = false)
        assertEquals("avatar", entry.key)
        assertEquals("photo.jpg", entry.value)
        assertTrue(entry.active)
        assertFalse(entry.isFile)
    }

    @Test
    fun testHoppFormDataEntryWithDefaults() {
        val entry = HoppFormDataEntry(key = "file")
        assertEquals("file", entry.key)
        assertEquals("", entry.value)
        assertTrue(entry.active)
        assertFalse(entry.isFile)
        assertNull(entry.contentType)
    }

    @Test
    fun testHoppFormDataEntryWithContentType() {
        val entry = HoppFormDataEntry(key = "data", value = "content", active = true, isFile = false, contentType = "text/plain")
        assertEquals("data", entry.key)
        assertEquals("content", entry.value)
        assertEquals("text/plain", entry.contentType)
    }
}

class HoppscotchGsonTest {

    @Test
    fun testHoppscotchGsonPrettyPrint() {
        val gson = hoppscotchGson(prettyPrint = true)
        val collection = HoppCollection(name = "Test")
        val json = gson.toJson(collection)
        assertTrue(json.contains("\n"))
        assertTrue(json.contains("\"v\""))
        assertTrue(json.contains("\"name\""))
    }

    @Test
    fun testHoppscotchGsonNoPrettyPrint() {
        val gson = hoppscotchGson(prettyPrint = false)
        val collection = HoppCollection(name = "Test")
        val json = gson.toJson(collection)
        assertFalse(json.contains("\n"))
    }

    @Test
    fun testHoppscotchGsonSerializeNulls() {
        val gson = hoppscotchGson(prettyPrint = false)
        val body = HoppRequestBody()
        val json = gson.toJson(body)
        val obj = gson.fromJson(json, JsonObject::class.java)
        assertTrue(obj.has("contentType"))
        assertTrue(obj.has("body"))
        assertTrue(obj.get("contentType").isJsonNull)
        assertTrue(obj.get("body").isJsonNull)
    }

    @Test
    fun testHoppscotchGsonCollectionVersionIsInt() {
        val gson = hoppscotchGson(prettyPrint = false)
        val collection = HoppCollection(name = "Test")
        val json = gson.toJson(collection)
        val obj = gson.fromJson(json, JsonObject::class.java)
        assertTrue(obj.get("v").isJsonPrimitive)
        assertTrue(obj.get("v").asJsonPrimitive.isNumber)
        assertEquals(12, obj.get("v").asInt)
    }

    @Test
    fun testHoppscotchGsonRequestVersionIsString() {
        val gson = hoppscotchGson(prettyPrint = false)
        val request = HoppRESTRequest(name = "Test", method = "GET", endpoint = "/test")
        val json = gson.toJson(request)
        val obj = gson.fromJson(json, JsonObject::class.java)
        assertTrue(obj.get("v").isJsonPrimitive)
        assertTrue(obj.get("v").asJsonPrimitive.isString)
        assertEquals("17", obj.get("v").asString)
    }

    @Test
    fun testResponsesSerializedAsObject() {
        val gson = hoppscotchGson(prettyPrint = false)
        val request = HoppRESTRequest(name = "Test", method = "GET", endpoint = "/test")
        val json = gson.toJson(request)
        val obj = gson.fromJson(json, JsonObject::class.java)
        assertTrue(obj.get("responses").isJsonObject)
        assertEquals(0, obj.getAsJsonObject("responses").size())
    }

    @Test
    fun testRefIdFormat() {
        val collRefId = generateUniqueRefId("coll")
        val reqRefId = generateUniqueRefId("req")
        assertTrue(collRefId.startsWith("coll_"))
        assertTrue(reqRefId.startsWith("req_"))
    }

    @Test
    fun testFullCollectionSerialization() {
        val gson = hoppscotchGson(prettyPrint = true)
        val collection = HoppCollection(
            name = "My API",
            folders = listOf(
                HoppCollection(
                    name = "Users",
                    requests = listOf(
                        HoppRESTRequest(
                            name = "Get Users",
                            method = "GET",
                            endpoint = "https://{{host}}/users",
                            params = listOf(HoppKeyValue("page", "1")),
                            headers = listOf(HoppKeyValue("Accept", "application/json")),
                            body = HoppRequestBody()
                        )
                    )
                )
            ),
            variables = listOf(
                HoppCollectionVariable(key = "host", initialValue = "localhost:8080", currentValue = "localhost:8080")
            ),
            preRequestScript = "pw.console.log('pre')",
            testScript = "pw.expect(pw.response.status).toBe(200)"
        )
        val json = gson.toJson(collection)
        val obj = gson.fromJson(json, JsonObject::class.java)

        assertEquals(12, obj.get("v").asInt)
        assertEquals("My API", obj.get("name").asString)
        assertEquals(1, obj.getAsJsonArray("folders").size())
        assertEquals(0, obj.getAsJsonArray("requests").size())
        val folder = obj.getAsJsonArray("folders")[0].asJsonObject
        assertEquals(1, folder.getAsJsonArray("requests").size())
        assertEquals("pw.console.log('pre')", obj.get("preRequestScript").asString)
        assertEquals("pw.expect(pw.response.status).toBe(200)", obj.get("testScript").asString)
        assertEquals(1, obj.getAsJsonArray("variables").size())
    }
}

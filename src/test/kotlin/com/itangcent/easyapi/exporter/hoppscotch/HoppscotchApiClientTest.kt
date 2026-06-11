package com.itangcent.easyapi.exporter.hoppscotch

import com.google.gson.JsonObject
import com.itangcent.easyapi.exporter.hoppscotch.model.HoppCollection
import com.itangcent.easyapi.exporter.hoppscotch.model.HoppRESTRequest
import com.itangcent.easyapi.http.HttpClient
import com.itangcent.easyapi.http.HttpRequest
import com.itangcent.easyapi.http.HttpResponse
import com.itangcent.easyapi.util.json.GsonUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class HoppscotchApiClientTest {

    private lateinit var mockHttpClient: MockHttpClient
    private lateinit var client: HoppscotchApiClient

    @Before
    fun setUp() {
        mockHttpClient = MockHttpClient()
        client = HoppscotchApiClient(
            token = "test-token-123",
            serverUrl = "https://hoppscotch.test",
            httpClient = mockHttpClient
        )
    }

    @Test
    fun `testConnection returns true for valid response`() = runBlocking {
        val data = JsonObject().apply {
            add("me", JsonObject().apply {
                addProperty("uid", "user1")
                addProperty("displayName", "Test User")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(data)
        assertTrue(client.testConnection())
    }

    @Test
    fun `testConnection returns false for error response`() = runBlocking {
        val errors = """{"errors":[{"message":"Unauthorized"}]}"""
        mockHttpClient.nextResponse = HttpResponse(code = 200, body = errors)
        assertFalse(client.testConnection())
    }

    @Test
    fun `testConnection returns false for blank token`() = runBlocking {
        val blankTokenClient = HoppscotchApiClient(
            token = "",
            serverUrl = "https://hoppscotch.test",
            httpClient = mockHttpClient
        )
        assertFalse(blankTokenClient.testConnection())
    }

    @Test
    fun `listTeams returns teams from valid response`() = runBlocking {
        val teamsData = JsonObject().apply {
            add("myTeams", GsonUtils.GSON.toJsonTree(listOf(
                mapOf("id" to "team1", "name" to "Team A"),
                mapOf("id" to "team2", "name" to "Team B")
            )))
        }
        mockHttpClient.nextResponse = graphqlResponse(teamsData)
        val teams = client.listTeams()
        assertEquals(2, teams.size)
        assertEquals("team1", teams[0].id)
        assertEquals("Team A", teams[0].name)
        assertEquals("team2", teams[1].id)
        assertEquals("Team B", teams[1].name)
    }

    @Test
    fun `listTeams returns empty for blank token`() = runBlocking {
        val blankTokenClient = HoppscotchApiClient(
            token = "",
            serverUrl = "https://hoppscotch.test",
            httpClient = mockHttpClient
        )
        assertTrue(blankTokenClient.listTeams().isEmpty())
    }

    @Test
    fun `listCollections returns collections from valid response`() = runBlocking {
        val collectionsData = JsonObject().apply {
            add("rootCollectionsOfTeam", GsonUtils.GSON.toJsonTree(listOf(
                mapOf("id" to "col1", "title" to "Collection A"),
                mapOf("id" to "col2", "title" to "Collection B")
            )))
        }
        mockHttpClient.nextResponse = graphqlResponse(collectionsData)
        val collections = client.listCollections()
        assertEquals(2, collections.size)
        assertEquals("col1", collections[0].id)
        assertEquals("Collection A", collections[0].name)
    }

    @Test
    fun `listCollections with teamId includes teamID in query`() = runBlocking {
        val collectionsData = JsonObject().apply {
            add("rootCollectionsOfTeam", GsonUtils.GSON.toJsonTree(emptyList<Map<String, String>>()))
        }
        mockHttpClient.nextResponse = graphqlResponse(collectionsData)
        client.listCollections(teamId = "team1")
        val lastRequest = mockHttpClient.lastRequest
        assertNotNull(lastRequest)
        assertTrue(lastRequest!!.body!!.contains("teamID"))
    }

    @Test
    fun `uploadCollection returns success for valid response`() = runBlocking {
        val importData = JsonObject().apply {
            add("importUserCollectionsFromJSON", JsonObject().apply {
                addProperty("exportedCollection", "new-col-1")
                addProperty("collectionType", "REST")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(importData)
        val collection = HoppCollection(
            name = "Test Collection",
            requests = listOf(HoppRESTRequest(name = "GET /api", method = "GET", endpoint = "/api"))
        )
        val result = client.uploadCollection(collection)
        assertTrue(result.success)
        assertEquals("new-col-1", result.collectionId)
    }

    @Test
    fun `uploadCollection with teamId uses importCollectionsFromJSON`() = runBlocking {
        val importData = JsonObject().apply {
            addProperty("importCollectionsFromJSON", true)
        }
        mockHttpClient.nextResponse = graphqlResponse(importData)
        val collection = HoppCollection(name = "Team Collection")
        val result = client.uploadCollection(collection, teamId = "team1")
        assertTrue(result.success)
        assertNull(result.collectionId) // Team import returns Boolean, no collection ID
        val lastRequest = mockHttpClient.lastRequest
        assertNotNull(lastRequest)
        assertTrue(lastRequest!!.body!!.contains("importCollectionsFromJSON"))
        assertTrue(lastRequest.body!!.contains("teamID"))
    }

    @Test
    fun `uploadCollection returns failure for GraphQL errors`() = runBlocking {
        val errorBody = """{"errors":[{"message":"Collection name already exists"}]}"""
        mockHttpClient.nextResponse = HttpResponse(code = 200, body = errorBody)
        val collection = HoppCollection(name = "Test")
        val result = client.uploadCollection(collection)
        assertFalse(result.success)
        assertEquals("Collection name already exists", result.message)
    }

    @Test
    fun `uploadCollection returns failure for blank token`() = runBlocking {
        val blankTokenClient = HoppscotchApiClient(
            token = "",
            serverUrl = "https://hoppscotch.test",
            httpClient = mockHttpClient
        )
        val collection = HoppCollection(name = "Test")
        val result = blankTokenClient.uploadCollection(collection)
        assertFalse(result.success)
        assertEquals("No access token configured", result.message)
    }

    @Test
    fun `updateCollection creates new then deletes old`() = runBlocking {
        val importData = JsonObject().apply {
            add("importUserCollectionsFromJSON", JsonObject().apply {
                addProperty("exportedCollection", "new-col-2")
                addProperty("collectionType", "REST")
            })
        }
        mockHttpClient.responseQueue.add(graphqlResponse(importData))
        val deleteData = JsonObject().apply {
            addProperty("deleteUserCollection", true)
        }
        mockHttpClient.responseQueue.add(graphqlResponse(deleteData))

        val collection = HoppCollection(name = "Updated")
        val result = client.updateCollection("old-col-1", collection)
        assertTrue(result.success)
        assertEquals("new-col-2", result.collectionId)
        assertTrue(result.message!!.contains("updated successfully"))
    }

    @Test
    fun `deleteCollection returns true for valid response`() = runBlocking {
        val deleteData = JsonObject().apply {
            addProperty("deleteUserCollection", true)
        }
        mockHttpClient.nextResponse = graphqlResponse(deleteData)
        assertTrue(client.deleteCollection("col-1"))
    }

    @Test
    fun `deleteCollection with teamId uses deleteCollection mutation`() = runBlocking {
        val deleteData = JsonObject().apply {
            addProperty("deleteCollection", true)
        }
        mockHttpClient.nextResponse = graphqlResponse(deleteData)
        assertTrue(client.deleteCollection("col-1", teamId = "team1"))
        val lastRequest = mockHttpClient.lastRequest
        assertNotNull(lastRequest)
        assertTrue(lastRequest!!.body!!.contains("deleteCollection"))
        assertTrue(lastRequest.body!!.contains("collectionID"))
    }

    @Test
    fun `deleteCollection returns false for blank token`() = runBlocking {
        val blankTokenClient = HoppscotchApiClient(
            token = "",
            serverUrl = "https://hoppscotch.test",
            httpClient = mockHttpClient
        )
        assertFalse(blankTokenClient.deleteCollection("col-1"))
    }

    @Test
    fun `request includes Bearer token in Authorization header`() = runBlocking {
        val data = JsonObject().apply {
            add("me", JsonObject().apply {
                addProperty("uid", "user1")
                addProperty("displayName", "Test User")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(data)
        client.testConnection()
        val lastRequest = mockHttpClient.lastRequest
        assertNotNull(lastRequest)
        val authHeader = lastRequest!!.headers.find { it.first == "Authorization" }
        assertNotNull(authHeader)
        assertEquals("Bearer test-token-123", authHeader!!.second)
    }

    @Test
    fun `request includes Content-Type json header`() = runBlocking {
        val data = JsonObject().apply {
            add("me", JsonObject().apply {
                addProperty("uid", "user1")
                addProperty("displayName", "Test User")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(data)
        client.testConnection()
        val lastRequest = mockHttpClient.lastRequest
        assertNotNull(lastRequest)
        val contentTypeHeader = lastRequest!!.headers.find { it.first == "Content-Type" }
        assertNotNull(contentTypeHeader)
        assertEquals("application/json", contentTypeHeader!!.second)
    }

    @Test
    fun `401 response returns false from testConnection`() = runBlocking {
        mockHttpClient.nextResponse = HttpResponse(code = 401, body = "Unauthorized")
        assertFalse(client.testConnection())
    }

    @Test
    fun `401 response makes listTeams return empty list`() = runBlocking {
        mockHttpClient.nextResponse = HttpResponse(code = 401, body = "Unauthorized")
        val result = client.listTeams()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `non-200 response returns null gracefully`() = runBlocking {
        mockHttpClient.nextResponse = HttpResponse(code = 500, body = "Internal Server Error")
        val result = client.listTeams()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `HoppscotchAuthException is an Exception with message`() {
        val exception = HoppscotchAuthException("Token expired")
        assertTrue(exception is Exception)
        assertEquals("Token expired", exception.message)
    }

    @Test
    fun `custom server URL uses api graphql path`() = runBlocking {
        val customClient = HoppscotchApiClient(
            token = "test-token",
            serverUrl = "https://custom.hoppscotch.example",
            httpClient = mockHttpClient
        )
        val data = JsonObject().apply {
            add("me", JsonObject().apply {
                addProperty("uid", "user1")
                addProperty("displayName", "Test User")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(data)
        customClient.testConnection()
        val lastRequest = mockHttpClient.lastRequest
        assertNotNull(lastRequest)
        assertEquals("https://custom.hoppscotch.example/graphql", lastRequest!!.url)
    }

    @Test
    fun `cloud server URL resolves to api hoppscotch io`() = runBlocking {
        val cloudClient = HoppscotchApiClient(
            token = "test-token",
            serverUrl = "https://hoppscotch.io",
            httpClient = mockHttpClient
        )
        val data = JsonObject().apply {
            add("me", JsonObject().apply {
                addProperty("uid", "user1")
                addProperty("displayName", "Test User")
            })
        }
        mockHttpClient.nextResponse = graphqlResponse(data)
        cloudClient.testConnection()
        val lastRequest = mockHttpClient.lastRequest
        assertNotNull(lastRequest)
        assertEquals("https://api.hoppscotch.io/graphql", lastRequest!!.url)
    }

    @Test
    fun `resolveApiBaseUrl returns api hoppscotch io for cloud`() {
        assertEquals(
            "https://api.hoppscotch.io",
            HoppscotchApiClient.resolveApiBaseUrl("https://hoppscotch.io")
        )
    }

    @Test
    fun `resolveApiBaseUrl returns same URL for self-hosted`() {
        assertEquals(
            "https://custom.example.com",
            HoppscotchApiClient.resolveApiBaseUrl("https://custom.example.com")
        )
    }

    @Test
    fun `isCloudServer returns true for hoppscotch io`() {
        assertTrue(HoppscotchApiClient.isCloudServer("https://hoppscotch.io"))
    }

    @Test
    fun `isCloudServer returns false for custom server`() {
        assertFalse(HoppscotchApiClient.isCloudServer("https://custom.example.com"))
    }

    private fun graphqlResponse(data: JsonObject): HttpResponse {
        val wrapper = JsonObject().apply { add("data", data) }
        return HttpResponse(code = 200, body = GsonUtils.GSON.toJson(wrapper))
    }

    class MockHttpClient : HttpClient {
        var nextResponse: HttpResponse = HttpResponse(code = 200, body = "{}")
        var responseQueue: ArrayDeque<HttpResponse> = ArrayDeque()
        var lastRequest: HttpRequest? = null

        override suspend fun execute(request: HttpRequest): HttpResponse {
            lastRequest = request
            return if (responseQueue.isNotEmpty()) responseQueue.removeFirst() else nextResponse
        }

        override fun close() {}
    }
}

package com.itangcent.easyapi.channel.openapi

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ApiHeader
import com.itangcent.easyapi.core.export.ApiParameter
import com.itangcent.easyapi.core.export.GrpcMetadata
import com.itangcent.easyapi.core.export.GrpcStreamingType
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.export.httpMetadata
import com.itangcent.easyapi.core.export.ParameterBinding
import com.itangcent.easyapi.core.export.ParameterType
import com.itangcent.easyapi.core.psi.model.FieldModel
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.testFramework.ResultLoader
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Golden-file tests for [OpenApiFormatter].
 *
 * The formatter signature takes [OpenApiEnvelope] (rule-resolved envelope
 * metadata) instead of [OpenApiConfig]. The default envelope here matches
 * what the channel produces when no rules fire: `infoTitle="API"`,
 * `infoVersion="1.0.0"`, `infoDescription=null`, `serverUrl=null`.
 *
 * Each test constructs `ApiEndpoint` instances directly (no PSI) — the model
 * is POJO. A local Gson-pretty instance serializes the produced
 * [OpenApiDocument] for byte comparison against the golden file
 * (`OpenApiSerializer.toJson` is not yet available).
 *
 * Golden files live at
 * `src/test/resources/result/com.itangcent.easyapi.channel.openapi.OpenApiFormatterTest.<name>.txt`
 * and are read via [ResultLoader.load] (CRLF→LF normalized).
 */
class OpenApiFormatterTest {

    private val project: Project = mock()
    private val formatter = OpenApiFormatter(project)

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    /** Default envelope — mirrors what `OpenApiChannel` produces when no rules fire. */
    private val defaultEnvelope: OpenApiEnvelope =
        OpenApiEnvelope(infoTitle = "API", infoVersion = "1.0.0", infoDescription = null, serverUrl = null)

    // ─── simple GET with void response ───────────────────────

    @Test
    fun simpleGet() {
        val endpoint = endpoint(
            name = "List users",
            path = "/users",
            method = HttpMethod.GET,
            sourceMethod = mockMethod("listUsers"),
        )
        val doc = formatter.format(listOf(endpoint), defaultEnvelope)
        val json = gson.toJson(doc)
        assertEquals(ResultLoader.load("simpleGet"), json)
    }

    // ─── POST with JSON body + response body ───────────

    @Test
    fun postWithJsonBody() {
        val userBody = ObjectModel.Object(
            linkedMapOf(
                "id" to FieldModel(model = ObjectModel.single("long"), required = true),
                "name" to FieldModel(model = ObjectModel.single("string")),
            )
        )
        val endpoint = endpoint(
            name = "Create user",
            path = "/users",
            method = HttpMethod.POST,
            contentType = "application/json",
            bodyAttr = "com.example.User",
            body = userBody,
            responseBody = userBody,
            responseType = "com.example.User",
            sourceMethod = mockMethod("createUser"),
        )
        val doc = formatter.format(listOf(endpoint), defaultEnvelope)
        val json = gson.toJson(doc)
        assertEquals(ResultLoader.load("postWithJsonBody"), json)
    }

    // ─── path collapse — two methods on same path ─────────────

    @Test
    fun pathCollapse() {
        val getEndpoint = endpoint(
            name = "List users",
            path = "/users",
            method = HttpMethod.GET,
            sourceMethod = mockMethod("listUsers"),
        )
        val postEndpoint = endpoint(
            name = "Create user",
            path = "/users",
            method = HttpMethod.POST,
            sourceMethod = mockMethod("createUser"),
        )
        val doc = formatter.format(listOf(getEndpoint, postEndpoint), defaultEnvelope)
        val json = gson.toJson(doc)
        assertEquals(ResultLoader.load("pathCollapse"), json)
    }

    // ─── path normalization — Spring colon form ──────────────

    @Test
    fun pathNormalization() {
        val endpoint = endpoint(
            name = "Get user by id",
            path = "/users/:id",
            method = HttpMethod.GET,
            sourceMethod = mockMethod("getUser"),
        )
        val doc = formatter.format(listOf(endpoint), defaultEnvelope)
        val json = gson.toJson(doc)
        assertEquals(ResultLoader.load("pathNormalization"), json)
    }

    // ─── operationId collision ─────────────────────────────────────

    @Test
    fun operationIdCollision() {
        val first = endpoint(
            name = "Get user",
            path = "/users/{id}",
            method = HttpMethod.GET,
            sourceMethod = mockMethod("getUser"),
        )
        val second = endpoint(
            name = "Get user (admin)",
            path = "/admin/users/{id}",
            method = HttpMethod.GET,
            sourceMethod = mockMethod("getUser"),
        )
        val doc = formatter.format(listOf(first, second), defaultEnvelope)
        val json = gson.toJson(doc)
        assertEquals(ResultLoader.load("operationIdCollision"), json)
    }

    // ─── parameter mapping ─────────────────────────────────────

    @Test
    fun parameters() {
        val endpoint = endpoint(
            name = "Search users",
            path = "/users/{id}",
            method = HttpMethod.GET,
            parameters = listOf(
                ApiParameter(
                    name = "id",
                    binding = ParameterBinding.Path,
                    required = true,
                    description = "User ID",
                    example = "42",
                ),
                ApiParameter(
                    name = "status",
                    binding = ParameterBinding.Query,
                    required = false,
                    description = "Filter by status",
                    enumValues = listOf("ACTIVE", "INACTIVE"),
                ),
                ApiParameter(
                    name = "X-Custom-Header",
                    binding = ParameterBinding.Header,
                    required = true,
                    description = "Custom header from ApiParameter",
                ),
                ApiParameter(
                    name = "session",
                    binding = ParameterBinding.Cookie,
                    description = "Session cookie",
                ),
            ),
            headers = listOf(
                // Dedup: this header should be skipped because there's already
                // a header-typed ApiParameter with the same name.
                ApiHeader(name = "X-Custom-Header", description = "Duplicate header", required = false),
                // New header — should appear in parameters.
                ApiHeader(name = "X-Request-Id", value = "abc-123", description = "Request ID", required = false),
            ),
            sourceMethod = mockMethod("searchUsers"),
        )
        val doc = formatter.format(listOf(endpoint), defaultEnvelope)
        val json = gson.toJson(doc)
        assertEquals(ResultLoader.load("parameters"), json)
    }

    // ─── multipart with file params ───────────────────────────

    @Test
    fun requestBodyMultipart() {
        val endpoint = endpoint(
            name = "Upload avatar",
            path = "/users/{id}/avatar",
            method = HttpMethod.POST,
            contentType = "multipart/form-data",
            parameters = listOf(
                ApiParameter(name = "id", binding = ParameterBinding.Path, required = true),
                ApiParameter(
                    name = "avatar",
                    binding = ParameterBinding.Form,
                    type = ParameterType.FILE,
                    required = true,
                    description = "Avatar image file",
                ),
                ApiParameter(
                    name = "gallery",
                    binding = ParameterBinding.Form,
                    type = ParameterType.FILE,
                    required = false,
                    description = "Additional gallery images (multiple)",
                ),
            ),
            sourceMethod = mockMethod("uploadAvatar"),
        )
        val doc = formatter.format(listOf(endpoint), defaultEnvelope)
        val json = gson.toJson(doc)
        assertEquals(ResultLoader.load("requestBodyMultipart"), json)
    }

    // ─── form-urlencoded with form params ──────────────────────────

    @Test
    fun requestBodyFormUrlencoded() {
        val endpoint = endpoint(
            name = "Submit form",
            path = "/forms/{id}",
            method = HttpMethod.POST,
            contentType = "application/x-www-form-urlencoded",
            parameters = listOf(
                ApiParameter(name = "id", binding = ParameterBinding.Path, required = true),
                ApiParameter(
                    name = "username",
                    binding = ParameterBinding.Form,
                    required = true,
                    description = "Username",
                ),
                ApiParameter(
                    name = "email",
                    binding = ParameterBinding.Form,
                    required = false,
                    description = "Email",
                ),
            ),
            sourceMethod = mockMethod("submitForm"),
        )
        val doc = formatter.format(listOf(endpoint), defaultEnvelope)
        val json = gson.toJson(doc)
        assertEquals(ResultLoader.load("requestBodyFormUrlencoded"), json)
    }

    // ─── GET with body model — suppressed ──────────────────────

    @Test
    fun requestBodySuppressedForGet() {
        val bodyModel = ObjectModel.Object(
            linkedMapOf("query" to FieldModel(model = ObjectModel.single("string")))
        )
        val endpoint = endpoint(
            name = "Search",
            path = "/search",
            method = HttpMethod.GET,
            contentType = "application/json",
            body = bodyModel,
            bodyAttr = "com.example.SearchQuery",
            sourceMethod = mockMethod("search"),
        )
        val doc = formatter.format(listOf(endpoint), defaultEnvelope)
        val json = gson.toJson(doc)
        assertEquals(ResultLoader.load("requestBodySuppressedForGet"), json)
    }

    // ─── tags resolution ──────────────────────────────────

    @Test
    fun tagsResolution() {
        val usersEndpoint = endpoint(
            name = "List users",
            path = "/users",
            method = HttpMethod.GET,
            folder = "Users",
            className = "com.example.UserController",
            sourceMethod = mockMethod("listUsers"),
        )
        val adminEndpoint = endpoint(
            name = "List admins",
            path = "/admins",
            method = HttpMethod.GET,
            // No folder — falls back to className simple name → "AdminController"
            className = "com.example.AdminController",
            sourceMethod = mockMethod("listAdmins"),
        )
        // Another endpoint under "Users" — the tag should NOT reappear.
        val secondUserEndpoint = endpoint(
            name = "Get user",
            path = "/users/{id}",
            method = HttpMethod.GET,
            folder = "Users",
            className = "com.example.UserController",
            sourceMethod = mockMethod("getUser"),
        )
        val doc = formatter.format(
            listOf(usersEndpoint, adminEndpoint, secondUserEndpoint),
            defaultEnvelope,
        )
        val json = gson.toJson(doc)
        assertEquals(ResultLoader.load("tagsResolution"), json)
    }

    // ─── void response (no responseBody, no responseType) ─────────

    @Test
    fun voidResponse() {
        val endpoint = endpoint(
            name = "Delete user",
            path = "/users/{id}",
            method = HttpMethod.DELETE,
            parameters = listOf(
                ApiParameter(name = "id", binding = ParameterBinding.Path, required = true),
            ),
            sourceMethod = mockMethod("deleteUser"),
        )
        val doc = formatter.format(listOf(endpoint), defaultEnvelope)
        val json = gson.toJson(doc)
        assertEquals(ResultLoader.load("voidResponse"), json)
    }

    // ─── responseType only — no inline schema ──────────────────────

    @Test
    fun responseTypeOnly() {
        val endpoint = endpoint(
            name = "Get config",
            path = "/config",
            method = HttpMethod.GET,
            responseType = "com.example.Config",
            sourceMethod = mockMethod("getConfig"),
        )
        val doc = formatter.format(listOf(endpoint), defaultEnvelope)
        val json = gson.toJson(doc)
        assertEquals(ResultLoader.load("responseTypeOnly"), json)
    }

    // ─── gRPC skipped ───────────────────────────────────────────────

    @Test
    fun grpcSkipped() {
        val httpEndpoint = endpoint(
            name = "Get user",
            path = "/users/{id}",
            method = HttpMethod.GET,
            sourceMethod = mockMethod("getUser"),
        )
        val grpcEndpoint = ApiEndpoint(
            name = "SayHello",
            metadata = GrpcMetadata(
                path = "/helloworld.Greeter/SayHello",
                serviceName = "Greeter",
                methodName = "SayHello",
                packageName = "helloworld",
                streamingType = GrpcStreamingType.UNARY,
            ),
        )
        val doc = formatter.format(listOf(httpEndpoint, grpcEndpoint), defaultEnvelope)
        val json = gson.toJson(doc)
        assertEquals(ResultLoader.load("grpcSkipped"), json)
    }

    // ─── Structural assertions (don't depend on golden files) ──────────────

    @Test
    fun formatProducesDocumentWithOpenApi303Constant() {
        val endpoint = endpoint(
            path = "/health",
            method = HttpMethod.GET,
            sourceMethod = mockMethod("health"),
        )
        val doc = formatter.format(listOf(endpoint), defaultEnvelope)
        assertEquals("3.0.3", doc.openapi)
    }

    @Test
    fun formatIncludesServerWhenConfigured() {
        val endpoint = endpoint(
            path = "/health",
            method = HttpMethod.GET,
            sourceMethod = mockMethod("health"),
        )
        val doc = formatter.format(
            listOf(endpoint),
            OpenApiEnvelope(infoTitle = "API", infoVersion = "1.0.0", infoDescription = null, serverUrl = "https://api.example.com"),
        )
        assertNotNull(doc.servers)
        assertEquals(1, doc.servers!!.size)
        assertEquals("https://api.example.com", doc.servers!!.first().url)
    }

    @Test
    fun formatOmitsServersWhenServerUrlIsNull() {
        val endpoint = endpoint(
            path = "/health",
            method = HttpMethod.GET,
            sourceMethod = mockMethod("health"),
        )
        val doc = formatter.format(listOf(endpoint), defaultEnvelope)
        assertNull(doc.servers)
    }

    @Test
    fun formatUsesEnvelopeInfoTitleVersionAndDescription() {
        val endpoint = endpoint(
            path = "/health",
            method = HttpMethod.GET,
            sourceMethod = mockMethod("health"),
        )
        val doc = formatter.format(
            listOf(endpoint),
            OpenApiEnvelope(
                infoTitle = "My API",
                infoVersion = "2.0.0",
                infoDescription = "A description",
                serverUrl = null,
            ),
        )
        assertEquals("My API", doc.info.title)
        assertEquals("2.0.0", doc.info.version)
        assertEquals("A description", doc.info.description)
    }

    @Test
    fun formatDefaultsInfoTitleToApiAndVersionTo100() {
        val endpoint = endpoint(
            path = "/health",
            method = HttpMethod.GET,
            sourceMethod = mockMethod("health"),
        )
        val doc = formatter.format(listOf(endpoint), defaultEnvelope)
        assertEquals("API", doc.info.title)
        assertEquals("1.0.0", doc.info.version)
        assertNull(doc.info.description)
    }

    @Test
    fun formatAlwaysEmitsPathsMapEvenWhenEmpty() {
        val doc = formatter.format(emptyList(), defaultEnvelope)
        assertNotNull(doc.paths)
        assertTrue(doc.paths!!.isEmpty())
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private fun endpoint(
        name: String? = null,
        path: String,
        method: HttpMethod,
        parameters: List<ApiParameter> = emptyList(),
        headers: List<ApiHeader> = emptyList(),
        contentType: String? = null,
        bodyAttr: String? = null,
        body: ObjectModel? = null,
        responseBody: ObjectModel? = null,
        responseType: String? = null,
        sourceMethod: PsiMethod? = null,
        folder: String? = null,
        className: String? = null,
    ): ApiEndpoint {
        return ApiEndpoint(
            name = name,
            folder = folder,
            metadata = httpMetadata(
                path = path,
                method = method,
                parameters = parameters,
                headers = headers,
                contentType = contentType,
                bodyAttr = bodyAttr,
                body = body,
                responseBody = responseBody,
                responseType = responseType,
            ),
            sourceMethod = sourceMethod,
            className = className,
        )
    }

    private fun mockMethod(name: String): PsiMethod {
        val method = mock<PsiMethod>()
        whenever(method.name).thenReturn(name)
        return method
    }
}

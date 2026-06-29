# EasyApi

[![CI](https://github.com/tangcent/easy-api/actions/workflows/ci.yml/badge.svg)](https://github.com/tangcent/easy-api/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/tangcent/easy-api/branch/master/graph/badge.svg?token=4DPGLAWL3Q)](https://codecov.io/gh/tangcent/easy-api)
[![](https://img.shields.io/jetbrains/plugin/v/12211?color=blue&label=version)](https://plugins.jetbrains.com/plugin/12211-easyapi)
[![](https://img.shields.io/jetbrains/plugin/d/12211)](https://plugins.jetbrains.com/plugin/12211-easyapi)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/tangcent/easy-api)

> **Note:** This is the v3.0 rewrite of EasyApi. For the source code of stable v2.x releases, see the [`stable/v2.x.x`](https://github.com/tangcent/easy-api/tree/stable/v2.x.x) branch.

An IntelliJ IDEA plugin for API development — export API documentation, send requests, and manage endpoints directly from your code.

## Features

### API Export

Export API endpoints from your source code to multiple formats:

| Format | HTTP | gRPC | Output |
|--------|:----:|:----:|--------|
| **Markdown** | ✓ | ✓ | `.md` documentation file |
| **Postman** | ✓ | — | JSON file or direct upload to Postman |
| **Hoppscotch** *(Beta)* | ✓ | — | JSON file or direct upload to Hoppscotch |
| **cURL** | ✓ | ✓ | Executable shell script |
| **HTTP Client** | ✓ | ✓ | IntelliJ HTTP Client scratch file |

### API Dashboard

A built-in tool window that provides a tree view of all API endpoints in your project:

- Browse endpoints organized by module and class
- Search and filter endpoints by path, name, or HTTP method
- View endpoint details (parameters, headers, body, response)
- Send HTTP requests directly from the dashboard
- Navigate to source code with a single click
- Edit request parameters with auto-persistence

### Send API Requests

Call any API endpoint directly from the editor:

- Right-click a controller method → **Call** (or press `Ctrl+C` on macOS / `Alt+Shift+C`)
- The API Dashboard opens and navigates to the selected endpoint
- Edit parameters, headers, and body before sending
- View response with syntax highlighting

### API Search Everywhere

Find API endpoints from anywhere in the IDE using IntelliJ's **Search Everywhere** (`Double Shift`):

- Search by HTTP method prefix (e.g., `GET /users`)
- Search by path, endpoint name, class name, or description
- Click a result to navigate directly to the source method

### Gutter Icons

API methods are marked with a gutter icon in the editor. Click it to open the endpoint in the API Dashboard.

### Field Conversion

Convert class fields to various formats:

- **To JSON** — Standard JSON with default values
- **To JSON5** — JSON5 format with comments support
- **To Properties** — Java `.properties` format

## Supported Frameworks

| Category | Supported |
|----------|-----------|
| **Languages** | Java, Kotlin, Scala (optional) |
| **Web Frameworks** | Spring MVC, Spring Cloud OpenFeign, JAX-RS (Quarkus / Jersey) |
| **RPC** | gRPC |
| **Validation** | javax.validation / Jakarta Validation |
| **Serialization** | Jackson, Gson |
| **API Docs** | Swagger / OpenAPI annotations |
| **Spring Actuator** | Actuator endpoints |

### Spring MVC

Full support for Spring MVC annotations:

- `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`
- `@RequestParam`, `@PathVariable`, `@RequestBody`, `@RequestHeader`, `@CookieValue`
- `@RestController`, `@Controller`
- Class-level and method-level mapping composition
- Generic type resolution for parameterized controllers
- Custom meta-annotation support

### Spring Cloud OpenFeign

Support for Feign client interfaces:

- `@FeignClient` interface detection
- Spring MVC annotations on interface methods
- Native Feign annotations: `@RequestLine`, `@Headers`, `@Body`, `@Param`

### JAX-RS

Full support for JAX-RS annotations:

- `@Path`, `@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH`, `@HEAD`, `@OPTIONS`
- `@PathParam`, `@QueryParam`, `@FormParam`, `@HeaderParam`, `@CookieParam`, `@MatrixParam`
- `@Consumes`, `@Produces`

### gRPC

Support for gRPC service implementations:

- Service path extraction (`/<package>.<ServiceName>/<MethodName>`)
- Streaming type detection (unary, server-streaming, client-streaming, bidirectional)
- Request/response protobuf message type resolution
- Server reflection support
- Stub class resolution

## How to Use

### Export APIs

1. Right-click on a controller file, class, or method in the editor or project view
2. Select **EasyApi → Export** (or press `Ctrl+E` on macOS / `Alt+Shift+E`)
3. Choose the target format (Postman / Hoppscotch *(Beta)* / Markdown / cURL / HTTP Client)
4. The APIs will be exported automatically

### Call an API

1. Right-click on a controller method
2. Select **EasyApi → Call** (or press `Ctrl+C` on macOS / `Alt+Shift+C`)
3. The API Dashboard opens with the endpoint loaded
4. Edit parameters and send the request

### Open API Dashboard

- Go to **Tools → Open API Dashboard**
- Or click the **API Dashboard** tab at the bottom of the IDE

### Search APIs

- Press `Double Shift` to open Search Everywhere
- Switch to the **APIs** tab
- Type an HTTP method prefix (e.g., `GET /users`) or any keyword

### Convert Fields

1. Right-click on a class in the editor
2. Select **EasyApi → ToJson / ToJson5 / ToProperties**

## Configuration

EasyApi uses a layered configuration system with multiple sources, processed in priority order:

| Priority | Source | Description |
|----------|--------|-------------|
| Highest | Runtime | Programmatic overrides set during execution |
| | Project rules | `<project>/.easyapi/*.rules` (3.0 folder model) |
| | Global rules | `~/.easyapi/*.rules` (applied to every project on the machine) |
| | Legacy project file | `.easy.api.config*` in the project root (and ancestor dirs) |
| | Extension | Plugin extension configs (Swagger, validation, etc.) |
| | Remote | Config files fetched from URLs |
| Lowest | Built-in | Default bundled configuration |

Configuration supports:

- **Property resolution** — Reference other config values with `${key}`
- **Directives** — Control parsing behavior (`#resolve`, `#ignore`, etc.)
- **Rule engine** — Groovy scripts, regex, annotation expressions, tag expressions
- **Remote configs** — Load shared configs from URLs (e.g., Swagger, javax.validation presets)

### When do you need a custom rule?

EasyApi understands standard HTTP frameworks (Spring MVC, WebFlux, JAX-RS, Feign) out of the box — **most projects need no custom rules**. For custom framework behaviour the scanner can't see (e.g. a `jakarta.servlet.Filter` that requires a header, or a `ResponseBodyAdvice` that wraps every response in an envelope), use the built-in AI Assistant or the external skill to detect it and generate the rule. See the [Rule Authoring Guide](src/main/resources/docs/knowledge-base/rule-guide.md) for the full Custom-Pattern Catalog.

## Skills

EasyApi ships an external skill that lets your favourite AI coding assistant (Trae, Cursor, Cline, Continue, etc.) author EasyApi rule files using the same knowledge base as the built-in assistant.

### Install the skill

```bash
npx skills add tangcent/easy-api -g -y
```

This installs the [`easy-api-assistant`](skills/easy-api-assistant/SKILL.md) skill globally. Once installed, your AI assistant will automatically invoke it when you ask to add or modify EasyApi rules.

### Two approaches to AI-assisted rule authoring

| Approach | Where it runs | Best for |
|----------|---------------|----------|
| **Built-in Rules-tab Chat / Magic** | Inside IntelliJ (Settings → EasyApi → Rules → Chat / Magic) | Users who want everything inside IntelliJ; the agent can call PSI tools to inspect the project. |
| **External skill** | Any AI coding assistant with file access | Users already invested in an external AI workflow; the assistant uses its own file/PSI access. |

The built-in assistant reads [`docs/knowledge-base/rule-guide.md`](src/main/resources/docs/knowledge-base/rule-guide.md) from the plugin, while the external skill bundles its own copy (`skills/easy-api-assistant/docs/rule-guide.md`) — the repo file isn't available after `npx skills add`, which publishes only the `skills/easy-api-assistant/` folder. The two copies are kept in sync, so both approaches produce consistent rule content.

## Development

### Prerequisites

- JDK 17 or higher
- IntelliJ IDEA 2025.2 or higher

### Build & Run

```bash
# Run an IDEA instance with the plugin installed
./gradlew runIde

# Run all tests
./gradlew clean test
```

### Compatibility

| JDK | IDE | Status |
|-----|-----|--------|
| 17 | 2025.2.1 | ✓ |

### Architecture

The plugin follows a layered architecture:

```mermaid
graph TB
    IDE["IDE Integration Layer<br/>(Actions, Dashboard, Line Markers, Search)"]
    Export["Export Layer<br/>(ExportOrchestrator → ClassExporter → ApiExporter)"]
    Core["Core Services<br/>(RuleEngine, ConfigReader, ApiIndex, HttpClient)"]
    PSI["PSI Analysis<br/>(TypeResolver, DocHelper, AnnotationHelper)"]

    IDE --> Export
    Export --> Core
    Core --> PSI
```

- **ClassExporter** — Extracts `ApiEndpoint` models from PSI classes (Spring MVC, JAX-RS, Feign, gRPC)
- **ApiExporter** — Converts `ApiEndpoint` models to output formats (Markdown, Postman, cURL, HTTP Client)
- **ExportOrchestrator** — Coordinates the full export pipeline from scanning to output
- **ApiIndex** — Caches discovered endpoints for fast search and dashboard access
- **RuleEngine** — Evaluates rule expressions to customize parsing behavior
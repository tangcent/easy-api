# EasyApi Settings Guide

This guide documents every setting in **Settings → EasyApi**. Settings are organized into tabs: General, Postman, HTTP, Intelligent, Extensions, AI, Other, gRPC, Hoppscotch (Beta), and Environments. Each section below covers one tab, listing every field with its label, default value, effect, and the underlying `Settings` property name.

> **Tip:** Project-level settings override application-level settings. Use the gear icon in the settings dialog to switch scope.

---

## Table of Contents

1. [General](#general)
2. [Postman](#postman)
3. [HTTP](#http)
4. [Intelligent](#intelligent)
5. [Extensions](#extensions)
6. [Rules](#rules)
7. [AI](#ai)
8. [gRPC](#grpc)
9. [Hoppscotch (Beta)](#hoppscotch-beta)
10. [Other](#other)
11. [Environments](#environments)

---

## General

Framework recognition and output formatting.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| Enable Feign | `false` | Recognize Spring Cloud Feign clients as API endpoints | `feignEnable` |
| Enable JAX-RS | `true` | Recognize JAX-RS `@Path` / `@GET` / `@POST` annotations | `jaxrsEnable` |
| Enable Actuator | `false` | Recognize Spring Boot Actuator endpoints | `actuatorEnable` |
| Auto Scan Enabled | `true` | Automatically scan the project for API endpoints | `autoScanEnabled` |
| Concurrent Scan | `false` | Use parallel scanning for faster discovery | `concurrentScanEnabled` |
| Output Demo | `true` | Include demo/example values in exports | `outputDemo` |
| Output Charset | `UTF-8` | Character encoding for exported files | `outputCharset` |
| Log Level | `100` (SILENT) | Console verbosity. Lower = more verbose. `100`=SILENT, `40`=ERROR, `30`=WARN, `20`=INFO, `10`=DEBUG, `0`=TRACE | `logLevel` |
| Gutter Icon | `true` | Show gutter icons for API endpoints in the editor | `gutterIconEnabled` |
| Switch Notice | `true` | Show a notification when switching settings scope | `switchNotice` |

The General tab also includes **Cache Management** (project and global cache size display with Clear buttons) and a **Repositories** editor for gRPC artifact resolution paths (Maven Local, Gradle Cache, or Custom).

---

## Postman

Postman collection export configuration.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| Postman Token | *(empty)* | Postman API token for collection upload | `postmanToken` |
| Postman Workspace | *(empty)* | Target workspace ID for Postman uploads | `postmanWorkspace` |
| Postman Export Mode | `CREATE_NEW` | Export mode: `CREATE_NEW`, `UPDATE`, or `OVERWRITE` | `postmanExportMode` |
| Postman Collections | *(empty)* | Comma-separated collection IDs for update mode | `postmanCollections` |
| Build Example | `true` | Include example responses in the collection | `postmanBuildExample` |
| Wrap Collection | `false` | Wrap endpoints in a top-level folder | `wrapCollection` |
| Auto Merge Script | `false` | Automatically merge pre/post scripts | `autoMergeScript` |
| JSON5 Format Type | `EXAMPLE_ONLY` | JSON5 body format: `EXAMPLE_ONLY` or `SCHEMA_AND_EXAMPLE` | `postmanJson5FormatType` |

---

## HTTP

HTTP client behavior for API calls (remote config fetch, AI provider calls, channel uploads).

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| HTTP Client | `APACHE` | HTTP client implementation: `APACHE` or `OKHTTP` | `httpClient` |
| HTTP Timeout | `30` (seconds) | Timeout for HTTP requests | `httpTimeOut` |
| Unsafe SSL | `false` | Skip SSL certificate verification | `unsafeSsl` |

---

## Intelligent

Smart inference and auto-detection.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| Query Expanded | `true` | Expand query parameters in the exported tree | `queryExpanded` |
| Form Expanded | `true` | Expand form parameters in the exported tree | `formExpanded` |
| Infer Return Main | `true` | Extract the inner type of wrapper types (e.g., `Mono<T>` → `T`) | `inferReturnMain` |
| Enable URL Templating | `true` | Use RFC 6570 URI template syntax for path variables in exported URLs | `enableUrlTemplating` |
| Path Multi | `ALL` | Controls multi-path handling mode (`ALL`, `FIRST`, `LAST`) | `pathMulti` |
| Enum Field Auto Infer | `false` | Automatically infer enum field values for ambiguous references | `enumFieldAutoInferEnabled` |

---

## Extensions

Plugin extension codes for custom behavior.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| Extension Configs | *(default codes)* | Extension configuration codes (comma-separated) | `extensionConfigs` |

---

## Rules

Rule file management. EasyApi 3.0 discovers rule files by **folder**, not by
an explicit list. The tab has three sub-tabs — **Project**,
**Global**, **Remote** — and a bottom action bar with **Chat**, **Magic**, and
**Help** buttons that host the inline AI assistant.

### Project sub-tab

Lists every regular file in `<project>/.easyapi/` (editable: add / edit /
rename / remove). Below it, a read-only list shows **legacy**
`.easy.api.config*` files discovered by walking up from the project root
(toggle Enabled only — no add / remove). The Enabled checkbox round-trips
through `Settings.disabledAutoRuleFiles`.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| `.easyapi/` files | *(empty)* | Discovered automatically from the folder. Add creates a new file; Remove deletes it from disk. | *(folder-based — no property)* |
| Disabled Auto Rule Files | *(empty)* | Array of auto-detected / `.easyapi/` file paths the user has unchecked. | `disabledAutoRuleFiles` |

### Global sub-tab

Lists every regular file in `~/.easyapi/` (editable: add / edit / rename /
remove). The Enabled checkbox round-trips through
`Settings.disabledGlobalRuleFiles`.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| `~/.easyapi/` files | *(empty)* | Discovered automatically from the folder. | *(folder-based — no property)* |
| Disabled Global Rule Files | *(empty)* | Array of global file paths the user has unchecked. | `disabledGlobalRuleFiles` |

### Remote sub-tab

Remote configuration sources (URLs that return rule/config content).

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| Remote Config | *(empty)* | Array of remote config URLs | `remoteConfig` |

### Bottom action bar

- **Chat** — reveals the inline AI chat panel. Type a request in natural
  language; the assistant reads your rules, reasons, and proposes content.
- **Magic** — runs a built-in "review and improve" instruction that also asks
  the assistant to detect custom framework patterns that lack a rule.
- **Help** — opens the knowledge-base overview (`docs/knowledge-base/README.md`)
  in the editor. The file is copied to the project cache directory first.

---

## AI

Dedicated tab for AI provider configuration (rule-authoring assistant).
Formerly the **Other (AI Assistant)** tab; promoted to a top-level tab in the
3.0 rework.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| AI Provider | `OPENAI` | Provider: `OPENAI`, `ANTHROPIC`, `GEMINI`, `OLLAMA`, `AZURE_OPENAI`, or `CUSTOM` | `aiProvider` |
| Base URL | *(provider default)* | API base URL. Auto-filled when the provider changes. | `aiBaseUrl` |
| API Key | *(stored in PasswordSafe)* | API key for the provider. Stored securely in IntelliJ's `PasswordSafe`. Required for `OPENAI`, `ANTHROPIC`, `GEMINI`, `AZURE_OPENAI`. Not required for `OLLAMA`. | *(not in Settings — stored in PasswordSafe)* |
| Model | *(provider default)* | Model name (e.g., `gpt-4`, `claude-3-5-sonnet`, `llama3`) | `aiModel` |
| Request Timeout | `60` (seconds) | Per-request timeout for AI API calls | `aiRequestTimeoutSec` |
| Max Requests | `100` | The maximum number of requests to allow per-turn when using an agent. When the limit is reached, will ask to confirm to continue. | `aiMaxRequests` |
| Context Window | *(provider default)* | Model context window in tokens. Used to derive how much conversation history the agent keeps. | `aiContextWindow` |

### Test Connection button

Validates the current configuration by sending a minimal test request to the provider. The button is disabled while the test is running and shows "Testing…". Results are surfaced as a notification balloon (success or failure). The test uses the **on-screen** field values, not persisted settings — so you can verify before clicking Apply/OK.

### Auto-detect button

Attempts to auto-detect the AI provider from the environment (checks for `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `OLLAMA_HOST`, etc.).

---

## gRPC

gRPC support configuration.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| Enable gRPC | `true` | Recognize gRPC service definitions | `grpcEnable` |
| gRPC Call Enabled | `false` | Enable gRPC call execution from the dashboard | `grpcCallEnabled` |
| Artifact Configs | *(empty)* | Array of protobuf artifact configuration strings | `grpcArtifactConfigs` |
| Additional Jars | *(empty)* | Array of additional JAR paths for gRPC stub resolution | `grpcAdditionalJars` |
| Repositories | *(empty)* | Array of repository URLs for artifact resolution | `grpcRepositories` |

> Repositories can also be managed from the **General** tab's Repositories editor.

---

## Hoppscotch (Beta)

Hoppscotch export configuration (Beta feature).

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| Server URL | `https://hoppscotch.io` | Hoppscotch server URL (use a custom URL for self-hosted instances) | `hoppscotchServerUrl` |
| Backend URL | *(empty)* | Backend API URL for self-hosted instances (e.g., `http://localhost:3170/v1`). Leave empty for cloud (hoppscotch.io). | `hoppscotchBackendUrl` |
| Manual Token | *(empty)* | Hoppscotch access token (fallback when browser login is not available) | `hoppscotchToken` |
| Refresh Token | *(empty)* | Hoppscotch refresh token (captured automatically during browser login) | `hoppscotchRefreshToken` |

### Login / Logout buttons

The **Login to Hoppscotch (Beta)** button opens a browser flow to authenticate and capture the access token. The **Logout** button clears the stored token. The token status is displayed inline.

---

## Other

Settings import/export and plugin info. This tab has no editable properties — it provides:

- **Import Settings** — load settings from a JSON file.
- **Export Settings** — save the current settings to a JSON file.
- Plugin version info.

---

## Environments

Environment variable management for export and script execution.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| Project Environments | *(empty)* | Project-scoped environment definitions (JSON) | `projectEnvironments` |
| Global Environments | *(empty)* | Application-scoped environment definitions (JSON) | `globalEnvironments` |

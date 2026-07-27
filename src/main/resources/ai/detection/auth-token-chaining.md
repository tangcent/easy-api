---
id: auth-token-chaining
title: Auth token chaining (login producer + secured consumer)
cue: a login/token endpoint whose response carries a token, plus secured controllers needing a Bearer header
---

A login/token endpoint whose response carries a token, plus secured
controllers needing a `Bearer` (or custom) header. The producer/consumer
split is the key insight — a script attached to the login endpoint stores
the token, and a header attached to the secured endpoints reads it. This
is the most common auth workflow pattern; it spans both the request side
(header injection on secured endpoints) and the response side (token
extraction from the login response).

## Detection signals

- A **producer endpoint**: a login/token/refresh endpoint whose response
  body carries a token field. Look for method names like `login`,
  `authenticate`, `token`, `refresh`, `oauth`, `accessToken`; or paths
  like `/login`, `/auth`, `/oauth/token`, `/api/token`.
- A **consumer scope**: secured controllers distinguished by:
  - A security annotation (`@PreAuthorize`, `@Secured`, `@RolesAllowed`,
    custom `@RequiresAuth`), OR
  - A security filter/interceptor reading `Authorization` headers, OR
  - A Spring Security configuration (`@EnableWebSecurity`,
    `@EnableResourceServer`, `SecurityFilterChain` bean) that protects
    URL patterns.
- The consumer side needs a `Bearer ${token}` (or custom) header; the
  producer side needs a script that extracts the token from the response
  and stores it for subsequent requests.

## Discovery

- `list_project_endpoints` to find login/token/refresh endpoints; filter
  by path/name patterns.
- `find_classes_by_annotation` for security annotations
  (`@PreAuthorize`, `@Secured`, `@RolesAllowed`, `@RequiresApiToken`,
  custom auth annotations).
- `find_classes_by_supertype` for auth filters/interceptors (see the
  `spring-filters-interceptors` / `jaxrs-filters` detections for the
  supertype lists).
- `get_psi_method_info` on the producer to confirm the token field name
  in the response body.

## Perceive → reason → act

- **Perceive.** Run the discovery tools above. For the producer,
  `get_psi_method_info` with `detail="full"` to read the response shape
  and identify the token field name (commonly `token`, `accessToken`,
  `access_token`, `idToken`). For the consumer scope, identify the
  annotation or filter that marks secured endpoints.
  `get_existing_rules_for_key` for `method.additional.header`,
  `postman.test`, `postman.prerequest`, `http.call.after` to avoid
  duplicates.
- **Reason.** Confirm the producer/consumer split. If the token field is
  ambiguous (multiple `*token*` keys) or the consumer scope is unclear,
  call `ask_clarification` with concrete options — do not guess. Reuse
  an existing env-var name when one is already referenced in the
  project's rules — resolve it from the rule files (e.g. grep `${...}`
  out of the existing `method.additional.header` values returned by
  `get_existing_rules_for_key`), not from the Environments panel.
  Default to `Authorization` when no existing rule references a token
  env var.
- **Act.** Propose the full bundle in one `propose_rule_content` call
  (filename like `auth-chaining.properties`):
  - **Producer side** (when Postman is enabled): a `postman.test` rule
    scoped to the login endpoint that extracts the token from the
    response and stores it via `pm.environment.set("token",
    pm.response.json().accessToken)`.
  - **Consumer side**: a `method.additional.header` rule scoped to the
    secured endpoints that injects `Authorization: Bearer ${token}`.
  Never propose half a bundle.

## Bundle integrity (CRITICAL)

Workflow rules that form a chain (login-script + consumer-header) MUST be
proposed together in a single `propose_rule_content` call. Proposing half
a chain is forbidden — a consumer header that references a token no
script stores is a silent bug.

## No hardcoded secrets

Every credential in a workflow rule is an env-var reference
(`${Authorization}`, `${appSecret}`, `${apiKey}`). Never emit a literal
token, key, or password in rule content.

## Script-context isolation (CRITICAL)

`postman.prerequest` and `postman.test` rule values MUST be **literal
scripts** (NO `groovy:` prefix). A `groovy:` prefix routes the value to
`Jsr223ScriptParser` at export time, where `pm` is NOT bound — the script
throws and the failure is silently swallowed, so no script lands in the
Postman collection.

## Fetch the full recipe

Fetch the full recipe on demand via `get_plugin_doc` with
`name="rule-guide"` (the "Workflow Patterns" → "Auth token chaining"
section). Do NOT reproduce the table from memory — the canonical doc
carries detection signals, complete `key[filter]=value` lines, and
env-var-reuse notes.

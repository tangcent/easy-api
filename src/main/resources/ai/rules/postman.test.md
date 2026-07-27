---
id: postman.test
key: postman.test
title: Postman test script
cue: JavaScript assertion attached to an endpoint, fired AFTER the response
channel: postman
---

## When to use

`postman.test` fires AFTER the response is received. Use it to:

- Read `pm.response` and assert on status / body fields.
- `pm.environment.set("token", …)` to extract a token from the response
  for use by subsequent requests (auth-token-chaining producer side).

## Script-context isolation (CRITICAL — silent-failure trap)

`postman.test` rule values MUST be **literal scripts** (NO `groovy:`
prefix). A `groovy:` prefix routes the value to `Jsr223ScriptParser` at
export time, where `pm` is NOT bound — the script throws and the
failure is **silently swallowed**, so no script lands in the Postman
collection.

Conversely, `http.call.before` / `http.call.after` rule values MUST use
the `groovy:` prefix (they run in `Jsr223ScriptParser`, where `pm` is
NOT available — use `session.set(...)` / `localStorage.set(...)` for
storage, NEVER `pm.environment.set(...)`).

## postman.test vs postman.prerequest (#1 mistake)

`postman.test` fires AFTER the response (read `pm.response`,
`pm.environment.set` a token). `postman.prerequest` fires BEFORE the
request (inject headers, compute signatures, mutate `pm.request`).
Swapping them is the most common workflow-rule error: a token extracted
in `prerequest` reads the PREVIOUS response (or none); a header injected
in `test` lands after the request has gone out.

## No hardcoded secrets

Every credential in a workflow rule is an env-var reference
(`${Authorization}`, `${appSecret}`, `${apiKey}`). Never emit a literal
token, key, or password in rule content.

## Bundle integrity (CRITICAL)

Workflow rules that form a chain (login-script + consumer-header) MUST
be proposed together in a single `propose_rule_content` call. Proposing
half a chain is forbidden — a consumer header that references a token no
script stores is a silent bug.

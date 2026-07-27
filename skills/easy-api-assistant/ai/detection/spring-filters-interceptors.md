---
id: spring-filters-interceptors
title: Spring filters, interceptors, web filters
cue: classes extending OncePerRequestFilter / HandlerInterceptor / WebFilter that mutate request or response headers
framework: springmvc
---

EasyApi understands Spring MVC's standard request flow out of the box —
`@RequestMapping`, `@GetMapping`, `@RequestBody`, `@PathVariable`, etc. need
no rules. But servlet filters / interceptors / web filters that mutate the
request or response contract **invisibly** (auth, signing, header injection,
response wrapping) are not visible from the controller signature. Without a
rule, the exported documentation will be missing those headers.

## Detection signals

Components declared by inheritance (the Spring Boot default) or by
annotation that mutate the request/response contract:

- **Servlet filters** extending `OncePerRequestFilter`
  (`org.springframework.web.filter.OncePerRequestFilter`) or implementing
  `jakarta.servlet.Filter` / `javax.servlet.Filter`.
- **Interceptors** implementing `HandlerInterceptor`
  (`org.springframework.web.servlet.HandlerInterceptor`) or
  `AsyncHandlerInterceptor`.
- **Web filters** annotated `@WebFilter`
  (`jakarta.servlet.annotation.WebFilter` /
  `javax.servlet.annotation.WebFilter`).

The mutation to look for: `request.setHeader(...)`,
`request.addHeader(...)`, `response.setHeader(...)`,
`exchange.getResponse().getHeaders().add(...)`. Not every filter/interceptor
mutates the contract — `MappedInterceptor` for logging or metrics does not
need a rule.

## Discovery

Probe BOTH declaration styles — the Spring Boot default is inheritance, but
annotation-declared filters exist too. Either may be present.

- `find_classes_by_supertype` for:
  - `org.springframework.web.filter.OncePerRequestFilter`
  - `org.springframework.web.servlet.HandlerInterceptor`
  - `jakarta.servlet.Filter`
  - `javax.servlet.Filter`
- `find_classes_by_annotation` for:
  - `jakarta.servlet.annotation.WebFilter`
  - `javax.servlet.annotation.WebFilter`

Both discovery tools can return empty — probe the annotation AND the
supertype before concluding "none found".

## Perceive → reason → act

- **Perceive.** Run both discovery tools (above). For each hit, call
  `get_psi_method_info` with `detail="full"` on the filtering method
  (`doFilterInternal`, `doFilter`, `preHandle`, `postHandle`) to read which
  headers are set/added and on which side (request vs response).
  `get_existing_rules_for_key` for `method.additional.header` and
  `method.additional.response.header` to avoid duplicates.
- **Reason.** For each mutation, decide the rule:
  - Request header set on every request → `method.additional.header`
    (filtered to the protected path via a `groovy:` filter if the filter
    scopes to specific URLs).
  - Response header set on every response → `method.additional.response.header`.
  - Header value computed from a script (e.g. UUID, timestamp) →
    `method.additional.header` with a `groovy:` value-block that returns
    a JSON object whose `value` is the script expression.
  - Header value computed from an HMAC/signature → defer to the
    `hmac-signing` detection recipe (bundle signer + consumer together).
- **Act.** Propose the `method.additional.header` / `method.additional.response.header`
  rule(s) in one `propose_rule_content` call (filename like
  `spring-filters.rules`). Bundle integrity applies — if the same filter
  injects both a request and a response header, both rules go in the same
  proposal.

## Confirm the contract changed

Confirm a hit with `get_psi_class_info` + `get_psi_method_info`, then ask:
*does it change the request/response contract invisibly?* If yes, apply the
catalog recipe. If no (logging, metrics, tracing-only), no rule is needed.

## Fetch the full recipe

Fetch the full recipe on demand via `get_plugin_doc` with
`name="rule-guide"` (the "Spring Patterns" → "Filters & interceptors"
section). Do NOT reproduce the table from memory.

---
id: jaxrs-filters
title: JAX-RS ContainerRequestFilter / ContainerResponseFilter
cue: ContainerRequestFilter or ContainerResponseFilter implementations that mutate headers invisibly
framework: jaxrs
---

EasyApi understands JAX-RS's standard annotations (`@Path`, `@GET`,
`@QueryParam`, `@PathParam`, `@HeaderParam`, `@FormParam`, `@BeanParam`)
out of the box. But `ContainerRequestFilter` and `ContainerResponseFilter`
implementations mutate the request/response contract **invisibly** (auth,
signing, header injection) — the contract is not visible from the resource
method signature. Without a rule, the exported documentation will be
missing those headers.

## Detection signals

Classes implementing:
- `javax.ws.rs.container.ContainerRequestFilter` /
  `jakarta.ws.rs.container.ContainerRequestFilter` — runs before the
  resource method; can mutate request headers, abort with a response, etc.
- `javax.ws.rs.container.ContainerResponseFilter` /
  `jakarta.ws.rs.container.ContainerResponseFilter` — runs after the
  resource method; can mutate response headers.

The filter may be:
- **Global** (annotated `@Provider`) — applies to all resources.
- **Name-bound** (annotated with a custom `@NameBinding` meta-annotation) —
  applies only to resources/methods annotated with the binding annotation.

The mutation to look for: `requestContext.getHeaders().add(...)`,
`requestContext.getHeaders().putSingle(...)`,
`responseContext.getHeaders().add(...)`. Not every filter mutates the
contract — a CORS filter adding `Access-Control-Allow-*` headers may not
need a rule if those headers are purely informational.

## Supertype to probe

Search via `find_classes_by_supertype` for:
- `javax.ws.rs.container.ContainerRequestFilter`
- `jakarta.ws.rs.container.ContainerRequestFilter`
- `javax.ws.rs.container.ContainerResponseFilter`
- `jakarta.ws.rs.container.ContainerResponseFilter`

Also `find_classes_by_annotation` for `@Provider`
(`javax.ws.rs.ext.Provider` / `jakarta.ws.rs.ext.Provider`) and any custom
`@NameBinding` annotations — name-bound filters only apply to
resources/methods carrying the binding annotation, so the rule's filter
must scope to those resources.

## Perceive → reason → act

- **Perceive.** Run both discovery tools (above). For each hit, call
  `get_psi_method_info` with `detail="full"` on the `filter` method to read
  which headers are added/put. Determine the binding scope:
  - `@Provider` (no `@NameBinding`) → global → rule applies to all endpoints.
  - `@NameBinding`-annotated → name-bound → rule applies only to endpoints
    whose resource class or method carries the binding annotation; use a
    `groovy:` filter on the `method.additional.header` rule that checks
    `it.hasAnn("com.example.MyBinding")`.
  - `get_existing_rules_for_key` for `method.additional.header` and
    `method.additional.response.header` to avoid duplicates.
- **Reason.** For each mutation, decide the rule:
  - Request header set on every request → `method.additional.header`
    (filtered to the bound resources if name-bound).
  - Response header set on every response →
    `method.additional.response.header` (filtered similarly).
  - Auth filter that aborts with 401 on missing credentials →
    `method.additional.header` for the credential header; defer to the
    `static-auth` or `auth-token-chaining` recipe for the auth side.
- **Act.** Propose the `method.additional.header` /
  `method.additional.response.header` rule(s) in one
  `propose_rule_content` call (filename like `jaxrs-filters.rules`).

## Bundle integrity

If a name-bound filter and its binding annotation are both needed, propose
them together — a rule referencing a binding annotation that no filter
applies is dead code.

## Fetch the full recipe

Fetch the full recipe on demand via `get_plugin_doc` with
`name="rule-guide"` (the "JAX-RS Patterns" → "Filters" section). Do NOT
reproduce the table from memory.

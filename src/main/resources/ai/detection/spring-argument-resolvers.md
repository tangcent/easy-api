---
id: spring-argument-resolvers
title: Spring handler method argument resolvers
cue: HandlerMethodArgumentResolver implementations that inject hidden params (principal, request context, current user)
framework: springmvc
---

EasyApi understands Spring MVC's standard parameter annotations
(`@RequestParam`, `@PathVariable`, `@RequestBody`, `@RequestHeader`,
`@CookieValue`, `@ModelAttribute`) out of the box. But
`HandlerMethodArgumentResolver` implementations inject method parameters
that the plugin cannot see from the method signature alone — the resolver
reads the request (principal, header, cookie, session) and supplies the
argument. Without a rule, the exported documentation will be **missing
those hidden parameters entirely**.

## Detection signals

Classes implementing `HandlerMethodArgumentResolver`
(`org.springframework.web.method.support.HandlerMethodArgumentResolver`)
or extending `HandlerMethodArgumentResolverSupport`. The resolver's
`supportsParameter` method declares which parameter types it handles; the
`resolveArgument` method reads the request and supplies the value.

Common examples:
- `@CurrentUser` → resolves `Principal`/`User` from the security context
- `@RequestContext` → resolves the request/response/context object
- `@TenantId` → resolves the tenant from a header or JWT claim
- `@Pageable` → resolves pagination params (Spring Data's `Pageable` is
  handled by `PageableHandlerMethodArgumentResolver` — this is **standard**,
  no rule needed unless customized)

## Supertype to probe

Argument resolvers implement
`org.springframework.web.method.support.HandlerMethodArgumentResolver`.

Annotation-declared resolvers are rare — the supertype probe is the
primary path. Search via `find_classes_by_supertype` for the interface FQN
above.

## Perceive → reason → act

- **Perceive.** `find_classes_by_supertype` for
  `org.springframework.web.method.support.HandlerMethodArgumentResolver`.
  For each hit, `get_psi_method_info` with `detail="full"` on
  `supportsParameter` to read which parameter types are supported, and on
  `resolveArgument` to read how the value is sourced (header? cookie?
  principal? session?). Then `find_classes_by_annotation` for the custom
  annotation the resolver supports (e.g. `@CurrentUser`) to find the
  controllers consuming it. `get_existing_rules_for_key` for
  `method.additional.param` and `param.ignore` to avoid duplicates.
- **Reason.** For each resolver, identify:
  - The parameter name (from the annotation value or the parameter name).
  - The parameter type (from `supportsParameter`).
  - The source (request header? cookie? principal? session?).
  - Whether the parameter should be **documented** (`method.additional.param`)
    or **hidden** (`param.ignore`). Hidden parameters are those that are
    framework-internal (e.g. `HttpServletRequest`, `Principal`) — the
    consumer doesn't supply them. Documented parameters are those the
    consumer must supply (e.g. a tenant header extracted by the resolver).
- **Act.** Propose the `method.additional.param` rule(s) for documented
  hidden params, or `param.ignore` rules for framework-internal params, in
  one `propose_rule_content` call (filename like `argument-resolvers.rules`).

## Bundle integrity

If a resolver supports multiple parameter types, propose one rule per type
in the same call. Do not propose a `method.additional.param` for a
parameter that should be `param.ignore`d — the two are mutually exclusive.

## Fetch the full recipe

Fetch the full recipe on demand via `get_plugin_doc` with
`name="rule-guide"` (the "Spring Patterns" → "Argument resolvers" section).
Do NOT reproduce the table from memory.

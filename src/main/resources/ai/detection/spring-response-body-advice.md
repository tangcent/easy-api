---
id: spring-response-body-advice
title: Spring ResponseBodyAdvice (response envelope wrapping)
cue: ResponseBodyAdvice implementations or @RestControllerAdvice that wrap responses in a standard envelope like {code, message, data}
framework: springmvc
---

EasyApi understands Spring MVC's standard return type wrappers
(`ResponseEntity<T>`, `Mono<T>`, `Flux<T>`, `Optional<T>`) out of the box —
those are unwrapped automatically. But `ResponseBodyAdvice`
implementations wrap the response body in a **standard envelope** (e.g.
`{code, message, data}`) that the plugin cannot detect from the
controller's return type alone. Without a rule, the exported documentation
will show the raw controller return type and **miss the envelope**.

## Detection signals

Two declaration styles, both common — probe both:

- **Interface implementation.** Classes implementing `ResponseBodyAdvice`
  (`org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice`).
  The `beforeBodyWrite` method is where the wrapping happens.
- **Annotation declaration.** Classes annotated `@RestControllerAdvice`
  (`org.springframework.web.bind.annotation.RestControllerAdvice`) that
  also implement `ResponseBodyAdvice`. The `@RestControllerAdvice` alone
  is for exception handlers (see the `spring-controller-advice` detection);
  the `ResponseBodyAdvice` interface is what makes it a body wrapper.

The wrapper typically wraps in a `Result<T>`, `ApiResponse<T>`, `R<T>`,
`Response<T>`, or similar envelope with fields like `code`, `message`,
`data`, `success`, `timestamp`.

## Supertype / annotation to probe

- `find_classes_by_supertype` for
  `org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice`.
- `find_classes_by_annotation` for
  `org.springframework.web.bind.annotation.RestControllerAdvice`.

Both can return empty — probe the annotation AND the supertype before
concluding "none found". A class can be both `@RestControllerAdvice` AND
implement `ResponseBodyAdvice` — that is the common combined form.

## Perceive → reason → act

- **Perceive.** Run both discovery tools (above). For each hit, call
  `get_psi_class_info` to read the envelope class (the type returned by
  `beforeBodyWrite`); `get_psi_method_info` with `detail="full"` on
  `beforeBodyWrite` to read the wrapping logic (which field holds the
  payload? which field holds the status code? which field holds the
  message?). `get_existing_rules_for_key` for `method.return.main` and
  `json.rule.convert` to avoid duplicates.
- **Reason.** Identify the envelope class FQN and the inner payload field
  path. The rule is `method.return.main=<field path>` (e.g. `data` or
  `result.data`). If the envelope is generic (`Result<T>`), also propose a
  `json.rule.convert[#regex:com\.example\.Result<(.*?)>]=${1}` rule so the
  exporter unwraps the generic — this avoids the envelope appearing as the
  response type in the docs. Wrap the `json.rule.convert` lines in
  `###set resolveProperty = false … true` so the `${1}` capture group is
  not treated as a property placeholder.
- **Act.** Propose the `method.return.main` + optional `json.rule.convert`
  bundle in one `propose_rule_content` call (filename like
  `response-envelope.rules`).

## Bundle integrity

The `method.return.main` rule and the `json.rule.convert` rule for the same
envelope MUST be proposed together — the convert unwraps the generic type
so the exporter sees the inner payload, and `method.return.main` tells the
exporter which field of the unwrapped object is the actual response. Half
a bundle produces confusing docs.

## Fetch the full recipe

Fetch the full recipe on demand via `get_plugin_doc` with
`name="rule-guide"` (the "Spring Patterns" → "Response body advice"
section). Do NOT reproduce the table from memory.

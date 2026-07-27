---
id: spring-controller-advice
title: Spring @RestControllerAdvice exception handlers
cue: "@RestControllerAdvice with @ExceptionHandler methods that change the error response shape for secured endpoints"
framework: springmvc
---

EasyApi documents the success response (2xx) from a controller's return type
out of the box. But `@RestControllerAdvice` classes with `@ExceptionHandler`
methods define the **error response shape** (4xx, 5xx) that the plugin
cannot associate with the controllers that throw those exceptions. Without
a rule, the exported documentation will miss the error response contract.

## Detection signals

- Classes annotated `@RestControllerAdvice`
  (`org.springframework.web.bind.annotation.RestControllerAdvice`) or
  `@ControllerAdvice`
  (`org.springframework.web.bind.annotation.ControllerAdvice`).
- Methods annotated `@ExceptionHandler`
  (`org.springframework.web.bind.annotation.ExceptionHandler`) within those
  classes. The annotation's `value` attribute names the exception type(s)
  handled; the method's return type is the error response shape.

The error response is typically an envelope like `ErrorResponse`,
`ApiError`, `Result<Void>`, or a `ResponseEntity<ErrorDetail>` carrying
fields like `code`, `message`, `timestamp`, `path`.

## Supertype / annotation to probe

- `find_classes_by_annotation` for:
  - `org.springframework.web.bind.annotation.RestControllerAdvice`
  - `org.springframework.web.bind.annotation.ControllerAdvice`
- For each hit, `get_psi_class_info` to enumerate the `@ExceptionHandler`
  methods and their return types.

Note: a class can be both `@RestControllerAdvice` AND implement
`ResponseBodyAdvice` — the `ResponseBodyAdvice` interface is the body
wrapper case (see the `spring-response-body-advice` detection). This
detection is specifically about `@ExceptionHandler` methods.

## Perceive → reason → act

- **Perceive.** `find_classes_by_annotation` for the advice annotations
  above. For each hit, `get_psi_class_info` to list the
  `@ExceptionHandler` methods; `get_psi_method_info` with `detail="full"`
  on each handler to read the exception type handled, the HTTP status
  returned (from `@ResponseStatus` or `ResponseEntity`), and the error
  response shape. `find_classes_by_supertype` for the exception types to
  find the controllers that throw them. `get_existing_rules_for_key` for
  `method.additional.response.header` and `method.doc` to avoid duplicates.
- **Reason.** For each handler, identify:
  - The exception type (from `@ExceptionHandler(MyException.class)`).
  - The HTTP status (from `@ResponseStatus(code=...)` or the
    `ResponseEntity` status).
  - The error response shape (the handler's return type).
  - The controllers that throw this exception (via
    `find_classes_by_supertype` for the exception type, then scanning
    their methods for `throw new MyException(...)`).
- **Act.** The error response contract is documented by annotating the
  affected endpoints. Propose:
  - `method.doc[@ann]` rules that append the error response shape to the
    method documentation of affected endpoints (filter by the throwing
    annotation or the controller class), OR
  - `method.additional.response.header` rules if the handler also sets
    response headers (e.g. `WWW-Authenticate` on 401).
  Bundle the rules in one `propose_rule_content` call (filename like
  `error-responses.rules`).

## Bundle integrity

If the same `@RestControllerAdvice` handles multiple exceptions, propose
one rule per exception→endpoint mapping in the same call. Do not propose
half a mapping — an error response documented without the triggering
condition is noise.

## Fetch the full recipe

Fetch the full recipe on demand via `get_plugin_doc` with
`name="rule-guide"` (the "Spring Patterns" → "Exception handlers" section).
Do NOT reproduce the table from memory.

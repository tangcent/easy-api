---
id: custom-framework
title: Custom framework (proprietary API annotations)
cue: project uses its own annotations (not Spring/JAX-RS/Feign/gRPC) to mark API classes and methods
---

EasyApi understands standard HTTP frameworks (Spring MVC, WebFlux, JAX-RS,
Feign, gRPC) out of the box — those need no rules. When a project uses
**proprietary annotations** to mark API classes/methods (e.g. `@MyApi`,
`@MyEndpoint`, `@RpcService`), the standard recognizers will not find them
and no endpoints will be exported. The Custom framework + a `custom.*` rule
set is the escape hatch.

## Detection signals

- Endpoints exist in the project (the user has controllers/services) but
  `list_project_endpoints` returns empty or far fewer than expected.
- Classes annotated with project-specific annotations whose names suggest
  API exposure (`@Api`, `@Endpoint`, `@Rpc`, `@RestApi`, `@Controller`-like
  but not the Spring/JAX-RS FQNs).
- Methods annotated with project-specific HTTP-verb-like annotations
  (`@Get`, `@Post`, `@RequestMapping`-like but not the standard FQN).

## Discovery

`find_classes_by_annotation` is the primary tool. Probe candidate annotation
FQNs mentioned by the user; if none, scan for short annotation names that
look API-related (`Api`, `Endpoint`, `Rpc`, `Rest`) and confirm with
`get_psi_class_info`. The Custom framework recognizer
(`frameworkName = "Custom"`) is **disabled by default** — it only fires
after the user enables it in settings AND a `custom.class.is.api` rule
recognizes the class.

## Perceive → reason → act

- **Perceive.** `find_classes_by_annotation` for the proprietary annotation
  FQN(s); `get_psi_class_info` on a sample class to read its methods and
  method-level annotations; `get_psi_method_info` with `detail="full"` on
  a sample method to read parameters and return type; `get_existing_rules_for_key`
  for `custom.class.is.api`, `custom.method.is.api`, `custom.http.method`,
  `custom.path` to avoid duplicates.
- **Reason.** Identify the class-level annotation that marks an API class
  and the method-level annotation that marks an API method. For methods,
  determine the HTTP verb (from the annotation name or attribute) and the
  path (from an attribute or convention). For parameters, determine the
  binding (body / form / path / header / cookie) — if the project uses
  standard Spring/JAX-RS binding annotations on parameters of the custom
  controller, reuse them; otherwise default `param.http.type=query`.
- **Act.** Propose the full `custom.*` rule bundle in ONE
  `propose_rule_content` call (filename like `custom-framework.rules`).
  A minimal bundle includes:
  - `custom.class.is.api` — class recognition
  - `custom.method.is.api` — method recognition
  - `custom.http.method` — HTTP verb extraction
  - `custom.path` — class base path + method path
  - `method.default.http.method=GET` — fallback when verb cannot be resolved
  - Parameter binding rules as needed (`custom.param.as.json.body`,
    `custom.param.as.form.body`, `custom.param.as.path.var`,
    `custom.param.as.cookie`, `param.http.type`)

## Bundle integrity (CRITICAL)

The `custom.*` rules form a coordinated set — a `custom.method.is.api`
without a `custom.http.method` leaves the verb unresolved; a `custom.path`
without `custom.class.is.api` never fires. Propose the full bundle in one
`propose_rule_content` call. Never propose half a custom-framework set.

## Reference

The bundled test fixture `custom-spring-reference.rules` re-implements
Spring MVC recognition entirely with `custom.*` rules — it is both a
learning aid and the completeness proof. Fetch it via `get_plugin_doc` with
`name="rule-guide"` (the "Custom Framework" section) for the canonical
recipe. Do NOT reproduce the table from memory.

## Confirm the gap first

Before proposing, confirm the project is NOT already covered by a standard
framework — Spring MVC / WebFlux / JAX-RS / Feign / gRPC endpoints with
standard annotations are detected automatically. Only write `custom.*`
rules for annotations the standard recognizers do not handle.

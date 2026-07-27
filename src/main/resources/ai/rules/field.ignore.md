---
id: field.ignore
key: field.ignore
title: Field ignore
cue: boolean rule that strips a field from the exported model — never generate blanket patterns automatically
---

## CRITICAL — never generate blanket field-ignore rules

Do NOT generate `field.ignore` rules based on field-name patterns
like `.*password.*`, `.*secret.*`, `.*token.*`. These fields are
often a **legitimate part of the API definition** — a login endpoint
requires `password`, an OAuth endpoint requires `clientSecret`, a
token-refresh endpoint requires `refreshToken`. Stripping them
silently breaks the exported documentation.

Sensitive-field handling is a **project policy** decision, not a
code-detection decision. If the user explicitly asks for it, you may
add it — but never invent it on your own, and always warn the user
that it may hide fields that some endpoints legitimately require.

## Filter examples

When the user has explicitly asked to ignore a specific field on a
specific class:

```
field.ignore[$class:com.example.dto.UserResponse]=password
```

Multiple fields on the same class means multiple lines (the key uses
default merge semantics — boolean rules do not merge, so each line is
its own rule).

## Check existing rules first

Before proposing, call `get_existing_rules_for_key` for `field.ignore`.
If an equivalent rule already exists, do NOT write a duplicate.

---
id: method.additional.response.header
key: method.additional.response.header
title: Additional response header
cue: JSON object header attached to every matching endpoint's response
---

## Value format

The value is a single-line JSON object:

```json
{"name":"X-RateLimit-Remaining","value":"100","desc":"","required":false}
```

One object per line — multiple headers means multiple
`method.additional.response.header=…` lines (the key uses
`StringRuleMode.MERGE`).

## Filter examples

Filter the rule to a class:

```
method.additional.response.header[$class:com.example.web.UserController]={"name":"X-Trace-Id","value":"${traceId}","desc":"trace","required":false}
```

Filter by annotation:

```
method.additional.response.header[@com.example.Tracked]={"name":"X-Trace-Id","value":"${traceId}","desc":"trace","required":false}
```

## Prefer groovy value-blocks for complex conditional logic

When a filter expression grows long — multiple `&&`/`||`, multiple
exclusions, or nested method calls — switch to the **groovy value-block**
form (see the `method.additional.header` recipe for the full example).
The script must `return` the value (string) or `return null` to skip.

Rules of thumb:
- **≤ 1 condition** → inline `key[filter]=value` is fine.
- **≥ 2 conditions or exclusions** → use a groovy value-block.

## Check existing rules first

Before proposing, call `get_existing_rules_for_key` for
`method.additional.response.header`. If an equivalent rule already
exists, do NOT write a duplicate.

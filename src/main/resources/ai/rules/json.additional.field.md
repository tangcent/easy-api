---
id: json.additional.field
key: json.additional.field
title: Additional JSON field
cue: JSON object field injected into the serialized response (e.g. computed totals, envelope metadata)
---

## Value format

The value is a single-line JSON object describing the additional field
to inject into the serialized response:

```json
{"name":"totalAmount","value":"${total}","desc":"computed","type":"number"}
```

One object per line — multiple fields means multiple
`json.additional.field=…` lines (the key uses `StringRuleMode.MERGE`).

## Filter examples

Filter the rule to a class:

```
json.additional.field[$class:com.example.dto.OrderResponse]={"name":"signature","value":"${sig}","desc":"HMAC","type":"string"}
```

Filter by annotation:

```
json.additional.field[@com.example.Signed]={"name":"signature","value":"${sig}","desc":"HMAC","type":"string"}
```

## Prefer groovy value-blocks for complex conditional logic

When a filter expression grows long — multiple `&&`/`||`, multiple
exclusions, or nested method calls — switch to the **groovy value-block**
form (see the `method.additional.header` recipe for the full example).
The script must `return` the value (string) or `return null` to skip.

## No hardcoded secrets

Every credential in a field value is an env-var reference
(`${appSecret}`, `${apiKey}`). Never emit a literal token, key, or
password in rule content.

## Check existing rules first

Before proposing, call `get_existing_rules_for_key` for
`json.additional.field`. If an equivalent rule already exists, do NOT
write a duplicate.

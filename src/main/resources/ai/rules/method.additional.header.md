---
id: method.additional.header
key: method.additional.header
title: Additional request header
cue: JSON object header attached to every matching endpoint's request
---

## Value format

The value is a single-line JSON object:

```json
{"name":"Authorization","value":"Bearer ${token}","desc":"JWT","required":true}
```

One object per line — multiple headers means multiple
`method.additional.header=…` lines (the key uses `StringRuleMode.MERGE`).

## Filter examples

Filter the rule to a class:

```
method.additional.header[$class:com.example.web.UserController]={"name":"Authorization","value":"Bearer ${token}","desc":"JWT","required":true}
```

Filter by annotation:

```
method.additional.header[@com.example.RequiresAuth]={"name":"Authorization","value":"Bearer ${token}","desc":"JWT","required":true}
```

## Prefer groovy value-blocks for complex conditional logic

When a filter expression grows long — multiple `&&`/`||`, multiple
exclusions, or nested method calls — switch to the **groovy value-block**
form: the value itself is a multi-line groovy script that returns the
value when the condition holds, or `null` when it doesn't.

**Bad (unreadable single-line filter):**
```
method.additional.header[groovy: it.containingClass().name().startsWith("com.example.merchant.") && !it.containingClass().name().equals("com.example.merchant.AuthController") && !it.containingClass().name().equals("com.example.merchant.PublicController")]={"name":"Authorization","value":"Bearer ${token}","desc":"JWT","required":true}
```

**Good (multi-line groovy value-block):**
```
method.additional.header=groovy:```
def cls = it.containingClass()?.name()
if (cls?.startsWith("com.example.merchant.")
    && cls != "com.example.merchant.AuthController"
    && cls != "com.example.merchant.PublicController") {
    return '{"name":"Authorization","value":"Bearer ${token}","desc":"JWT","required":true}'
}
return null
```
```

Rules of thumb:
- **≤ 1 condition** → inline `key[filter]=value` is fine.
- **≥ 2 conditions or exclusions** → use a groovy value-block.
- The script must `return` the value (string) or `return null` to skip.
- Keep the script readable: use local variables, one condition per line.

## No hardcoded secrets

Every credential in a header value is an env-var reference
(`${Authorization}`, `${token}`, `${apiKey}`). Never emit a literal
token, key, or password in rule content.

## Check existing rules first

Before proposing, call `get_existing_rules_for_key` for
`method.additional.header` (or pass `keys` as an array to check multiple
keys in one request). If an equivalent rule already exists, do NOT write
a duplicate.

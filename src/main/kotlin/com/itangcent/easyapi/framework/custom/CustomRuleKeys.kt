package com.itangcent.easyapi.framework.custom

import com.itangcent.easyapi.core.rule.RuleKey

/**
 * Custom-framework-specific rule keys.
 *
 * Framework-specific keys live in the framework package (DAG compliance —
 * `core.rule.RuleKeys` owns the shared keys; frameworks own their own).
 * Mirrors the `HoppscotchRuleKeys` / `OpenApiRuleKeys` pattern — `object`
 * with `RuleKey.string(...)` / `RuleKey.boolean(...)` vals;
 * `RuleKey.collectFrom(CustomRuleKeys)` enumerates them via reflection.
 *
 * Consumed by [CustomApiRecognizer.ruleKeys] so [com.itangcent.easyapi.core.rule.RuleKeyRegistry]
 * surfaces them in `list_rule_keys` and `RuleProposalValidator` accepts them.
 *
 * The 13 extraction keys drive class/method recognition, HTTP method, path,
 * and parameter binding. The 5 framework-scoped lifecycle `EventKey`s
 * (`custom.class.parse.before/after`, `custom.method.parse.before/after`,
 * `custom.export.after`) are fired by [CustomClassExporter] alongside the
 * shared hooks (`api.class.parse.before/after`, etc.) because the rule
 * evaluation context does not expose the framework name to user-written
 * rules — these custom hooks give users a framework-scoped surface for
 * side-effect rules that must only run during Custom-framework extraction.
 *
 * @see com.itangcent.easyapi.core.rule.RuleKeys for general (shared) rule keys
 */
object CustomRuleKeys {

    /** `true` if the class is a Custom API class. */
    val CUSTOM_CLASS_IS_API = RuleKey.boolean("custom.class.is.api")

    /** `true` if the method is a Custom endpoint. */
    val CUSTOM_METHOD_IS_API = RuleKey.boolean("custom.method.is.api")

    /** HTTP verb (`GET`/`POST`/...) for the method. */
    val CUSTOM_HTTP_METHOD = RuleKey.string("custom.http.method")

    /** Base path (class) / method path (method) — context-sensitive. */
    val CUSTOM_PATH = RuleKey.string("custom.path")

    /** `true` → bind parameter as request body. */
    val CUSTOM_PARAM_AS_JSON_BODY = RuleKey.boolean("custom.param.as.json.body")

    /** `true` → bind parameter as form field. */
    val CUSTOM_PARAM_AS_FORM_BODY = RuleKey.boolean("custom.param.as.form.body")

    /** `true` → bind parameter as path variable. */
    val CUSTOM_PARAM_AS_PATH_VAR = RuleKey.boolean("custom.param.as.path.var")

    /** `true` → bind parameter as cookie. */
    val CUSTOM_PARAM_AS_COOKIE = RuleKey.boolean("custom.param.as.cookie")

    /** Path-variable name override. */
    val CUSTOM_PARAM_PATH_VAR = RuleKey.string("custom.param.path.var")

    /** Header name (when binding=header). */
    val CUSTOM_PARAM_HEADER = RuleKey.string("custom.param.header")

    /** Cookie name (when binding=cookie). */
    val CUSTOM_PARAM_COOKIE = RuleKey.string("custom.param.cookie")

    /** Cookie value override. */
    val CUSTOM_PARAM_COOKIE_VALUE = RuleKey.string("custom.param.cookie.value")

    /** Parameter name override (query/form). */
    val CUSTOM_PARAM_NAME = RuleKey.string("custom.param.name")

    // ── Framework-scoped lifecycle hooks ─────────────────────────
    // Fired by CustomClassExporter alongside the corresponding shared hooks
    // (shared first, then custom). See design Decision 8.

    /** Fired after `api.class.parse.before` — before parsing a class. */
    val CUSTOM_CLASS_PARSE_BEFORE = RuleKey.event("custom.class.parse.before")

    /** Fired after `api.class.parse.after` — after parsing a class. */
    val CUSTOM_CLASS_PARSE_AFTER = RuleKey.event("custom.class.parse.after")

    /** Fired after `api.method.parse.before` — before parsing a method. */
    val CUSTOM_METHOD_PARSE_BEFORE = RuleKey.event("custom.method.parse.before")

    /** Fired after `api.method.parse.after` — after parsing a method. */
    val CUSTOM_METHOD_PARSE_AFTER = RuleKey.event("custom.method.parse.after")

    /** Fired after `export.after` — after building each endpoint. */
    val CUSTOM_EXPORT_AFTER = RuleKey.event("custom.export.after")
}

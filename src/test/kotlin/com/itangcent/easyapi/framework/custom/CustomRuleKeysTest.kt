package com.itangcent.easyapi.framework.custom

import com.itangcent.easyapi.core.rule.RuleKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the 18 `custom.*` rule keys (13 extraction + 5 framework-scoped
 * lifecycle) are declared in [CustomRuleKeys] (the framework's own package —
 * mirrors the `HoppscotchRuleKeys` / `OpenApiRuleKeys` pattern) with the
 * correct name and type, and that [RuleKey.collectFrom] surfaces them (this
 * is what `CustomApiRecognizer.ruleKeys()` returns, and what
 * [com.itangcent.easyapi.core.rule.RuleKeyRegistry] consumes via the framework
 * source so `list_rule_keys` and `RuleProposalValidator` accept them).
 */
class CustomRuleKeysTest {

    @Test
    fun customClassIsApi() {
        val key = CustomRuleKeys.CUSTOM_CLASS_IS_API
        assertEquals("custom.class.is.api", key.name)
        assertTrue("Expected BooleanKey, got ${key::class.simpleName}", key is RuleKey.BooleanKey)
    }

    @Test
    fun customMethodIsApi() {
        val key = CustomRuleKeys.CUSTOM_METHOD_IS_API
        assertEquals("custom.method.is.api", key.name)
        assertTrue(key is RuleKey.BooleanKey)
    }

    @Test
    fun customHttpMethod() {
        val key = CustomRuleKeys.CUSTOM_HTTP_METHOD
        assertEquals("custom.http.method", key.name)
        assertTrue("Expected StringKey, got ${key::class.simpleName}", key is RuleKey.StringKey)
    }

    @Test
    fun customPath() {
        val key = CustomRuleKeys.CUSTOM_PATH
        assertEquals("custom.path", key.name)
        assertTrue(key is RuleKey.StringKey)
    }

    @Test
    fun customParamAsJsonBody() {
        val key = CustomRuleKeys.CUSTOM_PARAM_AS_JSON_BODY
        assertEquals("custom.param.as.json.body", key.name)
        assertTrue(key is RuleKey.BooleanKey)
    }

    @Test
    fun customParamAsFormBody() {
        val key = CustomRuleKeys.CUSTOM_PARAM_AS_FORM_BODY
        assertEquals("custom.param.as.form.body", key.name)
        assertTrue(key is RuleKey.BooleanKey)
    }

    @Test
    fun customParamAsPathVar() {
        val key = CustomRuleKeys.CUSTOM_PARAM_AS_PATH_VAR
        assertEquals("custom.param.as.path.var", key.name)
        assertTrue(key is RuleKey.BooleanKey)
    }

    @Test
    fun customParamAsCookie() {
        val key = CustomRuleKeys.CUSTOM_PARAM_AS_COOKIE
        assertEquals("custom.param.as.cookie", key.name)
        assertTrue(key is RuleKey.BooleanKey)
    }

    @Test
    fun customParamPathVar() {
        val key = CustomRuleKeys.CUSTOM_PARAM_PATH_VAR
        assertEquals("custom.param.path.var", key.name)
        assertTrue(key is RuleKey.StringKey)
    }

    @Test
    fun customParamHeader() {
        val key = CustomRuleKeys.CUSTOM_PARAM_HEADER
        assertEquals("custom.param.header", key.name)
        assertTrue(key is RuleKey.StringKey)
    }

    @Test
    fun customParamCookie() {
        val key = CustomRuleKeys.CUSTOM_PARAM_COOKIE
        assertEquals("custom.param.cookie", key.name)
        assertTrue(key is RuleKey.StringKey)
    }

    @Test
    fun customParamCookieValue() {
        val key = CustomRuleKeys.CUSTOM_PARAM_COOKIE_VALUE
        assertEquals("custom.param.cookie.value", key.name)
        assertTrue(key is RuleKey.StringKey)
    }

    @Test
    fun customParamName() {
        val key = CustomRuleKeys.CUSTOM_PARAM_NAME
        assertEquals("custom.param.name", key.name)
        assertTrue(key is RuleKey.StringKey)
    }

    // ── Framework-scoped lifecycle keys (5) ──────────────────────

    @Test
    fun customClassParseBefore() {
        val key = CustomRuleKeys.CUSTOM_CLASS_PARSE_BEFORE
        assertEquals("custom.class.parse.before", key.name)
        assertTrue("Expected EventKey, got ${key::class.simpleName}", key is RuleKey.EventKey)
    }

    @Test
    fun customClassParseAfter() {
        val key = CustomRuleKeys.CUSTOM_CLASS_PARSE_AFTER
        assertEquals("custom.class.parse.after", key.name)
        assertTrue(key is RuleKey.EventKey)
    }

    @Test
    fun customMethodParseBefore() {
        val key = CustomRuleKeys.CUSTOM_METHOD_PARSE_BEFORE
        assertEquals("custom.method.parse.before", key.name)
        assertTrue(key is RuleKey.EventKey)
    }

    @Test
    fun customMethodParseAfter() {
        val key = CustomRuleKeys.CUSTOM_METHOD_PARSE_AFTER
        assertEquals("custom.method.parse.after", key.name)
        assertTrue(key is RuleKey.EventKey)
    }

    @Test
    fun customExportAfter() {
        val key = CustomRuleKeys.CUSTOM_EXPORT_AFTER
        assertEquals("custom.export.after", key.name)
        assertTrue(key is RuleKey.EventKey)
    }

    @Test
    fun allCustomKeysAreCollectedByRuleKeyCollectFrom() {
        val collected = RuleKey.collectFrom(CustomRuleKeys).map { it.name }.toSet()
        val expected = setOf(
            "custom.class.is.api",
            "custom.method.is.api",
            "custom.http.method",
            "custom.path",
            "custom.param.as.json.body",
            "custom.param.as.form.body",
            "custom.param.as.path.var",
            "custom.param.as.cookie",
            "custom.param.path.var",
            "custom.param.header",
            "custom.param.cookie",
            "custom.param.cookie.value",
            "custom.param.name",
            "custom.class.parse.before",
            "custom.class.parse.after",
            "custom.method.parse.before",
            "custom.method.parse.after",
            "custom.export.after"
        )
        val missing = expected - collected
        assertTrue(
            "RuleKey.collectFrom(CustomRuleKeys) is missing: $missing",
            missing.isEmpty()
        )
    }

    @Test
    fun customRuleKeysAreExposedByRecognizerRuleKeys() {
        // ApiClassRecognizer.ruleKeys() is what RuleKeyRegistry consumes; verify
        // the recognizer surfaces all 18 keys so list_rule_keys / validator see them.
        val recognizer = CustomApiRecognizer()
        val collected = recognizer.ruleKeys().map { it.name }.toSet()
        val expected = setOf(
            "custom.class.is.api",
            "custom.method.is.api",
            "custom.http.method",
            "custom.path",
            "custom.param.as.json.body",
            "custom.param.as.form.body",
            "custom.param.as.path.var",
            "custom.param.as.cookie",
            "custom.param.path.var",
            "custom.param.header",
            "custom.param.cookie",
            "custom.param.cookie.value",
            "custom.param.name",
            "custom.class.parse.before",
            "custom.class.parse.after",
            "custom.method.parse.before",
            "custom.method.parse.after",
            "custom.export.after"
        )
        val missing = expected - collected
        assertTrue(
            "CustomApiRecognizer.ruleKeys() is missing: $missing",
            missing.isEmpty()
        )
    }
}

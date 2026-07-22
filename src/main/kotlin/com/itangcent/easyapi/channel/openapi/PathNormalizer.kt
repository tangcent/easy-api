package com.itangcent.easyapi.channel.openapi

/**
 * Normalizes EasyApi HTTP paths into OpenAPI Path Template syntax.
 *
 * Handles two Spring-specific input forms:
 * - **Spring colon form:** `/users/:id` → `/users/{id}` (only when the `:`
 *   appears at the start of a segment).
 * - **Spring regex form:** `/users/{id:\\d+}` → `/users/{id}` (the trailing
 *   `:regex` portion inside braces is stripped; nested braces inside the
 *   regex are tolerated, e.g. quantifiers `{8,}` in `{id:[0-9a-f]{8,}}`).
 *
 * After the two rewrites, the path is validated against the OAS Path
 * Template grammar (segments separated by `/`; each segment is either a
 * literal or a `{name}` parameter with a name matching
 * `[a-zA-Z_][a-zA-Z0-9_.-]*`). Paths that still violate the grammar
 * (e.g. unclosed braces, query strings, empty parameter names) return
 * `null` so the caller can skip-with-warn.
 *
 * The object is stateless and pure.
 */
object PathNormalizer {

    /**
     * Matches Spring's `:name` colon form, but only when the colon is at the
     * start of a segment — either at the start of the string or immediately
     * after a `/`. Group 1 captures the segment-start anchor (empty string
     * if at start of input, or `/`); group 2 captures the parameter name.
     */
    private val COLON_FORM = Regex("""(^|/):([a-zA-Z_][a-zA-Z0-9_]*)""")

    /**
     * Validates a path parameter name (the substring between `{` and the
     * first `:` or `}`). Allows letters, digits, underscore, dot, and dash;
     * must start with a letter or underscore.
     */
    private val PARAM_NAME = Regex("^[a-zA-Z_][a-zA-Z0-9_.-]*$")

    /**
     * Validates that the post-normalization path is a valid OAS 3.0.3 Path
     * Template: a leading `/`, followed by zero or more segments separated
     * by `/`, where each segment is either a literal (`[^/{}?#]+`) or a
     * path parameter (`{[a-zA-Z0-9_.-]+}`). The root path `/` is also valid.
     */
    private val VALID_PATH_TEMPLATE =
        Regex("""^/(?:\{[a-zA-Z0-9_.-]+\}|[^/{}?#]+)(?:/(?:\{[a-zA-Z0-9_.-]+\}|[^/{}?#]+))*$|^/$""")

    /**
     * Normalizes [path] to OAS Path Template syntax, or returns `null` if the
     * post-normalization path still violates the OAS grammar.
     */
    fun normalize(path: String): String? {
        if (path.isBlank()) return null

        // Step 1: Spring colon form `:name` → `{name}` (segment-start only)
        val step1 = COLON_FORM.replace(path) { match ->
            "${match.groupValues[1]}{${match.groupValues[2]}}"
        }

        // Step 2: Spring regex form `{name:regex}` → `{name}` (per segment).
        // Splitting by `/` lets each segment be inspected in isolation —
        // nested braces inside the regex (e.g. quantifiers `{8,}`) don't
        // confuse a balanced-brace matcher because the segment boundaries
        // are already known.
        val step2 = step1.split("/").joinToString("/") { seg ->
            stripRegexFromSegment(seg)
        }

        // Step 3: Validate the post-normalization path against the OAS grammar
        if (!VALID_PATH_TEMPLATE.matches(step2)) return null
        return step2
    }

    /**
     * If [seg] is a `{name:regex}` segment, returns `{name}`; otherwise
     * returns [seg] unchanged.
     */
    private fun stripRegexFromSegment(seg: String): String {
        if (!seg.startsWith("{") || !seg.endsWith("}") || seg.length < 4) return seg
        val colonIdx = seg.indexOf(':')
        if (colonIdx !in 1 until seg.length - 1) return seg
        val name = seg.substring(1, colonIdx)
        if (!PARAM_NAME.matches(name)) return seg
        return "{$name}"
    }
}

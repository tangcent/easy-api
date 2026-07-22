package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.psi.model.FieldModel
import com.itangcent.easyapi.core.psi.model.FieldOption
import com.itangcent.easyapi.core.psi.model.ObjectModel

/**
 * Cycle-safe converter from EasyApi's [ObjectModel] to an OAS [SchemaObject]
 * tree.
 *
 * The converter is pure and project-independent — it accumulates named
 * schemas in an internal [components] map and exposes them via
 * [buildComponents]. Cycles in the input [ObjectModel.Object] graph are
 * detected via an immutable `seenIds` set passed by copy;
 * on revisit, the converter returns a `$ref` rather than recursing infinitely.
 *
 * Schema-name resolution:
 * - When a [nameHint] is provided, the FIRST occurrence registers the full
 *   inlined schema in `components.schemas[<name>]` and the caller receives a
 *   `$ref` to it. Subsequent calls with the same [nameHint] and matching
 *   shape return the existing `$ref`.
 * - When the shape differs, a `_2`, `_3`, … suffix is appended and a `warn`
 *   is logged.
 * - When no [nameHint] is available (anonymous body), the schema is inlined
 *   at the call site; only on recursion (cycle) does it get a
 *   `GeneratedSchemaN` name and a `$ref`.
 */
class OpenApiSchemaConverter : IdeaLog {

    /** Mutable accumulator of named schemas → emitted as `components.schemas`. */
    private val components: LinkedHashMap<String, SchemaObject> = linkedMapOf()

    /** Tracks the resolved schema name (if any) for each visited [ObjectModel.Object] id. */
    private val idToName: MutableMap<Int, String> = mutableMapOf()

    /** Counter for `GeneratedSchemaN` synthesis. */
    private var generatedCounter: Int = 0

    /**
     * Converts [model] into a [SchemaObject]. Returns `null` if [model] is null.
     *
     * - [nameHint]: hint for the schema name (typically the simple class name
     *   from `responseType` / `bodyAttr`); package and generic args are
     *   stripped. When provided, the schema is registered under that name in
     *   `components.schemas` and the caller receives a `$ref`.
     * - [seenIds]: immutable-by-copy cycle-detection set. Each
     *   recursive call passes `LinkedHashSet(seenIds).apply { add(model.id) }`.
     */
    fun convert(
        model: ObjectModel?,
        nameHint: String? = null,
        seenIds: LinkedHashSet<Int> = linkedSetOf(),
    ): SchemaObject? {
        if (model == null) return null

        return when (model) {
            is ObjectModel.Object -> {
                // Cycle: the same Object.id has been visited along the current path.
                if (model.id in seenIds) {
                    val name = idToName[model.id] ?: allocateGeneratedName(model, seenIds)
                    return SchemaObject(`$ref` = "#/components/schemas/$name")
                }

                // Same model already registered under a name → reuse $ref.
                val existingName = idToName[model.id]
                if (existingName != null) {
                    return SchemaObject(`$ref` = "#/components/schemas/$existingName")
                }

                // First visit.
                if (nameHint != null) {
                    val simpleName = stripPackageAndGenerics(nameHint)
                    registerWithShapeCheck(model, simpleName, seenIds)
                } else {
                    // Anonymous first visit — inline at the call site.
                    buildObjectSchema(model, LinkedHashSet(seenIds).apply { add(model.id) })
                }
            }
            is ObjectModel.Single -> primitiveSchema(model.type)
            is ObjectModel.Array -> SchemaObject(
                type = "array",
                items = convert(model.item, nameHint = null, seenIds = seenIds),
            )
            is ObjectModel.MapModel -> SchemaObject(
                type = "object",
                additionalProperties = convert(model.valueType, nameHint = null, seenIds = seenIds),
            )
        }
    }

    /** Returns the accumulated named schemas as a [ComponentsObject]. */
    fun buildComponents(): ComponentsObject =
        ComponentsObject(schemas = components.takeIf { it.isNotEmpty() })

    // ─── Name resolution ──────────────────────────────────────────

    /**
     * Registers [model] under [simpleName], after checking the existing entry
     * (if any) for a shape match. On shape mismatch, finds the first free
     * `_N` suffix (N ≥ 2), logs a `warn`, and registers under the new name.
     *
     * Sets `idToName[model.id]` BEFORE building the schema so recursive
     * cycle-$refs pick up the correct name. If the name turns out to clash,
     * `idToName` is updated to the resolved `_N` name and the schema is
     * rebuilt so its internal $refs point to the correct name.
     */
    private fun registerWithShapeCheck(
        model: ObjectModel.Object,
        simpleName: String,
        seenIds: LinkedHashSet<Int>,
    ): SchemaObject {
        // Tentatively assign so cycle recursion picks up `simpleName`.
        idToName[model.id] = simpleName
        val nextSeen = LinkedHashSet(seenIds).apply { add(model.id) }
        val newSchema = buildObjectSchema(model, nextSeen)

        val existing = components[simpleName]
        if (existing == null) {
            // Name is free → register and return $ref.
            components[simpleName] = newSchema
            return SchemaObject(`$ref` = "#/components/schemas/$simpleName")
        }
        if (existing == newSchema) {
            // Same shape → reuse. Discard the freshly-built schema.
            return SchemaObject(`$ref` = "#/components/schemas/$simpleName")
        }

        // Shape mismatch → find first free `_N` slot.
        var n = 2
        while (true) {
            val candidate = "${simpleName}_$n"
            val existingN = components[candidate]
            if (existingN == null) {
                // Free slot — rebuild with the correct name so cycle $refs
                // point to `candidate`, not the tentative `simpleName`.
                idToName[model.id] = candidate
                val rebuilt = buildObjectSchema(model, nextSeen)
                components[candidate] = rebuilt
                LOG.warn("Schema name clash: '$simpleName' already used with a different shape; registering as '$candidate'")
                return SchemaObject(`$ref` = "#/components/schemas/$candidate")
            }
            if (existingN == newSchema) {
                // `_N` entry matches → reuse.
                idToName[model.id] = candidate
                return SchemaObject(`$ref` = "#/components/schemas/$candidate")
            }
            n++
        }
    }

    /**
     * Allocates a fresh `GeneratedSchemaN` name for an anonymous cyclic model.
     * Registers the inline schema under the new name so future
     * cycle-$refs resolve correctly.
     */
    private fun allocateGeneratedName(
        model: ObjectModel.Object,
        seenIds: LinkedHashSet<Int>,
    ): String {
        val name = "GeneratedSchema${++generatedCounter}"
        idToName[model.id] = name
        val nextSeen = LinkedHashSet(seenIds).apply { add(model.id) }
        components[name] = buildObjectSchema(model, nextSeen)
        return name
    }

    /**
     * Strips package prefix and generic args from [name].
     * `com.acme.Result<User>` → `Result`. `Result` → `Result`.
     */
    private fun stripPackageAndGenerics(name: String): String {
        val noGenerics = name.substringBefore('<').trim()
        val simple = noGenerics.substringAfterLast('.').trim()
        return simple.ifBlank { name }
    }

    // ─── Inline schema construction ──────────────────────────────────────────

    /**
     * Builds an inline [SchemaObject] for [model]. Recurses into fields via
     * [convert] so cycle detection applies. The caller must have added
     * [model.id] to [seenIds] before calling.
     */
    private fun buildObjectSchema(
        model: ObjectModel.Object,
        seenIds: LinkedHashSet<Int>,
    ): SchemaObject {
        val properties = linkedMapOf<String, SchemaObject>()
        val required = mutableListOf<String>()
        for ((fieldName, fieldModel) in model.fields) {
            val fieldSchema = convert(fieldModel.model, nameHint = null, seenIds = seenIds)
                ?: continue
            properties[fieldName] = enrichWithFieldMetadata(fieldSchema, fieldModel)
            if (fieldModel.required) required.add(fieldName)
        }
        return SchemaObject(
            type = "object",
            properties = properties.takeIf { it.isNotEmpty() },
            required = required.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * Applies field-level enrichments (comment → description,
     * options → enum + x-enumDescriptions, demo → example)
     * to [base]. Returns [base] unchanged if no enrichment applies; otherwise
     * returns a copy with the enriched fields populated.
     */
    private fun enrichWithFieldMetadata(base: SchemaObject, field: FieldModel): SchemaObject {
        if (field.comment == null && field.options.isNullOrEmpty() && field.demo == null) {
            return base
        }
        val description = field.comment ?: base.description
        val enumValues: List<Any>? = field.options
            ?.mapNotNull { it.value }
            ?.takeIf { it.isNotEmpty() }
            ?: base.enumValues
        val enumDescs: Map<String, String>? = field.options
            ?.mapNotNull { opt ->
                val v = opt.value ?: return@mapNotNull null
                opt.desc?.let { v.toString() to it }
            }
            ?.takeIf { it.isNotEmpty() }
            ?.let { pairs -> linkedMapOf<String, String>().apply { putAll(pairs) } }
            ?: base.xEnumDescriptions
        val example = field.demo ?: base.example
        return base.copy(
            description = description,
            enumValues = enumValues,
            xEnumDescriptions = enumDescs,
            example = example,
        )
    }

    /** Converts an option [value] of arbitrary type to its wire form. */
    private fun FieldOption.wireValue(): Any? = value

    // ─── Primitive type table ─────────────────────────────────────

    /**
     * Maps a Java/Kotlin type name to its OAS `(type, format)` pair. Match is
     * case-insensitive substring. Unknown types default to
     * `(string, null)`.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun primitiveSchema(type: String): SchemaObject {
        val t = type.lowercase()
        return when {
            // `byte[]` must be checked before `byte` (the latter → integer/int32).
            t.contains("byte[]") || t.contains("binary") ->
                SchemaObject(type = "string", format = "binary")

            // datetime-family → string/date-time. Checked before `date` so that
            // "datetime"/"date-time"/"zoneddatetime" all land here.
            t.contains("datetime") || t.contains("date-time") ||
                t.contains("timestamp") || t.contains("instant") ||
                t.contains("zoneddatetime") ->
                SchemaObject(type = "string", format = "date-time")

            // Plain `date` (not `date-time`) → string/date.
            t.contains("date") ->
                SchemaObject(type = "string", format = "date")

            t.contains("uuid") ->
                SchemaObject(type = "string", format = "uuid")

            // `char` / `character` → string/null. Must be checked before default.
            t.contains("char") ->
                SchemaObject(type = "string", format = null)

            // `long` must be checked before `int` (no substring overlap, but
            // explicit for clarity).
            t.contains("long") ->
                SchemaObject(type = "integer", format = "int64")

            t.contains("int") ->
                SchemaObject(type = "integer", format = "int32")

            t.contains("short") || t.contains("byte") ->
                SchemaObject(type = "integer", format = "int32")

            t.contains("float") ->
                SchemaObject(type = "number", format = "float")

            t.contains("double") || t.contains("decimal") || t.contains("number") ->
                SchemaObject(type = "number", format = "double")

            t.contains("boolean") ->
                SchemaObject(type = "boolean", format = null)

            // Explicit `string` and unknown types default to (string, null).
            else -> SchemaObject(type = "string", format = null)
        }
    }
}

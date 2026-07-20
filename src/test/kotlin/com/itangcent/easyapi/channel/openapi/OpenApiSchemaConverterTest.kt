package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.core.psi.model.FieldModel
import com.itangcent.easyapi.core.psi.model.FieldOption
import com.itangcent.easyapi.core.psi.model.ObjectModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [OpenApiSchemaConverter] — the cycle-safe `ObjectModel → OAS
 * Schema Object` converter.
 *
 * Constructs `ObjectModel` instances directly (no PSI) — the model is POJO.
 */
class OpenApiSchemaConverterTest {

    // ─── primitive type table ──────────────────────────────────────

    @Test
    fun primitiveStringMapsToStringWithNullFormat() {
        val schema = converter().convert(ObjectModel.single("string"))!!
        assertEquals("string", schema.type)
        assertNull(schema.format)
    }

    @Test
    fun primitiveIntMapsToIntegerInt32() {
        val schema = converter().convert(ObjectModel.single("int"))!!
        assertEquals("integer", schema.type)
        assertEquals("int32", schema.format)
    }

    @Test
    fun primitiveIntegerMapsToIntegerInt32() {
        val schema = converter().convert(ObjectModel.single("integer"))!!
        assertEquals("integer", schema.type)
        assertEquals("int32", schema.format)
    }

    @Test
    fun primitiveLongMapsToIntegerInt64() {
        val schema = converter().convert(ObjectModel.single("long"))!!
        assertEquals("integer", schema.type)
        assertEquals("int64", schema.format)
    }

    @Test
    fun primitiveShortMapsToIntegerInt32() {
        val schema = converter().convert(ObjectModel.single("short"))!!
        assertEquals("integer", schema.type)
        assertEquals("int32", schema.format)
    }

    @Test
    fun primitiveByteMapsToIntegerInt32() {
        val schema = converter().convert(ObjectModel.single("byte"))!!
        assertEquals("integer", schema.type)
        assertEquals("int32", schema.format)
    }

    @Test
    fun primitiveFloatMapsToNumberFloat() {
        val schema = converter().convert(ObjectModel.single("float"))!!
        assertEquals("number", schema.type)
        assertEquals("float", schema.format)
    }

    @Test
    fun primitiveDoubleMapsToNumberDouble() {
        val schema = converter().convert(ObjectModel.single("double"))!!
        assertEquals("number", schema.type)
        assertEquals("double", schema.format)
    }

    @Test
    fun primitiveDecimalMapsToNumberDouble() {
        val schema = converter().convert(ObjectModel.single("decimal"))!!
        assertEquals("number", schema.type)
        assertEquals("double", schema.format)
    }

    @Test
    fun primitiveBigdecimalMapsToNumberDouble() {
        val schema = converter().convert(ObjectModel.single("bigdecimal"))!!
        assertEquals("number", schema.type)
        assertEquals("double", schema.format)
    }

    @Test
    fun primitiveNumberMapsToNumberDouble() {
        val schema = converter().convert(ObjectModel.single("number"))!!
        assertEquals("number", schema.type)
        assertEquals("double", schema.format)
    }

    @Test
    fun primitiveBooleanMapsToBooleanWithNullFormat() {
        val schema = converter().convert(ObjectModel.single("boolean"))!!
        assertEquals("boolean", schema.type)
        assertNull(schema.format)
    }

    @Test
    fun primitiveDateMapsToStringDate() {
        val schema = converter().convert(ObjectModel.single("date"))!!
        assertEquals("string", schema.type)
        assertEquals("date", schema.format)
    }

    @Test
    fun primitiveDatetimeMapsToStringDateTime() {
        val schema = converter().convert(ObjectModel.single("datetime"))!!
        assertEquals("string", schema.type)
        assertEquals("date-time", schema.format)
    }

    @Test
    fun primitiveDateTimeWithHyphenMapsToStringDateTime() {
        val schema = converter().convert(ObjectModel.single("date-time"))!!
        assertEquals("string", schema.type)
        assertEquals("date-time", schema.format)
    }

    @Test
    fun primitiveTimestampMapsToStringDateTime() {
        val schema = converter().convert(ObjectModel.single("timestamp"))!!
        assertEquals("string", schema.type)
        assertEquals("date-time", schema.format)
    }

    @Test
    fun primitiveInstantMapsToStringDateTime() {
        val schema = converter().convert(ObjectModel.single("instant"))!!
        assertEquals("string", schema.type)
        assertEquals("date-time", schema.format)
    }

    @Test
    fun primitiveZoneddatetimeMapsToStringDateTime() {
        val schema = converter().convert(ObjectModel.single("zoneddatetime"))!!
        assertEquals("string", schema.type)
        assertEquals("date-time", schema.format)
    }

    @Test
    fun primitiveUuidMapsToStringUuid() {
        val schema = converter().convert(ObjectModel.single("uuid"))!!
        assertEquals("string", schema.type)
        assertEquals("uuid", schema.format)
    }

    @Test
    fun primitiveByteArrayMapsToStringBinary() {
        val schema = converter().convert(ObjectModel.single("byte[]"))!!
        assertEquals("string", schema.type)
        assertEquals("binary", schema.format)
    }

    @Test
    fun primitiveBinaryMapsToStringBinary() {
        val schema = converter().convert(ObjectModel.single("binary"))!!
        assertEquals("string", schema.type)
        assertEquals("binary", schema.format)
    }

    @Test
    fun primitiveCharMapsToStringWithNullFormat() {
        val schema = converter().convert(ObjectModel.single("char"))!!
        assertEquals("string", schema.type)
        assertNull(schema.format)
    }

    @Test
    fun primitiveCharacterMapsToStringWithNullFormat() {
        val schema = converter().convert(ObjectModel.single("character"))!!
        assertEquals("string", schema.type)
        assertNull(schema.format)
    }

    @Test
    fun primitiveUnknownTypeDefaultsToStringWithNullFormat() {
        val schema = converter().convert(ObjectModel.single("totallyUnknownThing"))!!
        assertEquals("string", schema.type)
        assertNull(schema.format)
    }

    @Test
    fun primitiveTypeMatchIsCaseInsensitive() {
        val schema = converter().convert(ObjectModel.single("INTEGER"))!!
        assertEquals("integer", schema.type)
        assertEquals("int32", schema.format)
    }

    // ─── Object with fields + required ────────────────────────────

    @Test
    fun objectMapsToObjectTypeWithPropertiesAndRequired() {
        val model = ObjectModel.Object(
            linkedMapOf(
                "id" to FieldModel(model = ObjectModel.single("long"), required = true),
                "name" to FieldModel(model = ObjectModel.single("string")),
            )
        )
        val schema = converter().convert(model)!!
        assertEquals("object", schema.type)
        val properties = schema.properties
        assertNotNull(properties)
        assertTrue(properties!!.containsKey("id"))
        assertTrue(properties.containsKey("name"))
        assertEquals(listOf("id"), schema.required)
        // Properties use LinkedHashMap to preserve insertion order
        assertEquals(listOf("id", "name"), properties.keys.toList())
    }

    // ─── Array ──────────────────────────────────────────────────────

    @Test
    fun arrayMapsToArrayTypeWithItemsDerivedFromElementModel() {
        val model = ObjectModel.Array(ObjectModel.single("int"))
        val schema = converter().convert(model)!!
        assertEquals("array", schema.type)
        val items = schema.items
        assertNotNull(items)
        assertEquals("integer", items!!.type)
        assertEquals("int32", items.format)
    }

    // ─── MapModel ──────────────────────────────────────────────────

    @Test
    fun mapModelMapsToObjectTypeWithAdditionalPropertiesFromValueType() {
        val model = ObjectModel.MapModel(
            keyType = ObjectModel.single("string"),
            valueType = ObjectModel.single("int"),
        )
        val schema = converter().convert(model)!!
        assertEquals("object", schema.type)
        val additional = schema.additionalProperties
        assertNotNull(additional)
        assertEquals("integer", additional!!.type)
        assertEquals("int32", additional.format)
    }

    // ─── field comment → description ───────────────────────────────

    @Test
    fun fieldCommentPopulatesSchemaDescription() {
        val model = ObjectModel.Object(
            linkedMapOf(
                "name" to FieldModel(
                    model = ObjectModel.single("string"),
                    comment = "User's display name",
                ),
            )
        )
        val schema = converter().convert(model)!!
        val fieldSchema = schema.properties!!["name"]!!
        assertEquals("User's display name", fieldSchema.description)
    }

    // ─── field options → enum + x-enumDescriptions ─────────────────

    @Test
    fun fieldOptionsWithoutDescriptionsPopulateEnumValuesOnly() {
        val model = ObjectModel.Object(
            linkedMapOf(
                "status" to FieldModel(
                    model = ObjectModel.single("string"),
                    options = listOf(
                        FieldOption(value = "ACTIVE"),
                        FieldOption(value = "INACTIVE"),
                    ),
                ),
            )
        )
        val schema = converter().convert(model)!!
        val fieldSchema = schema.properties!!["status"]!!
        assertEquals(listOf("ACTIVE", "INACTIVE"), fieldSchema.enumValues)
        assertNull(fieldSchema.xEnumDescriptions)
    }

    @Test
    fun fieldOptionsWithDescriptionsPopulateEnumValuesAndXEnumDescriptions() {
        val model = ObjectModel.Object(
            linkedMapOf(
                "status" to FieldModel(
                    model = ObjectModel.single("string"),
                    options = listOf(
                        FieldOption(value = "ACTIVE", desc = "Active user"),
                        FieldOption(value = "INACTIVE", desc = "Inactive user"),
                    ),
                ),
            )
        )
        val schema = converter().convert(model)!!
        val fieldSchema = schema.properties!!["status"]!!
        assertEquals(listOf("ACTIVE", "INACTIVE"), fieldSchema.enumValues)
        val enumDescs = fieldSchema.xEnumDescriptions
        assertNotNull(enumDescs)
        assertEquals("Active user", enumDescs!!["ACTIVE"])
        assertEquals("Inactive user", enumDescs["INACTIVE"])
    }

    // ─── field demo → example ──────────────────────────────────────

    @Test
    fun fieldDemoPopulatesSchemaExample() {
        val model = ObjectModel.Object(
            linkedMapOf(
                "name" to FieldModel(
                    model = ObjectModel.single("string"),
                    demo = "Alice",
                ),
            )
        )
        val schema = converter().convert(model)!!
        val fieldSchema = schema.properties!!["name"]!!
        assertEquals("Alice", fieldSchema.example)
    }

    // ─── cycle detection ───────────────────────────────

    @Test
    fun selfReferentialNodeReturnsDollarRefAndRegistersInComponents() {
        val node = buildSelfReferentialNode()
        val converter = converter()

        val schema = converter.convert(node, nameHint = "Node")!!

        // First use with nameHint returns a $ref (not an inline object).
        assertEquals("#/components/schemas/Node", schema.`$ref`)

        // components.schemas has Node registered.
        val components = converter.buildComponents()
        val schemas = components.schemas
        assertNotNull(schemas)
        assertTrue("Node" in schemas!!)
        val nodeSchema = schemas["Node"]!!
        assertEquals("object", nodeSchema.type)
        // The `next` field of Node is a $ref back to Node (cycle broken).
        val nextFieldSchema = nodeSchema.properties!!["next"]!!
        assertEquals("#/components/schemas/Node", nextFieldSchema.`$ref`)
    }

    @Test
    fun cycleDetectionTerminatesInFiniteTimeForDeepRecursion() {
        // Construct Node → next → Node → next → ... (cycle of length 1).
        // convert() must terminate, not StackOverflow.
        val node = buildSelfReferentialNode()
        val schema = converter().convert(node, nameHint = "Node")
        assertNotNull(schema)
    }

    // ─── schema-name resolution ────────────────────

    @Test
    fun nameHintPresentFirstUseRegistersAndReturnsDollarRef() {
        val model = ObjectModel.Object(
            linkedMapOf("id" to FieldModel(model = ObjectModel.single("long")))
        )
        val converter = converter()

        val schema = converter.convert(model, nameHint = "User")!!

        assertEquals("#/components/schemas/User", schema.`$ref`)
        val components = converter.buildComponents()
        assertTrue(components.schemas!!.containsKey("User"))
    }

    @Test
    fun nameHintPresentSecondUseSameShapeReturnsExistingDollarRefWithoutDuplicate() {
        val model1 = ObjectModel.Object(
            linkedMapOf("id" to FieldModel(model = ObjectModel.single("long")))
        )
        val model2 = ObjectModel.Object(
            linkedMapOf("id" to FieldModel(model = ObjectModel.single("long")))
        )
        val converter = converter()

        val first = converter.convert(model1, nameHint = "User")!!
        val second = converter.convert(model2, nameHint = "User")!!

        // Both calls return the same $ref — no _N suffix on second use.
        assertEquals("#/components/schemas/User", first.`$ref`)
        assertEquals("#/components/schemas/User", second.`$ref`)

        // Only one entry registered.
        val schemas = converter.buildComponents().schemas!!
        assertEquals(1, schemas.size)
        assertTrue(schemas.containsKey("User"))
    }

    @Test
    fun nameHintPresentSecondUseDifferentShapeAppendsUnderscoreTwoSuffix() {
        val model1 = ObjectModel.Object(
            linkedMapOf("id" to FieldModel(model = ObjectModel.single("long")))
        )
        val model2 = ObjectModel.Object(
            linkedMapOf("name" to FieldModel(model = ObjectModel.single("string")))
        )
        val converter = converter()

        val first = converter.convert(model1, nameHint = "User")!!
        val second = converter.convert(model2, nameHint = "User")!!

        assertEquals("#/components/schemas/User", first.`$ref`)
        // Different shape → _2 suffix (and a warn is logged).
        assertEquals("#/components/schemas/User_2", second.`$ref`)

        val schemas = converter.buildComponents().schemas!!
        assertTrue(schemas.containsKey("User"))
        assertTrue(schemas.containsKey("User_2"))
    }

    @Test
    fun noNameHintCyclicAnonymousModelUsesGeneratedSchemaN() {
        val anon1 = buildSelfReferentialNode()
        val anon2 = buildSelfReferentialNode()
        val converter = converter()

        val first = converter.convert(anon1, nameHint = null)!!
        val second = converter.convert(anon2, nameHint = null)!!

        // Anonymous first-use returns an inline schema (type=object), not a $ref.
        assertEquals("object", first.type)
        assertNull(first.`$ref`)
        // The `next` field is a $ref to GeneratedSchema1.
        val firstNext = first.properties!!["next"]!!
        assertEquals("#/components/schemas/GeneratedSchema1", firstNext.`$ref`)

        // Second anonymous cyclic model → GeneratedSchema2.
        assertEquals("object", second.type)
        val secondNext = second.properties!!["next"]!!
        assertEquals("#/components/schemas/GeneratedSchema2", secondNext.`$ref`)

        val schemas = converter.buildComponents().schemas!!
        assertTrue(schemas.containsKey("GeneratedSchema1"))
        assertTrue(schemas.containsKey("GeneratedSchema2"))
    }

    @Test
    fun nameHintStripsPackageAndGenerics() {
        // `com.acme.Result<User>` → `Result`
        val model = ObjectModel.Object(
            linkedMapOf("data" to FieldModel(model = ObjectModel.single("string")))
        )
        val converter = converter()

        val schema = converter.convert(model, nameHint = "com.acme.Result<User>")!!

        assertEquals("#/components/schemas/Result", schema.`$ref`)
        val schemas = converter.buildComponents().schemas!!
        assertTrue(schemas.containsKey("Result"))
        assertFalse(schemas.containsKey("com.acme.Result<User>"))
    }

    // ─── Misc: null model ───────────────────────────────────────────────────

    @Test
    fun convertNullModelReturnsNull() {
        assertNull(converter().convert(null))
    }

    @Test
    fun buildComponentsReturnsEmptyObjectWhenNothingRegistered() {
        val components = converter().buildComponents()
        // No schemas registered.
        assertNull(components.schemas)
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private fun converter(): OpenApiSchemaConverter = OpenApiSchemaConverter()

    /**
     * Builds a self-referential `Node { next: Node }` model. The trick is to
     * construct an `ObjectModel.Object` with an empty mutable `fields` map,
     * then mutate the map to add the self-reference after construction. The
     * `Object.id` is auto-assigned at construction and stays stable, so the
     * cycle detection (which uses `Object.id`) sees the same id when we
     * recurse into the `next` field.
     */
    private fun buildSelfReferentialNode(): ObjectModel.Object {
        val fields = linkedMapOf<String, FieldModel>()
        val node = ObjectModel.Object(fields = fields)
        fields["next"] = FieldModel(model = node)
        return node
    }
}

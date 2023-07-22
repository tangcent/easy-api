package com.itangcent.intellij.extend

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.itangcent.common.utils.GsonUtils

/**
 * Convert a JsonObject to a mutable map with string keys and nullable values.
 */
fun JsonObject.asHashMap(): MutableMap<String, Any?> {
    val map = LinkedHashMap<String, Any?>()
    this.entrySet().forEach { map[it.key] = it.value.unbox() }
    return map
}

/**
 * Convert a JsonElement to a map with string keys and nullable values.
 */
fun JsonElement.asMap(dumb: Boolean = true): Map<String, Any?> {
    return when {
        this.isJsonObject -> this.asJsonObject.asHashMap()
        dumb -> LinkedHashMap()
        else -> throw IllegalStateException("Not a JSON Object: $this")
    }
}

fun JsonArray.asMutableList(): MutableList<Any?> {
    val list: ArrayList<Any?> = ArrayList()
    this.forEach { list.add(it.unbox()) }
    return list
}

fun JsonElement.asList(dumb: Boolean = true): List<Any?> {
    return when {
        this.isJsonArray -> this.asJsonArray.asMutableList()
        dumb -> ArrayList()
        else -> throw IllegalStateException("Not a JSON Array: $this")
    }
}

/**
 * Unbox a JsonElement.
 */
fun JsonElement.unbox(): Any? {
    return when {
        this.isJsonNull -> null
        this.isJsonObject -> asMap()
        this.isJsonArray -> asList()
        this.isJsonPrimitive -> this.asJsonPrimitive.unbox()
        else -> null
    }
}

fun JsonPrimitive.unbox(): Any? {
    return when {
        this.isBoolean -> this.asBoolean
        this.isNumber -> this.asNumber
        this.isString -> this.asString
        else -> null
    }
}

/**
 * Parse a string as a JsonElement.
 */
fun String.asJsonElement(): JsonElement? {
    return GsonUtils.parseToJsonTree(this)
}

/**
 * Get a property of a JsonObject as a JsonElement.
 *
 * If the JsonElement is not a JsonObject or the property does not exist, this function
 * returns null.
 */
fun JsonElement?.sub(property: String): JsonElement? {
    if (this == null || !this.isJsonObject) {
        return null
    }
    return this.asJsonObject.get(property)
}

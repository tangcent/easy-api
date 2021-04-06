package com.itangcent.idea.utils

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator


object JacksonUtils {

    private val objectMapper: ObjectMapper = ObjectMapper()

    init {
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY)
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL)
    }

    fun toJson(bean: Any?): String {
        if (bean == null) {
            return "null"
        }
        return "${bean::class.java.name},${objectMapper.writeValueAsString(bean)}"
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> fromJson(json: String): T? {
        if (json.startsWith("{\"c\":")) {
            //old version cache
            return null
        }
        if (json == "null") {
            return null
        }
        val split = json.indexOf(',')
        return try {
            objectMapper.readValue(
                    json.substring(split + 1),
                    Class.forName(json.substring(0, split)) as Class<T>)
        } catch (e: Exception) {
            LOG.error("failed parse json: [$json]")
            null
        }
    }
}

private val LOG = org.apache.log4j.Logger.getLogger(JacksonUtils::class.java)

package com.itangcent.utils

import com.itangcent.common.constant.Attrs
import com.itangcent.common.model.Header
import com.itangcent.utils.ExtensibleKit.fromJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test


/**
 * Test case for [ExtensibleKit]
 */
class ExtensibleKitTest {

    @Test
    fun testFromJson() {
        val acceptHeader = Header()

        acceptHeader.name = "Accept"
        acceptHeader.value = "*/*"
        acceptHeader.desc = "authentication"
        acceptHeader.required = true

        assertEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true}"))
        assertEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true, default:\"token123\"}"))

        assertEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true}", Attrs.DEFAULT_VALUE_ATTR))
        assertNotEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true, default:\"token123\"}", Attrs.DEFAULT_VALUE_ATTR))

        acceptHeader.setExt(Attrs.DEFAULT_VALUE_ATTR, "token123")

        assertNotEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true}"))
        assertNotEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true, default:\"token123\"}"))

        assertNotEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true}", Attrs.DEFAULT_VALUE_ATTR))
        assertEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true, default:\"token123\"}", Attrs.DEFAULT_VALUE_ATTR))

        //ext with '@'
        assertEquals(acceptHeader,
                Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true, \"@default\":\"token123\"}"))
    }
}
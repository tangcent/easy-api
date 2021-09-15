package com.itangcent.utils

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

        assertEquals(
            acceptHeader,
            Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true}")
        )

        acceptHeader.setExt("@flag", "deprecated")
        assertNotEquals(
            acceptHeader,
            Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true, flag:\"deprecated\"}")
        )

        assertEquals(
            acceptHeader,
            Header::class.fromJson(
                "{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true, flag:\"deprecated\"}",
                "flag"
            )
        )

        //ext with '@'
        assertEquals(
            acceptHeader,
            Header::class.fromJson("{name: \"Accept\",value: \"*/*\",desc: \"authentication\",required:true, \"@flag\":\"deprecated\"}")
        )
    }
}
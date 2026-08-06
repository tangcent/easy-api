package com.itangcent.easyapi.core.psi

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Verifies that field rules see inherited members from the exported class's
 * perspective while retaining access to the member's declaring class.
 */
class MemberRuleContextIntegrationTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader(): TestConfigReader = TestConfigReader.fromConfigText(
        project,
        """
        field.name=groovy:it.containingClass()?.qualifiedName() == "com.example.Child" && it.defineClass()?.qualifiedName() == "com.example.Parent" ? "renamedInheritedField" : null
        """.trimIndent()
    )

    fun testInheritedFieldNameRuleUsesCurrentAndDeclaringClasses() = runTest {
        myFixture.addClass(
            """
            package com.example;
            public class Parent {
                protected String inheritedField;
            }
            """.trimIndent()
        )
        myFixture.addClass(
            """
            package com.example;
            public class Child extends Parent {
                private String ownField;
            }
            """.trimIndent()
        )

        val childClass = findClass("com.example.Child")
        assertNotNull("Should find Child", childClass)

        val fields = PsiClassHelper.getInstance(project)
            .buildObjectModel(childClass!!)
            ?.asObject()
            ?.fields
        assertNotNull("Should build Child fields", fields)
        assertTrue("Own field should be preserved", fields!!.containsKey("ownField"))
        assertTrue("Inherited field should be renamed by member-aware rule", fields.containsKey("renamedInheritedField"))
        assertFalse("Original inherited field name should be replaced", fields.containsKey("inheritedField"))
    }
}

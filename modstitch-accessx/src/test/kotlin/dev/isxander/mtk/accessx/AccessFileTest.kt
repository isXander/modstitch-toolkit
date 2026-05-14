package dev.isxander.mtk.accessx

import java.io.StringWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AccessFileTest {
    @Test
    fun `parses access transformer comments classes fields and joined method descriptors`() {
        val file = AccessFile.parse(
            """
            # comments are ignored
            public-f net.minecraft.client.Minecraft run()V # inline comments are ignored
            protected net.minecraft.world.level.Level someField Ljava/lang/String;
            private net.minecraft.world.entity.Entity
            """.trimIndent().reader()
        )

        assertEquals(AccessFormat.AT, file.format)
        assertEquals(AccessNamespace.Official, file.namespace)
        assertEquals(3, file.entries.size)

        val method = assertIs<AccessEntry.AccessModifier.Method>(file.entries[0])
        assertEquals(AccessEntry.AccessModifier.Modification.Public, method.modification)
        assertEquals(false, method.final)
        assertEquals("net/minecraft/client/Minecraft", method.className)
        assertEquals("run", method.methodName)
        assertEquals("()V", method.methodDescriptor)

        val field = assertIs<AccessEntry.AccessModifier.Field>(file.entries[1])
        assertEquals(AccessEntry.AccessModifier.Modification.Protected, field.modification)
        assertEquals("net/minecraft/world/level/Level", field.className)
        assertEquals("someField", field.fieldName)
        assertEquals("Ljava/lang/String;", field.fieldDescriptor)

        val clazz = assertIs<AccessEntry.AccessModifier.Class>(file.entries[2])
        assertEquals(AccessEntry.AccessModifier.Modification.Private, clazz.modification)
        assertEquals("net/minecraft/world/entity/Entity", clazz.className)
    }

    @Test
    fun `writes class tweaker entries on separate lines and rejects unsupported target formats`() {
        val file = AccessFile.parse(
            """
            classTweaker v2 named
            transitive-accessible class example/Foo
            transitive-inject-interface example/Foo example/Injected
            extend-enum example/Mode EXTRA
            """.trimIndent().reader()
        )

        assertTrue(file.isValid)
        assertFailsWith<IllegalStateException> {
            file.convertFormat(AccessFormat.AW_V2)
        }

        val output = StringWriter().also(file::write).toString()
        assertEquals(
            """
            classTweaker	v2	named
            transitive-accessible	class	example/Foo
            transitive-inject-interface	example/Foo	example/Injected
            extend-enum	example/Mode	EXTRA

            """.trimIndent(),
            output
        )
    }

    @Test
    fun `converts fabric access widener entries to forge access transformer output`() {
        val file = AccessFile.parse(
            """
            accessWidener v2 named
            accessible method example/Foo tick ()V
            mutable field example/Foo value I
            """.trimIndent().reader()
        ).convertFormat(AccessFormat.AT)

        val output = StringWriter().also(file::write).toString()
        assertEquals(
            """
            public example.Foo tick()V
            public-f example.Foo value I

            """.trimIndent(),
            output
        )
    }
}

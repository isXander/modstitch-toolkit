package dev.isxander.mtk.manifests

import dev.isxander.mtk.manifests.spec.VersionRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionRangeTest {
    @Test
    fun `parses union ranges and converts to both loader syntaxes`() {
        val range = VersionRange.parseMaven("[1.20,1.21),[1.21.4,)")

        assertEquals("[1.20,1.21),[1.21.4,)", range.toMaven())
        assertEquals(listOf(">=1.20 <1.21", ">=1.21.4"), range.toFabric())

        assertTrue(range.satisfies("1.20.6"))
        assertFalse(range.satisfies("1.21"))
        assertTrue(range.satisfies("1.21.4"))
    }

    @Test
    fun `handles exact any and bare lower-bound ranges`() {
        assertEquals("(,)", VersionRange.parseMaven("*").toMaven())
        assertEquals(listOf("*"), VersionRange.parseMaven("").toFabric())
        assertEquals("[1.0]", VersionRange.parseMaven("[1.0]").toMaven())
        assertEquals(listOf("=1.0"), VersionRange.parseMaven("[1.0]").toFabric())
        assertEquals("[2.0,)", VersionRange.parseMaven("2.0").toMaven())
        assertTrue(VersionRange.parseMaven("2.0").satisfies("2.0.1"))
    }
}
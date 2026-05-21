package dev.isxander.mtk.multiloader.jarinjar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResolvedEmbeddedJarTest {
    @Test
    fun `matches loom generated id and final version behavior`() {
        val jar = ResolvedEmbeddedJar(
            path = "META-INF/embeddedJars/utility.jar",
            group = "org.Example",
            artifact = "json.tools",
            classifier = "Dev",
            version = "1.2.3.Final",
            mavenVersionRange = "[1.2.3,)",
        )

        assertEquals("org_example_json_tools_dev", jar.fabricModId)
        assertEquals("1.2.3", jar.fabricVersion)
    }

    @Test
    fun `hash truncates generated fabric ids`() {
        val jar = ResolvedEmbeddedJar(
            path = "META-INF/embeddedJars/utility.jar",
            group = "org.example.a.very.long.group.name.for.generated.nested.library.metadata",
            artifact = "library.with.a.very.long.artifact.name",
            classifier = null,
            version = "1.0.0",
            mavenVersionRange = "[1.0.0,)",
        )

        assertEquals(64, jar.fabricModId.length)
        assertTrue(jar.fabricModId.startsWith("org_example_a_very_long_group_name_for_generated_n"))
    }
}

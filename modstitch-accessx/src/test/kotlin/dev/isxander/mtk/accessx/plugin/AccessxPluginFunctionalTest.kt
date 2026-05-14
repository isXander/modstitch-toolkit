package dev.isxander.mtk.accessx.plugin

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccessxPluginFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `convert helper wires generated access transformer into main resources`() {
        projectDir.resolve("settings.gradle.kts").writeText("""rootProject.name = "accessx-fixture"""")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("java")
                id("dev.isxander.mtk.accessx")
            }

            accessx.convert("main") {
                inputFiles.from(layout.projectDirectory.file("src/main/resources/example.accesswidener"))
                outputFormat.set(accessx.AT)
            }
            """.trimIndent()
        )
        projectDir.resolve("src/main/resources").mkdirs()
        projectDir.resolve("src/main/resources/example.accesswidener").writeText(
            """
            accessWidener v2 named
            accessible class example/Foo
            mutable field example/Foo value I
            """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("processResources", "--stacktrace")
            .withPluginClasspath()
            .build()

        val generated = projectDir.resolve("build/generated/accessx/main/accesstransformer.cfg")
        val processed = projectDir.resolve("build/resources/main/accesstransformer.cfg")

        assertTrue(generated.isFile)
        assertTrue(processed.isFile)
        assertEquals(generated.readText(), processed.readText())
        assertEquals(
            """
            public example.Foo
            public-f example.Foo value I

            """.trimIndent(),
            processed.readText()
        )
    }
}

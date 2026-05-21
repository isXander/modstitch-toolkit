package dev.isxander.mtk.manifests

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import tools.jackson.dataformat.toml.TomlMapper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManifestsPluginFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `source set helpers generate loader manifests into processed resources`() {
        projectDir.resolve("settings.gradle.kts").writeText("""rootProject.name = "manifests-fixture"""")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("java")
                id("dev.isxander.mtk.manifests")
            }

            manifests.fabricModJson(sourceSets.main.get()) {
                modId.set("example")
                version.set("1.0.0")
                displayName.set("Example")
                depends("minecraft", "[1.20,1.21)")
            }

            manifests.neoForgeModsToml(sourceSets.main.get()) {
                modId.set("example")
                version.set("1.0.0")
                displayName.set("Example")
                licenses.add("MIT")
                modLoader.set("javafml")
                loaderVersion.set("[4,)")
                required("minecraft", "[1.20,1.21)")
            }
            """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("processResources", "--stacktrace")
            .withPluginClasspath()
            .build()

        val fabricManifest = projectDir.resolve("build/resources/main/fabric.mod.json")
        val neoForgeManifest = projectDir.resolve("build/resources/main/META-INF/neoforge.mods.toml")

        assertTrue(fabricManifest.isFile)
        assertTrue(neoForgeManifest.isFile)
        assertTrue(fabricManifest.readText().contains(""""depends""""))
        val neoForgeText = neoForgeManifest.readText()
        val neoForgeTree = TomlMapper().readTree(neoForgeText)
        assertEquals("minecraft", neoForgeTree.path("dependencies").path("example")[0].path("modId").asString())
    }
}

package dev.isxander.mtk.multiloader.jarinjar

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class UniversalJarInJarMetadataFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `resolved jar metadata survives loader metadata tasks`() {
        projectDir.resolve("settings.gradle.kts").writeText(
            """rootProject.name = "universal-jar-in-jar-metadata-fixture""""
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.isxander.mtk.multiloader") apply false
            }

            tasks.register<dev.isxander.mtk.multiloader.jarinjar.GenerateJarJarMetadataTask>("generateRegressionJarJarMetadata") {
                resolvedJarsFile.set(layout.projectDirectory.file("resolved-jars.json"))
                metadataFile.set(layout.buildDirectory.file("metadata.json"))
            }

            tasks.register<dev.isxander.mtk.multiloader.jarinjar.PatchFabricModJsonTask>("patchRegressionFabricModJson") {
                resolvedJarsFile.set(layout.projectDirectory.file("resolved-jars.json"))
                inputFabricModJson.set(layout.projectDirectory.file("fabric.mod.json"))
                outputFabricModJson.set(layout.buildDirectory.file("fabric.mod.json"))
            }
            """.trimIndent()
        )
        projectDir.resolve("resolved-jars.json").writeText(
            """
            {
              "jars" : [ {
                "path" : "META-INF/embeddedJars/annotations-26.0.2.jar",
                "group" : "org.jetbrains",
                "artifact" : "annotations",
                "version" : "26.0.2",
                "mavenVersionRange" : "[26.0.2,)"
              }, {
                "path" : "META-INF/embeddedJars/slf4j-api-2.0.17.jar",
                "group" : "org.slf4j",
                "artifact" : "slf4j-api",
                "version" : "2.0.17",
                "mavenVersionRange" : "[2.0,3.0)"
              } ]
            }
            """.trimIndent()
        )
        projectDir.resolve("fabric.mod.json").writeText(
            """
            {
              "schemaVersion": 1,
              "id": "fixture",
              "version": "1.0.0",
              "jars": [ {
                "file": "META-INF/embeddedJars/already-present.jar"
              } ]
            }
            """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "generateRegressionJarJarMetadata",
                "patchRegressionFabricModJson",
                "--stacktrace",
            )
            .withPluginClasspath()
            .build()

        val jarJarMetadata = jarInJarJsonMapper.readTree(
            projectDir.resolve("build/metadata.json")
        )
        assertEquals(
            listOf(
                "org.jetbrains:annotations:[26.0.2,):26.0.2:META-INF/embeddedJars/annotations-26.0.2.jar",
                "org.slf4j:slf4j-api:[2.0,3.0):2.0.17:META-INF/embeddedJars/slf4j-api-2.0.17.jar",
            ),
            jarJarMetadata.path("jars").values().map { jar ->
                listOf(
                    jar.path("identifier").path("group").stringValue(),
                    jar.path("identifier").path("artifact").stringValue(),
                    jar.path("version").path("range").stringValue(),
                    jar.path("version").path("artifactVersion").stringValue(),
                    jar.path("path").stringValue(),
                ).joinToString(":")
            },
        )

        val fabricModJson = jarInJarJsonMapper.readTree(
            projectDir.resolve("build/fabric.mod.json")
        )
        assertEquals(
            listOf(
                "META-INF/embeddedJars/already-present.jar",
                "META-INF/embeddedJars/annotations-26.0.2.jar",
                "META-INF/embeddedJars/slf4j-api-2.0.17.jar",
            ),
            fabricModJson.path("jars").values().map { jar ->
                jar.path("file").stringValue()
            },
        )
    }
}

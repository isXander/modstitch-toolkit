package dev.isxander.mtk.multiloader.jarinjar

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
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

    @Test
    fun `resolve jars task separates fabric and neoforge load metadata`() {
        projectDir.resolve("settings.gradle.kts").writeText(
            """rootProject.name = "universal-jar-in-jar-split-fixture""""
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.isxander.mtk.multiloader") apply false
            }

            repositories {
                maven {
                    url = uri("repo")
                }
            }

            val embedded by configurations.creating
            val fabricLoad by configurations.creating
            val neoforgeLoad by configurations.creating

            dependencies {
                embedded("test:common:1.0.0")
                embedded("test:fabric-only:1.0.0")
                embedded("test:neoforge-only:1.0.0")

                fabricLoad("test:common:1.0.0")
                fabricLoad("test:fabric-only:1.0.0")

                neoforgeLoad("test:common:1.0.0")
                neoforgeLoad("test:neoforge-only:1.0.0")
            }

            tasks.register<dev.isxander.mtk.multiloader.jarinjar.ResolveJarsTask>("resolveSplitJars") {
                embeddedFrom(embedded)
                fabricFrom(fabricLoad)
                neoforgeFrom(neoforgeLoad)
                outputDirectory.set(layout.buildDirectory.dir("embedded"))
                fabricResolvedJarsFile.set(layout.buildDirectory.file("fabric-resolved-jars.json"))
                neoforgeResolvedJarsFile.set(layout.buildDirectory.file("neoforge-resolved-jars.json"))
            }
            """.trimIndent()
        )
        listOf("common", "fabric-only", "neoforge-only").forEach { artifact ->
            publishJar(artifact)
        }

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("resolveSplitJars", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertEquals(
            listOf(
                "common-1.0.0.jar",
                "fabric-only-1.0.0.jar",
                "neoforge-only-1.0.0.jar",
            ),
            projectDir.resolve("build/embedded/META-INF/embeddedJars")
                .listFiles()
                .orEmpty()
                .map(File::getName)
                .sorted(),
        )
        assertEquals(
            listOf("common", "fabric-only"),
            resolvedArtifacts(projectDir.resolve("build/fabric-resolved-jars.json")),
        )
        assertEquals(
            listOf("common", "neoforge-only"),
            resolvedArtifacts(projectDir.resolve("build/neoforge-resolved-jars.json")),
        )
    }

    private fun publishJar(artifact: String) {
        val artifactDir = projectDir.resolve("repo/test/$artifact/1.0.0").apply {
            mkdirs()
        }
        artifactDir.resolve("$artifact-1.0.0.pom").writeText(
            """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>test</groupId>
              <artifactId>$artifact</artifactId>
              <version>1.0.0</version>
            </project>
            """.trimIndent()
        )
        JarOutputStream(artifactDir.resolve("$artifact-1.0.0.jar").outputStream()).use { jar ->
            jar.putNextEntry(JarEntry("test/$artifact.txt"))
            jar.write(artifact.toByteArray())
            jar.closeEntry()
        }
    }

    private fun resolvedArtifacts(file: File): List<String> =
        jarInJarJsonMapper.readTree(file)
            .path("jars")
            .values()
            .asSequence()
            .map { jar -> jar.path("artifact").stringValue() }
            .toList()
}

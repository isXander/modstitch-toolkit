package dev.isxander.mtk.moddeps

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ModdepsPluginFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `repeated fabric calls reuse the same dependency set and task`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("dev.isxander.mtk.moddeps")
        val configuration = project.configurations.create("customDepends")
        val extension = project.extensions.getByType(MinecraftDependenciesExtension::class.java)

        val first = extension.fabric(configuration)
        val second = extension.fabric(configuration)

        assertSame(first, second)
        assertTrue(project.tasks.names.contains("generateCustomDependsFabricModDependencySet"))
        assertEquals(
            1,
            project.tasks.names.count { it == "generateCustomDependsFabricModDependencySet" },
        )
    }

    @Test
    fun `extracts direct fabric and neoforge dependency metadata`() {
        writeSettings()
        writeBuildFile()
        publishModule(
            artifact = "fabric-mod",
            jarEntries = mapOf("fabric.mod.json" to """{"schemaVersion":1,"id":"fabric_mod","version":"1.0.0"}"""),
        )
        publishModule(
            artifact = "neoforge-mod",
            jarEntries = mapOf("META-INF/neoforge.mods.toml" to """[[mods]]
modId = "neoforge_mod"
version = "1.0.0"
"""),
        )
        publishModule(
            artifact = "prefer-only",
            jarEntries = mapOf("fabric.mod.json" to """{"schemaVersion":1,"id":"prefer_only","version":"1.0.0"}"""),
        )
        publishModule(
            artifact = "transitive-child",
            jarEntries = mapOf("fabric.mod.json" to """{"schemaVersion":1,"id":"transitive_child","version":"1.0.0"}"""),
        )
        publishModule(
            artifact = "with-transitive",
            jarEntries = mapOf("fabric.mod.json" to """{"schemaVersion":1,"id":"with_transitive","version":"1.0.0"}"""),
            dependencies = listOf("transitive-child"),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments(
                "generateFabricDependsFabricModDependencySet",
                "generateNeoforgeDependsNeoForgeModDependencySet",
                "--stacktrace",
            )
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateFabricDependsFabricModDependencySet")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateNeoforgeDependsNeoForgeModDependencySet")?.outcome)

        val fabricDependencies = readDependencies(
            projectDir.resolve("build/modstitch-moddeps/fabricDepends/fabric/dependencies.json"),
        )
        assertEquals(
            listOf("fabric-mod", "prefer-only", "with-transitive"),
            fabricDependencies.map { it.path("name").stringValue() },
        )
        assertEquals("fabric_mod", fabricDependencies[0].path("loaderModId").stringValue())
        assertEquals("[1,2)", fabricDependencies[0].path("declaredVersionRange").stringValue())
        assertEquals("Optional", fabricDependencies[0].path("relationship").stringValue())
        assertEquals("fabric-project", fabricDependencies[0].path("modrinthProject").stringValue())
        assertEquals("fabric-curse", fabricDependencies[0].path("curseForgeProject").stringValue())
        assertTrue(fabricDependencies[1].path("declaredVersionRange").isNull)
        assertTrue(fabricDependencies[1].path("modrinthProject").isNull)
        assertTrue(fabricDependencies[1].path("curseForgeProject").isNull)
        assertEquals("explicit_parent", fabricDependencies[2].path("loaderModId").stringValue())

        val neoforgeDependencies = readDependencies(
            projectDir.resolve("build/modstitch-moddeps/neoforgeDepends/neoforge/dependencies.json"),
        )
        assertEquals(listOf("neoforge-mod"), neoforgeDependencies.map { it.path("name").stringValue() })
        assertEquals("neoforge_mod", neoforgeDependencies.single().path("loaderModId").stringValue())
        assertEquals("[1,2)", neoforgeDependencies.single().path("declaredVersionRange").stringValue())
    }

    @Test
    fun `missing loader metadata fails clearly`() {
        writeSettings()
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.isxander.mtk.moddeps")
            }

            repositories {
                maven { url = uri("repo") }
            }

            val arbitraryDepends by configurations.creating

            dependencies {
                arbitraryDepends(modDependency("test:plain:1.0.0"))
            }

            minecraftDependencies.fabric(arbitraryDepends)
            """.trimIndent(),
        )
        publishModule(artifact = "plain", jarEntries = mapOf("plain.txt" to "plain"))

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateArbitraryDependsFabricModDependencySet", "--stacktrace")
            .withPluginClasspath()
            .buildAndFail()

        assertContains(result.output, "Could not infer Fabric mod id for test:plain:1.0.0")
        assertContains(result.output, "fabric.mod.json")
    }

    @Test
    fun `mod dependency accepts version catalog library provider`() {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        library("catalog-fabric-mod", "test", "catalog-fabric-mod").version("1.0.0")
                    }
                }
            }

            rootProject.name = "moddeps-version-catalog-fixture"
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.isxander.mtk.moddeps")
            }

            repositories {
                maven { url = uri("repo") }
            }

            val fabricDepends by configurations.creating

            dependencies {
                fabricDepends(modDependency(libs.catalog.fabric.mod) {
                    publish {
                        modrinth("catalog-project")
                    }
                })
            }

            minecraftDependencies.fabric(fabricDepends)
            """.trimIndent(),
        )
        publishModule(
            artifact = "catalog-fabric-mod",
            jarEntries = mapOf("fabric.mod.json" to """{"schemaVersion":1,"id":"catalog_fabric_mod","version":"1.0.0"}"""),
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateFabricDependsFabricModDependencySet", "--stacktrace")
            .withPluginClasspath()
            .build()

        val dependencies = readDependencies(
            projectDir.resolve("build/modstitch-moddeps/fabricDepends/fabric/dependencies.json"),
        )
        assertEquals("catalog-fabric-mod", dependencies.single().path("name").stringValue())
        assertEquals("catalog_fabric_mod", dependencies.single().path("loaderModId").stringValue())
        assertEquals("catalog-project", dependencies.single().path("modrinthProject").stringValue())
    }

    @Test
    fun `generated dependency set task is configuration cache compatible`() {
        writeSettings()
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.isxander.mtk.moddeps")
            }

            repositories {
                maven { url = uri("repo") }
            }

            val fabricDepends by configurations.creating

            dependencies {
                fabricDepends(modDependency("test:cacheable-fabric-mod:1.0.0"))
            }

            minecraftDependencies.fabric(fabricDepends)
            """.trimIndent(),
        )
        publishModule(
            artifact = "cacheable-fabric-mod",
            jarEntries = mapOf("fabric.mod.json" to """{"schemaVersion":1,"id":"cacheable_fabric_mod","version":"1.0.0"}"""),
        )

        fun run() = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("generateFabricDependsFabricModDependencySet", "--configuration-cache", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertContains(run().output, "Configuration cache entry stored.")
        assertContains(run().output, "Configuration cache entry reused.")
    }

    private fun writeSettings() {
        projectDir.resolve("settings.gradle.kts").writeText("""rootProject.name = "moddeps-fixture"""")
    }

    private fun writeBuildFile() {
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.isxander.mtk.moddeps")
            }

            repositories {
                maven { url = uri("repo") }
            }

            val fabricDepends by configurations.creating
            val neoforgeDepends by configurations.creating

            dependencies {
                fabricDepends(modDependency("test:fabric-mod") {
                    version {
                        prefer("1.0.0")
                        strictly("[1,2)")
                    }
                    optional()
                    publish {
                        modrinth("fabric-project")
                        curseforge("fabric-curse")
                    }
                })
                fabricDepends(modDependency("test:prefer-only") {
                    version {
                        prefer("1.0.0")
                    }
                })
                fabricDepends(modDependency("test:with-transitive:1.0.0") {
                    modId("explicit_parent")
                })

                neoforgeDepends(modDependency("test:neoforge-mod") {
                    version {
                        strictly("[1,2)")
                        prefer("1.0.0")
                    }
                })
            }

            val first = minecraftDependencies.fabric(fabricDepends)
            val second = minecraftDependencies.fabric(fabricDepends)
            check(first === second)
            minecraftDependencies.neoforge(neoforgeDepends)
            """.trimIndent(),
        )
    }

    private fun publishModule(
        artifact: String,
        jarEntries: Map<String, String>,
        dependencies: List<String> = emptyList(),
    ) {
        val moduleDir = projectDir.resolve("repo/test/$artifact").apply {
            mkdirs()
        }
        moduleDir.resolve("maven-metadata.xml").writeText(
            """
            <metadata>
              <groupId>test</groupId>
              <artifactId>$artifact</artifactId>
              <versioning>
                <latest>1.0.0</latest>
                <release>1.0.0</release>
                <versions>
                  <version>1.0.0</version>
                </versions>
              </versioning>
            </metadata>
            """.trimIndent(),
        )
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
              <dependencies>
                ${dependencies.joinToString("\n") { dependency ->
                """
                <dependency>
                  <groupId>test</groupId>
                  <artifactId>$dependency</artifactId>
                  <version>1.0.0</version>
                </dependency>
                """.trimIndent()
            }}
              </dependencies>
            </project>
            """.trimIndent(),
        )
        JarOutputStream(artifactDir.resolve("$artifact-1.0.0.jar").outputStream()).use { jar ->
            jarEntries.forEach { (path, contents) ->
                jar.putNextEntry(JarEntry(path))
                jar.write(contents.toByteArray())
                jar.closeEntry()
            }
        }
    }

    private fun readDependencies(file: File) =
        modDependencyJsonMapper.readTree(file)
            .path("dependencies")
            .values()
            .asSequence()
            .toList()
}

package dev.isxander.mtk.propapply

import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PropapplyPluginTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `fails clearly when platform property is missing`() {
        val project = ProjectBuilder.builder().withName("missing-platform").build()

        val failure = assertFailsWith<IllegalStateException> {
            PropapplyPlugin().apply(project)
        }

        assertContains(failure.message.orEmpty(), "missing 'modstitch.platform' property")
    }

    @Test
    fun `fails clearly for unknown platform property`() {
        val project = ProjectBuilder.builder().build()
        project.extensions.extraProperties["modstitch.platform"] = "not-a-loader"

        val failure = assertFailsWith<IllegalStateException> {
            PropapplyPlugin().apply(project)
        }

        assertContains(failure.message.orEmpty(), "unknown platform: not-a-loader")
    }

    @Test
    fun `applies the plugin selected by modstitch platform property`() {
        val projectDir = tempDir.resolve("fixture")
        writeFixture(projectDir, "fabric-loom-remap", "net.fabricmc.fabric-loom-remap")

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("verifyPlatform", "--stacktrace")
            .withPluginClasspath()
            .build()

        assertTrue(projectDir.resolve("build/verified.txt").readText().contains("net.fabricmc.fabric-loom-remap"))
    }

    private fun writeFixture(projectDir: File, platform: String, expectedPlugin: String) {
        projectDir.mkdirs()
        projectDir.resolve("settings.gradle.kts").writeText(
            """rootProject.name = "propapply-fixture""""
        )
        projectDir.resolve("gradle.properties").writeText("modstitch.platform=$platform\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.isxander.mtk.propapply")
            }

            tasks.register("verifyPlatform") {
                doLast {
                    check(plugins.hasPlugin("$expectedPlugin")) {
                        "Expected $expectedPlugin to be applied"
                    }
                    layout.buildDirectory.file("verified.txt").get().asFile.apply {
                        parentFile.mkdirs()
                        writeText("$expectedPlugin applied")
                    }
                }
            }
            """.trimIndent()
        )

        val fakeBuild = projectDir.resolve("buildSrc")
        fakeBuild.mkdirs()
        fakeBuild.resolve("settings.gradle.kts").writeText("""rootProject.name = "fake-platform-plugins"""")
        fakeBuild.resolve("build.gradle.kts").writeText(
            """
            plugins {
                `java-gradle-plugin`
            }

            gradlePlugin {
                plugins {
                    register("fabricLoom") {
                        id = "net.fabricmc.fabric-loom"
                        implementationClass = "fake.FakePlatformPlugin"
                    }
                    register("fabricLoomRemap") {
                        id = "net.fabricmc.fabric-loom-remap"
                        implementationClass = "fake.FakePlatformPlugin"
                    }
                    register("modDevGradle") {
                        id = "net.neoforged.moddev"
                        implementationClass = "fake.FakePlatformPlugin"
                    }
                    register("modDevGradleLegacy") {
                        id = "net.neoforged.moddev.legacyforge"
                        implementationClass = "fake.FakePlatformPlugin"
                    }
                }
            }
            """.trimIndent()
        )
        fakeBuild.resolve("src/main/java/fake").mkdirs()
        fakeBuild.resolve("src/main/java/fake/FakePlatformPlugin.java").writeText(
            """
            package fake;

            import org.gradle.api.Plugin;
            import org.gradle.api.Project;

            public class FakePlatformPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                }
            }
            """.trimIndent()
        )
    }
}

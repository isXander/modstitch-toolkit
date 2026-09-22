package dev.isxander.mtk.multiloader.publishing

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Exercise the production feature/publication setup without downloading Minecraft or applying
// loader plugins: the regression is in Gradle publication and dependency resolution itself.
class PublishingFixturePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("java-library")
        setupFeatures(project)
        configurePublicationCapabilities(project)
    }
}

class PublicationCapabilitiesFunctionalTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `renamed publications resolve by loader and feature without project capability conflicts`() {
        projectDir.resolve("settings.gradle.kts").writeText("""rootProject.name = "26.1"""")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.isxander.mtk.multiloader") apply false
                `maven-publish`
            }
            apply<dev.isxander.mtk.multiloader.publishing.PublishingFixturePlugin>()
            group = "dev.isxander"
            version = "1.0"
            configurations.named("fabricApiElements") {
                outgoing.capability("other:custom:1.0")
            }
            publishing {
                publications {
                    create<MavenPublication>("mod") {
                        from(components["java"])
                        artifactId = "controlify"
                    }
                    create<MavenPublication>("other") {
                        from(components["java"])
                        groupId = "published.group"
                        artifactId = "other-mod"
                        version = "2.0"
                    }
                    create<MavenPublication>("unchanged") {
                        from(components["java"])
                    }
                }
                repositories { maven { url = uri("repo") } }
            }
            """.trimIndent()
        )
        val runner = GradleRunner.create().withProjectDir(projectDir).withPluginClasspath()
        runner.withPluginClasspath(runner.pluginClasspath + File(PublishingFixturePlugin::class.java.protectionDomain.codeSource.location.toURI()))
        runner.withArguments("publish", "--configuration-cache", "--stacktrace").build()
        val cached = runner.withArguments("publish", "--configuration-cache", "--rerun-tasks", "--stacktrace").build()
        assertTrue(cached.output.contains("Reusing configuration cache."))

        assertCapabilities("dev.isxander", "controlify", "1.0")
        assertCapabilities("published.group", "other-mod", "2.0")
        assertCapabilities("dev.isxander", "26.1", "1.0")

        val consumer = projectDir.resolve("consumer").apply { mkdirs() }
        consumer.resolve("settings.gradle.kts").writeText("""rootProject.name = "26.1"""")
        consumer.resolve("build.gradle.kts").writeText(
            """
            plugins { `java-library` }
            group = "dev.isxander"
            version = "1.0"
            repositories { maven { url = uri("../repo") } }
            val loader = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)
            val requests = listOf("common", "fabric", "neoforge")
            requests.forEach { requested ->
                configurations.create(requested) {
                    isCanBeConsumed = false
                    attributes {
                        attribute(loader, requested)
                        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
                    }
                }
                configurations.create(requested + "Feature") {
                    isCanBeConsumed = false
                    attributes { attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME)) }
                }
                dependencies.add(requested, "dev.isxander:controlify:1.0")
                dependencies.add(requested + "Feature", "dev.isxander:controlify:1.0") {
                    this as ExternalModuleDependency
                    capabilities { requireCapability("dev.isxander:controlify-" + requested) }
                }
            }
            dependencies { implementation("dev.isxander:controlify:1.0") }
            configurations.compileClasspath {
                attributes { attribute(loader, "fabric") }
            }
            tasks.register("resolve") {
                val classpath = configurations.compileClasspath.get()
                val requestedFiles = requests.associateWith { configurations[it] to configurations[it + "Feature"] }
                doLast {
                    check(classpath.singleFile.name == "controlify-1.0-fabric.jar")
                    requestedFiles.forEach { (loader, configurations) ->
                        val expected = "controlify-1.0" + (if (loader == "common") "" else "-" + loader) + ".jar"
                        check(configurations.first.singleFile.name == expected)
                        check(configurations.second.singleFile.name == expected)
                    }
                }
            }
            """.trimIndent()
        )
        GradleRunner.create().withProjectDir(consumer).withArguments("resolve", "--stacktrace").build()
    }

    private fun assertCapabilities(group: String, module: String, version: String) {
        val metadata = JsonMapper.builder().build().readTree(
            projectDir.resolve("repo/${group.replace('.', '/')}/$module/$version/$module-$version.module")
        )
        val variants = metadata.path("variants").values().toList()
        assertEquals(12, variants.size)
        for (variant in variants) {
            val loader = variant.path("attributes").path("io.github.mcgradleconventions.loader").stringValue()
            val actual = variant.path("capabilities").values().map {
                "${it.path("group").stringValue()}:${it.path("name").stringValue()}:${it.path("version").stringValue()}"
            }.toSet()
            val expected = mutableSetOf("$group:$module:$version", "$group:$module-$loader:$version")
            if (variant.path("name").stringValue() == "fabricApiElements") expected.add("other:custom:1.0")
            assertEquals(expected, actual, variant.path("name").stringValue())
        }
    }
}

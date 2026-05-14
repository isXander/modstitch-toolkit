package dev.isxander.mtk.commonconf.extensions

import dev.isxander.mtk.commonconf.CommonconfPlugin
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.property
import javax.inject.Inject

abstract class CommonconfExtension @Inject constructor(
    objects: ObjectFactory,
    private val project: Project,
) {
    /**
     * Defines the Minecraft version this project targets.
     *
     * - On Loom: `minecraft("com.mojang:minecraft:$thisProperty")`
     * - On ModDevGradle: This Property is essentially treated as a Provider.
     *   Setting this property does nothing. After commonconf enables MDG, it sets and finalizes
     *   this property to the minecraft version the MDG extension provides.
     *   Commonconf attempts to parse [loaderVersion] to convert to a minecraft version.
     *   Then, when it enables MDG, it sets this property definitively, provided by MDG itself.
     */
    val minecraftVersion: Property<String> =
        objects.property()

    /**
     * Defines the mod loader version this project targets.
     *
     * - On Loom: `implementation("net.fabricmc:fabric-loader:$thisProperty")`
     * - On ModDevGradle: `neoForge.version = thisProperty`
     */
    val loaderVersion: Property<String> =
        objects.property()

    /**
     * Defines any accessx files this project uses.
     *
     * - On Loom: `loom.accessWidenerPath = thisProperty`.
     *   If there are multiple files in this collection on Loom, an error will be thrown.
     * - On ModDevGradle: `neoForge.accessTransformers.from(thisProperty)`
     *
     * Consider using the `modstitch-accessx` plugin to convert your accessx files
     * between loader formats.
     */
    val accessxFiles: ConfigurableFileCollection =
        objects.fileCollection()

    /**
     * Defines run configurations for this project.
     *
     * - On Loom: `loom.runs` is configured to contain these objects.
     * - On ModDevGradle: `neoForge.runs` is configured to contain these objects.
     *
     * This plugin calls `register` on the backing container, so if they already exist,
     * Gradle will throw an error. Loom (and presumably ModDevGradle) automatically registers
     * default run configurations
     */
    val runs: NamedDomainObjectContainer<RunConfig> =
        objects.domainObjectContainer(RunConfig::class.java)

    fun disableDefaultRuns() {
        CommonconfPlugin.disableIdeRuns(project)
    }
}
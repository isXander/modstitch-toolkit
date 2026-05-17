package dev.isxander.mtk.multiloader.jarinjar

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Resolves an include configuration and outputs the jars into a folder,
 * read for embedding into the universal jar.
 *
 * Outputs resolved jar metadata to [resolvedJarsFile] for use by the loader-specific metadata generation tasks.
 */
@CacheableTask
abstract class ResolveJarsTask : DefaultTask() {

    @get:Classpath
    abstract val configurations: ConfigurableFileCollection

    /**
     * The directory within the jar that holds the embedded jars.
     */
    @get:Input
    abstract val jarPath: Property<String>

    init {
        jarPath.convention("META-INF/embeddedJars")
    }

    /**
     * Output directory that should be merged with jars, it includes the embedded jars.
     * Relative to this directory, jars are located at [jarPath]/<examplename>.jar.
     *
     * This is intended to be sourced by the relevant source set's resources.
     *
     * This does not include NeoForge's `META-INF/jarjar/metadata.json` or
     * Fabric's modified `fabric.mod.json` files.
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /**
     * Contains structured metadata about the embedded jars and their location.
     * This output is intended to be used as inputs by the loader-specific metadata generation tasks.
     *
     * The file is a JSON array of [ResolvedEmbeddedJar]s.
     */
    @get:OutputFile
    abstract val resolvedJarsFile: RegularFileProperty

    @TaskAction
    fun embedJars() {
        // TODO: one big massive fuck-off TODO right here
        // prior works:
        // - https://github.com/FabricMC/fabric-loom/blob/exp/1.17/src/main/java/net/fabricmc/loom/build/nesting/NestableJarGenerationTask.java
        // - https://github.com/neoforged/NeoGradle/blob/NG_7.1/common/src/main/java/net/neoforged/gradle/common/tasks/JarJar.java
    }
}
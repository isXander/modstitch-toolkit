package dev.isxander.mtk.accessx.plugin

import dev.isxander.mtk.accessx.AccessFile
import dev.isxander.mtk.accessx.AccessFormat
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.withType

/**
 * A Gradle task that converts an accessx file to another format.
 * If multiple inputs are given, their entries are merged.
 *
 * This task does not do any remapping between namespaces.
 * If the namespace of the input files differ from each other, the task will fail.
 * The output will use the same namespace as the inputs.
 */
@CacheableTask
abstract class ConvertAccessxTask : DefaultTask() {
    /** The input accessx files to be converted. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputFiles: ConfigurableFileCollection

    /** The output format. */
    @get:Input
    abstract val outputFormat: Property<AccessFormat>

    /** The output file for the generated accessx file. */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    /**
     * Wires this task to run before ModDevGradle's `createMinecraftArtifacts` task.
     *
     * Has no effect unless the `net.neoforged.moddev` plugin is applied; the wiring is
     * deferred until the plugin appears, so call ordering does not matter.
     *
     * Useful when you want the output file to be used as the access transformer
     * that applies to development sources.
     *
     * THIS DOES NOT TELL MDG TO USE THIS TASK'S OUTPUT.
     * It simply ensures that this task is ran early enough that you can configure it to do so.
     * ```kotlin
     * neoForge {
     *     accessTransformers.from(thisTask.outputFile)
     * }
     *
     * // or with `modstitch-commonconf`
     *
     * commonconf {
     *     accessxFiles.from(thisTask.outputFile)
     * }
     * ```
     */
    fun runBeforePlugins() {
        val accessxTask = this
        project.pluginManager.withPlugin("net.neoforged.moddev") {
            project.tasks.named("createMinecraftArtifacts") {
                val task = this
                task.dependsOn(this@ConvertAccessxTask)
            }
        }
    }

    /**
     * Converts an accessx file to another format and outputs to a separate file.
     */
    @TaskAction
    fun generateAccessTransformer() {
        val parsedFiles = inputFiles.files
            .sortedBy { it.absolutePath } // deterministic output
            .map { file ->
                file.reader().use { reader -> AccessFile.parse(reader) }
            }

        val namespace = parsedFiles.map { it.namespace }.singleOrNull()
            ?: error("Input files have different namespaces, cannot merge")

        val entries = parsedFiles.flatMap { file -> file.entries }

        val mergedFile = AccessFile(outputFormat.get(), entries, namespace)

        outputFile.get().asFile.writer().use { writer ->
            mergedFile.write(writer)
        }
    }
}

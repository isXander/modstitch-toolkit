package dev.isxander.mtk.accessx.plugin

import dev.isxander.mtk.accessx.AccessFormat
import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import javax.inject.Inject

abstract class AccessxExtension @Inject constructor(private val project: Project) {
    val AT = AccessFormat.AT
    val AW_V1 = AccessFormat.AW_V1
    val AW_V2 = AccessFormat.AW_V2
    val CT_V1 = AccessFormat.CT_V1
    val CT_V2 = AccessFormat.CT_V2

    /**
     * Registers a [ConvertAccessxTask] whose output is bundled into the `main` source set's resources.
     *
     * The task is named `convertAccessx<Name>` and writes to `build/generated/accessx/<name>/`.
     * The output directory is added as a resource source directory, so the converted file is
     * picked up by `processResources` and included in the produced jar. The convert task is
     * automatically wired as a dependency of `processResources`.
     *
     * @param name a unique identifier used for the task name and output directory.
     * @param action configuration applied to the registered task (inputs, output format, etc.).
     * @return the [TaskProvider] for the registered convert task.
     */
    fun convert(name: String, action: Action<ConvertAccessxTask>): TaskProvider<ConvertAccessxTask> =
        convert(name, SourceSet.MAIN_SOURCE_SET_NAME, action)

    /**
     * Registers a [ConvertAccessxTask] whose output is bundled into the given [sourceSet]'s resources.
     *
     * Behaves identically to [convert(name, action)][convert] but allows targeting a specific
     * [SourceSet] instance instead of `main`.
     *
     * @param name a unique identifier used for the task name and output directory.
     * @param sourceSet the source set whose resources will receive the generated file.
     * @param action configuration applied to the registered task.
     * @return the [TaskProvider] for the registered convert task.
     */
    fun convert(name: String, sourceSet: SourceSet, action: Action<ConvertAccessxTask>): TaskProvider<ConvertAccessxTask> =
        convert(name, sourceSet.name, action)

    /**
     * Registers a [ConvertAccessxTask] whose output is bundled into the given [sourceSet]'s resources.
     *
     * Behaves identically to [convert(name, action)][convert] but allows targeting a specific
     * [SourceSet] instance instead of `main`.
     *
     * @param name a unique identifier used for the task name and output directory.
     * @param sourceSet the source set whose resources will receive the generated file.
     * @param action configuration applied to the registered task.
     * @return the [TaskProvider] for the registered convert task.
     */
    fun convert(name: String, sourceSet: NamedDomainObjectProvider<SourceSet>, action: Action<ConvertAccessxTask>): TaskProvider<ConvertAccessxTask> =
        convert(name, sourceSet.name, action)

    /**
     * Registers a [ConvertAccessxTask] whose output is bundled into the resources of the source
     * set with the given [sourceSetName].
     *
     * The lookup of the source set is deferred until the `java` plugin is applied, so the call
     * site does not need to ensure plugin ordering.
     *
     * @param name a unique identifier used for the task name and output directory.
     * @param sourceSetName the name of the source set whose resources will receive the generated file.
     * @param action configuration applied to the registered task.
     * @return the [TaskProvider] for the registered convert task.
     */
    fun convert(name: String, sourceSetName: String, action: Action<ConvertAccessxTask>): TaskProvider<ConvertAccessxTask> {
        val outputDir = project.layout.buildDirectory.dir("generated/accessx/$name")

        val task = project.tasks.register(
            "convertAccessx${name.replaceFirstChar { it.uppercaseChar() }}",
            ConvertAccessxTask::class.java
        ) {
            val task = this

            group = "modstitch/accessx"
            task.outputFile.convention(task.outputFormat.flatMap { fmt ->
                outputDir.map { dir -> dir.file(defaultFileName(name, fmt)) }
            })
            action.execute(task)
        }

        project.pluginManager.withPlugin("java") {
            val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
            sourceSets.named(sourceSetName) ss@{
                this@ss.resources.srcDir(project.files(outputDir).builtBy(task))
            }
        }

        return task
    }

    private fun defaultFileName(name: String, format: AccessFormat): String = when (format) {
        AccessFormat.AT -> "accesstransformer.cfg"
        AccessFormat.AW_V1, AccessFormat.AW_V2 -> "$name.accesswidener"
        AccessFormat.CT_V1, AccessFormat.CT_V2 -> "$name.classtweaker"
    }
}
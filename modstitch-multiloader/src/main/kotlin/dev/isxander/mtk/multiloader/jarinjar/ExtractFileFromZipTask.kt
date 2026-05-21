package dev.isxander.mtk.multiloader.jarinjar

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import javax.inject.Inject

/**
 * Extracts a singular file from an input zip file, matching an ANT-style pattern.
 */
@CacheableTask
abstract class ExtractFileFromZipTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputZip: RegularFileProperty

    @get:Input
    abstract val pattern: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Inject
    protected abstract val archiveOperations: ArchiveOperations

    @TaskAction
    fun extract() {
        val inputJarFile = inputZip.get().asFile
        val pattern = pattern.get()

        val foundFile = archiveOperations.zipTree(inputJarFile)
            .matching {
                include(pattern)
            }
            .singleOrNull()
            ?: throw GradleException(
                "Cannot extract file because ${inputJarFile.name} does not contain $pattern."
            )

        val outputFile = outputFile.get().asFile
        outputFile.parentFile.mkdirs()
        foundFile.copyTo(outputFile, overwrite = true)
    }
}
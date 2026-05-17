package dev.isxander.mtk.multiloader.jarinjar

import com.electronwill.nightconfig.core.io.ParsingException
import com.electronwill.nightconfig.json.JsonFormat
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.Problems
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

/**
 * Patches a Fabric `fabric.mod.json` file to include the embedded jars as
 * "jars" entries.
 */
@CacheableTask
abstract class PatchFabricModJsonTask : ResolvedJarConsumerTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFabricModJson: RegularFileProperty

    @get:OutputFile
    abstract val outputFabricModJson: RegularFileProperty

    @get:Inject
    protected abstract val problems: Problems

    @TaskAction
    fun patch() {
        val jars = getResolvedJars()

        val inputFile = inputFabricModJson.get().asFile

        // parse the input file as valid json
        val fabricModJson = inputFile.reader().use { reader ->
            try {
                JsonFormat.fancyInstance().createParser().parse(reader)
            } catch (e: ParsingException) {
                throw problems.reporter.throwing(e, PROBLEM_ID) {
                    // TODO: improve this
                    solution("Ensure that the fabric.mod.json file is valid JSON.")
                }
            }
        }

        // Patch the fabric.mod.json file to include the embedded jars as "jars" entries.
        fabricModJson.add("jars", jars.map { jar ->
            fabricModJson.createSubConfig().apply {
                add("file", jar.path)
            }
        })

        // write the patched fabric.mod.json file to the output file
        val outputFile = outputFabricModJson.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writer().use { writer ->
            JsonFormat.fancyInstance().createWriter().write(fabricModJson, writer)
        }
    }

    private companion object {
        val PROBLEM_ID: ProblemId = ProblemId.create(
            "jarinjar-fabric-mod-json-parse-failure",
            "Could not parse fabric.mod.json:",
            ProblemGroup.create("modstitch-multiloader", "Modstitch Multiloader"),
        )
    }
}
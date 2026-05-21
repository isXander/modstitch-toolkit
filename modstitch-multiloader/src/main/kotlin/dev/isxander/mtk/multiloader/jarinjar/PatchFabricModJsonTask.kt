package dev.isxander.mtk.multiloader.jarinjar

import com.electronwill.nightconfig.core.io.ParsingException
import com.electronwill.nightconfig.core.io.ParsingMode
import com.electronwill.nightconfig.json.JsonFormat
import dev.isxander.mtk.multiloader.utils.ModstitchProblems
import dev.isxander.mtk.multiloader.utils.throwingUniversalJarInJarFMJParseFailure
import org.gradle.api.file.RegularFileProperty
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
        val jars = readResolvedJars()

        val inputFile = inputFabricModJson.get().asFile

        // parse the input file as valid json into an order-preserving config so the
        // patched output keeps the original key ordering
        val fabricModJson = JsonFormat.newConfig(::LinkedHashMap)
        inputFile.reader().use { reader ->
            try {
                JsonFormat.fancyInstance().createParser()
                    .parse(reader, fabricModJson, ParsingMode.REPLACE)
            } catch (e: ParsingException) {
                throw problems.reporter.throwingUniversalJarInJarFMJParseFailure(
                    e, inputFile.absolutePath
                )
            }
        }

        val existingJars = fabricModJson.get<List<Any>>("jars").orEmpty()

        // Patch the fabric.mod.json file to include the embedded jars as "jars" entries.
        fabricModJson.set<List<Any>>("jars", existingJars + jars.map { jar ->
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
}

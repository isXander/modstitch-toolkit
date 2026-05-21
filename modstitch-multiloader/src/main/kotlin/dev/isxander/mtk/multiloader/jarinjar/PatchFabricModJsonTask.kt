package dev.isxander.mtk.multiloader.jarinjar

import dev.isxander.mtk.multiloader.utils.throwingUniversalJarInJarFMJParseFailure
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.problems.Problems
import org.gradle.api.tasks.*
import tools.jackson.core.JacksonException
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
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

        // Parse the input file as valid JSON into an order-preserving tree so the
        // patched output keeps the original key ordering
        val fabricModJson = try {
            inputFile.reader().use { reader ->
                jarInJarJsonMapper.readTree(reader) as? ObjectNode
                    ?: throw IllegalArgumentException("fabric.mod.json root must be a JSON object.")
            }
        } catch (e: JacksonException) {
            throw problems.reporter.throwingUniversalJarInJarFMJParseFailure(
                e, inputFile.absolutePath
            )
        } catch (e: IllegalArgumentException) {
            throw problems.reporter.throwingUniversalJarInJarFMJParseFailure(
                e, inputFile.absolutePath
            )
        }

        // Patch the fabric.mod.json file to include the embedded jars as "jars" entries.
        val fabricJars = fabricModJson.get("jars") as? ArrayNode
            ?: fabricModJson.putArray("jars")
        jars.forEach { jar ->
            fabricJars.addObject().put("file", jar.path)
        }

        // write the patched fabric.mod.json file to the output file
        val outputFile = outputFabricModJson.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writer().use { writer ->
            jarInJarJsonMapper.writerWithDefaultPrettyPrinter().writeValue(writer, fabricModJson)
        }
    }
}

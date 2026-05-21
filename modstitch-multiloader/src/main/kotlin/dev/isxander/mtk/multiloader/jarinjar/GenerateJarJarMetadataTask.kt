package dev.isxander.mtk.multiloader.jarinjar

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Generates the NeoForge-required `META-INF/jarjar/metadata.json`
 * which tells NeoForge how to load embedded jars.
 */
@CacheableTask
abstract class GenerateJarJarMetadataTask : ResolvedJarConsumerTask() {

    /**
     * The `metadata.json` file to be generated.
     */
    @get:OutputFile
    abstract val metadataFile: RegularFileProperty

    @TaskAction
    fun createMetadata() {
        val jars = readResolvedJars()

        val metadataJson = jarInJarJsonMapper.createObjectNode().apply {
            putArray("jars").apply {
                jars.forEach { jar ->
                    addObject().apply {
                        putObject("identifier").apply {
                            put("group", jar.group)
                            put("artifact", jar.artifact)
                        }

                        putObject("version").apply {
                            put("range", jar.mavenVersionRange)
                            put("artifactVersion", jar.version)
                        }

                        put("path", jar.path)
                        // TODO: investigate if we can easily support obfuscation (doubt)
                        put("isObfuscated", false)
                    }
                }
            }
        }

        val metadataString = jarInJarJsonMapper
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(metadataJson)

        metadataFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(metadataString)
        }
    }
}

package dev.isxander.mtk.multiloader.jarinjar

import com.electronwill.nightconfig.json.JsonFormat
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.LinkedHashMap

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

        val metadataJson = JsonFormat.newConfig(::LinkedHashMap).apply {
            add("jars", jars.map { jar ->
                createSubConfig().apply {
                    add("identifier", createSubConfig().apply {
                        add("group", jar.group)
                        add("artifact", jar.artifact)
                    })

                    add("version", createSubConfig().apply {
                        add("range", jar.mavenVersionRange)
                        add("artifactVersion", jar.version)
                    })

                    add("path", jar.path)
                    // TODO: investigate if we can easily support obfuscation (doubt)
                    add("isObfuscated", false)
                }
            })
        }

        val metadataString = JsonFormat
            .fancyInstance()
            .createWriter()
            .writeToString(metadataJson)

        metadataFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(metadataString)
        }
    }
}

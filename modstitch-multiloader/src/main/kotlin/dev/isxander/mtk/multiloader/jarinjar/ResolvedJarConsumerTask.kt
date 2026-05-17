package dev.isxander.mtk.multiloader.jarinjar

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
abstract class ResolvedJarConsumerTask : DefaultTask() {
    @get:Nested
    abstract val resolvedJars: ListProperty<ResolvedEmbeddedJar>

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val resolvedJarsFile: RegularFileProperty

    protected fun getResolvedJars(): List<ResolvedEmbeddedJar> {
        val jars = mutableListOf<ResolvedEmbeddedJar>()

        if (resolvedJars.isPresent) {
            jars += resolvedJars.get()
        }

        if (resolvedJarsFile.isPresent) {
            // TODO: decode json file to list of jars
        }

        return jars
    }
}
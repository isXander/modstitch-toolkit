package dev.isxander.mtk.multiloader.jarinjar

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
abstract class ResolvedJarConsumerTask : DefaultTask() {
    @get:Nested
    abstract val resolvedJars: ListProperty<ResolvedEmbeddedJar>

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val resolvedJarsFile: RegularFileProperty

    protected fun readResolvedJars(): List<ResolvedEmbeddedJar> {
        val jars = mutableListOf<ResolvedEmbeddedJar>()

        if (resolvedJars.isPresent) {
            jars += resolvedJars.get()
        }

        if (resolvedJarsFile.isPresent) {
            val resolvedJarsMetadata = resolvedJarsFile.get().asFile.reader().use { reader ->
                jarInJarJsonMapper.readTree(reader)
            }

            jars += resolvedJarsMetadata.path("jars").map { jar ->
                ResolvedEmbeddedJar(
                    path = jar.path("path").asString(),
                    group = jar.path("group").asString(),
                    artifact = jar.path("artifact").asString(),
                    classifier = jar.path("classifier")
                        .takeUnless { it.isMissingNode || it.isNull }
                        ?.asString(),
                    version = jar.path("version").asString(),
                    mavenVersionRange = jar.path("mavenVersionRange").asString(),
                )
            }
        }

        return jars
    }
}

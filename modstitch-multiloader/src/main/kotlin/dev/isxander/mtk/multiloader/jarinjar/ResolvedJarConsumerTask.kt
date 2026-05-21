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

            jars += resolvedJarsMetadata.path("jars").values().map { jar ->
                ResolvedEmbeddedJar(
                    path = jar.path("path").stringValue(),
                    group = jar.path("group").stringValue(),
                    artifact = jar.path("artifact").stringValue(),
                    classifier = jar.path("classifier")
                        .takeUnless { it.isMissingNode || it.isNull }
                        ?.stringValue(),
                    version = jar.path("version").stringValue(),
                    mavenVersionRange = jar.path("mavenVersionRange").stringValue(),
                )
            }
        }

        return jars
    }
}

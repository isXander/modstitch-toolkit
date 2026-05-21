package dev.isxander.mtk.multiloader.jarinjar

import com.electronwill.nightconfig.core.Config
import com.electronwill.nightconfig.json.JsonFormat
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

    protected fun readResolvedJars(): List<ResolvedEmbeddedJar> {
        val jars = mutableListOf<ResolvedEmbeddedJar>()

        if (resolvedJars.isPresent) {
            jars += resolvedJars.get()
        }

        if (resolvedJarsFile.isPresent) {
            val resolvedJarsMetadata = resolvedJarsFile.get().asFile.reader().use { reader ->
                JsonFormat.fancyInstance().createParser().parse(reader)
            }

            jars += resolvedJarsMetadata.get<List<Config>>("jars").orEmpty().map { jar ->
                ResolvedEmbeddedJar(
                    path = jar.get("path"),
                    group = jar.get("group"),
                    artifact = jar.get("artifact"),
                    classifier = jar.get("classifier"),
                    version = jar.get("version"),
                    mavenVersionRange = jar.get("mavenVersionRange"),
                )
            }
        }

        return jars
    }
}

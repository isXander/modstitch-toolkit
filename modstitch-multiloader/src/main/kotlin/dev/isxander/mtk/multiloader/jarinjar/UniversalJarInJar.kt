package dev.isxander.mtk.multiloader.jarinjar

import net.neoforged.gradle.userdev.UserDevProjectPlugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.attributes.*
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.register
import javax.inject.Inject

abstract class UniversalJarInJar @Inject constructor(
    private val archiveOperations: ArchiveOperations
) {
    /**
     * Sets up Jar-in-Jar capability for the universal jar.
     *
     * Configurations created:
     * - `universalOnlyInclude`: for dependencies to be JiJ-ed into the universal jar ONLY
     * - `commonInclude`: for dependencies to be JiJ-ed into the universal jar and the loader-specific jars
     * - detached internal configuration
     * `commonInclude` is the one you will use most often. Loom's `fabricInclude` and NeoGradle's `neoforgeJarJar`
     * extend from it, and they are responsible for doing any JiJ-ing natively as you would.
     * This setup is mainly for universal jar only, it allows the toolchain plugins to JiJ normally.
     *
     * Tasks created:
     * - `embedCommonJars`: outputs a directory containing the resolved jars.
     *   This directory should be merged with the universal jar.
     *   These jars have a generated fabric.mod.json within them.
     * - `generateJarJarMetadata`: outputs `META-INF/jarjar/metadata.json` to be included in the universal jar.
     *   This file is read by FML at runtime.
     * - `extractFabricModJson`: extracts the `fabric.mod.json` from the universal jar.
     * - `patchFabricModJson`: patches the file input containing a `fabric.mod.json` to add the `"jars": {}` entry to it
     * - `fatUniversalJar`: creates a copy of the original universal jar, with the embedded jars and any metadata included.
     *
     * The [universalJar] task is then configured to use the outputs of the above tasks to produce a
     * fat jar with the embedded jars included in `universalOnlyInclude`.
     */
    // TODO: naming is all over the place; Jar-in-Jar, embedJars, JarJar, pick one.
    fun setup(
        target: Project,
        neoforgeSourceSet: SourceSet,
        universalJar: TaskProvider<out Jar>,
    ) {
        val universalOnlyIncludeConfig = target.configurations.dependencyScope("universalOnlyInclude")
        val commonIncludeConfig = target.configurations.dependencyScope("commonInclude")

        universalOnlyIncludeConfig {
            extendsFrom(commonIncludeConfig)
        }

        // allow universal Jar-in-Jars to also be picked up by Loom and NeoForge, for the loader-specific jars
        target.configurations.named("fabricInclude") {
            extendsFrom(commonIncludeConfig)
        }
        target.configurations.named(UserDevProjectPlugin.JAR_JAR_DEFAULT_CONFIGURATION_NAME prefixedBy neoforgeSourceSet) {
            extendsFrom(commonIncludeConfig)
        }

        val includeInternalConfig = target.configurations.detachedConfiguration().apply {
            isCanBeResolved = true
            isCanBeConsumed = false

            dependencies.addAllLater(target.provider {
                universalOnlyIncludeConfig.get().incoming.dependencies.map { dependency ->
                    when {
                        dependency.isPlatformDependency -> dependency
                        dependency is ModuleDependency -> dependency.copy().apply { isTransitive = false }
                        else -> dependency
                    }
                }
            })

            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, target.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, target.objects.named(LibraryElements::class.java, LibraryElements.JAR))
                attribute(Category.CATEGORY_ATTRIBUTE, target.objects.named(Category::class.java, Category.LIBRARY))
                attribute(Bundling.BUNDLING_ATTRIBUTE, target.objects.named(Bundling::class.java, Bundling.EXTERNAL))
            }
        }

        val resolveJarsTask = target.tasks.register<ResolveJarsTask>("resolveEmbeddedJarsJars") {
            from(includeInternalConfig)
            outputDirectory = target.layout.buildDirectory.dir("modstitch-multiloader/embedjars/jars")
            resolvedJarsFile = target.layout.buildDirectory.file("modstitch-multiloader/embedjars/resolved-jars.json")
        }

        val generateJarJarMetadataTask = target.tasks.register<GenerateJarJarMetadataTask>("generateJarJarMetadata") {
            resolvedJarsFile = resolveJarsTask.flatMap { it.resolvedJarsFile }
            metadataFile = target.layout.buildDirectory.file("modstitch-multiloader/embedjars/metadata.json")
        }

        val extractFabricModJsonTask = target.tasks.register<ExtractFileFromZipTask>("extractFabricModJson") {
            inputZip = universalJar.flatMap { it.archiveFile }
            pattern = "fabric.mod.json"
            outputFile = target.layout.buildDirectory.file("modstitch-multiloader/embedjars/input-fabric.mod.json")
        }

        val patchFabricModJsonTask = target.tasks.register<PatchFabricModJsonTask>("patchFabricModJson") {
            resolvedJarsFile = resolveJarsTask.flatMap { it.resolvedJarsFile }

            inputFabricModJson = extractFabricModJsonTask.flatMap { it.outputFile }

            outputFabricModJson = target.layout.buildDirectory.file("modstitch-multiloader/embedjars/fabric.mod.json")
        }

        universalJar.configure {
            archiveClassifier = "universal-slim"
        }

        // TODO: it's also a bunch of extra work if there is no JiJ in the configuration; we should disable this task until something is added to the configuration
        target.tasks.register<Jar>("fatUniversalJar") {
            group = "build"
            archiveClassifier = "universal"

            // include all contents of the original universal jar
            from(universalJar.flatMap { it.archiveFile }.map { archiveOperations.zipTree(it) }) {
                exclude("fabric.mod.json") // replacing with patched version
            }

            // include the embedded jars themselves
            from(resolveJarsTask.flatMap { it.outputDirectory })

            // include `META-INF/jarjar/metadata.json` used for NeoForge
            from(generateJarJarMetadataTask.flatMap { it.metadataFile }) {
                into("META-INF/jarjar")
                rename { "metadata.json" }
            }

            // include the patched `fabric.mod.json` for Fabric Loader
            from(patchFabricModJsonTask.flatMap { it.outputFabricModJson }) {
                into("")
                rename { "fabric.mod.json" }
            }
        }
    }

    private infix fun String.prefixedBy(sourceSet: SourceSet): String {
        val suffix = this
        if (sourceSet.name == SourceSet.MAIN_SOURCE_SET_NAME) return suffix
        return "${sourceSet.name}${suffix.replaceFirstChar { it.titlecaseChar() }}"
    }

    private val Dependency.isPlatformDependency: Boolean
        get() = (this as? HasConfigurableAttributes<*>)
            ?.attributes
            ?.getAttribute(Category.CATEGORY_ATTRIBUTE)
            ?.name
            ?.let { it in listOf(Category.ENFORCED_PLATFORM, Category.REGULAR_PLATFORM) } == true
}

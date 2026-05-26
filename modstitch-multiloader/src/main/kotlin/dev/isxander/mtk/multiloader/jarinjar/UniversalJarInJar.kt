package dev.isxander.mtk.multiloader.jarinjar

import dev.isxander.mtk.multiloader.utils.sourceSets
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.attributes.*
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.invoke
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.jvm.tasks.ProcessResources
import javax.inject.Inject

abstract class UniversalJarInJar @Inject constructor() {
    fun setup(
        target: Project,
        universalJar: TaskProvider<out Jar>,
    ) {
        val fabricIncludeConfig = target.configurations.named("fabricInclude")
        val neoforgeIncludeConfig = target.configurations.named("neoforgeInclude")
        val commonIncludeConfig = target.configurations.dependencyScope("commonInclude")

        fabricIncludeConfig {
            extendsFrom(commonIncludeConfig.get())
        }
        neoforgeIncludeConfig {
            extendsFrom(commonIncludeConfig.get())
        }

        val embeddedInternalConfig = includeInternalConfig(
            target,
            fabricIncludeConfig,
            neoforgeIncludeConfig,
        )
        val fabricMetadataInternalConfig = includeInternalConfig(
            target,
            fabricIncludeConfig,
        )
        val neoforgeMetadataInternalConfig = includeInternalConfig(
            target,
            neoforgeIncludeConfig,
        )

        val resolveJarsTask = target.tasks.register<ResolveJarsTask>("resolveEmbeddedJarsJars") {
            embeddedFrom(embeddedInternalConfig)
            fabricFrom(fabricMetadataInternalConfig)
            neoforgeFrom(neoforgeMetadataInternalConfig)
            outputDirectory = target.layout.buildDirectory.dir("modstitch-multiloader/embedjars/jars")
            fabricResolvedJarsFile =
                target.layout.buildDirectory.file("modstitch-multiloader/embedjars/fabric-resolved-jars.json")
            neoforgeResolvedJarsFile =
                target.layout.buildDirectory.file("modstitch-multiloader/embedjars/neoforge-resolved-jars.json")
        }

        val generateJarJarMetadataTask = target.tasks.register<GenerateJarJarMetadataTask>("generateJarJarMetadata") {
            resolvedJarsFile = resolveJarsTask.flatMap { it.neoforgeResolvedJarsFile }
            metadataFile = target.layout.buildDirectory.file("modstitch-multiloader/embedjars/metadata.json")
        }

        val main = target.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        val fabric = target.sourceSets.getByName("fabric")
        val neoforge = target.sourceSets.getByName("neoforge")
        val processMainResources = target.tasks.named<ProcessResources>(main.processResourcesTaskName)
        val processFabricResources = target.tasks.named<ProcessResources>(fabric.processResourcesTaskName)
        val processNeoforgeResources = target.tasks.named<ProcessResources>(neoforge.processResourcesTaskName)
        val fabricModJson = target.layout.file(
            processFabricResources.map { it.destinationDir.resolve("fabric.mod.json") },
        )

        val patchFabricModJsonTask = target.tasks.register<PatchFabricModJsonTask>("patchFabricModJson") {
            resolvedJarsFile = resolveJarsTask.flatMap { it.fabricResolvedJarsFile }

            inputFabricModJson = fabricModJson

            outputFabricModJson = target.layout.buildDirectory.file("modstitch-multiloader/embedjars/fabric.mod.json")
        }

        val processUniversalResources = target.tasks.register<ProcessResources>("processUniversalResources") {
            duplicatesStrategy = DuplicatesStrategy.FAIL
            destinationDir = target.layout.buildDirectory.dir("modstitch-multiloader/universal-resources").get().asFile

            from(processMainResources.map { it.destinationDir })
            from(processFabricResources.map { it.destinationDir }) {
                exclude("fabric.mod.json")
            }
            from(processNeoforgeResources.map { it.destinationDir })

            from(resolveJarsTask.flatMap { it.outputDirectory })
            from(generateJarJarMetadataTask.flatMap { it.metadataFile }) {
                into("META-INF/jarjar")
                rename { "metadata.json" }
            }
            from(patchFabricModJsonTask.flatMap { it.outputFabricModJson }) {
                into("")
                rename { "fabric.mod.json" }
            }
        }

        universalJar.configure {
            from(processUniversalResources)
        }
    }

    private fun includeInternalConfig(
        target: Project,
        vararg sourceConfigurations: NamedDomainObjectProvider<out Configuration>,
    ): Configuration = target.configurations.detachedConfiguration().apply {
        isCanBeResolved = true
        isCanBeConsumed = false

        dependencies.addAllLater(target.provider {
            sourceConfigurations.flatMap { sourceConfiguration ->
                sourceConfiguration.get().allDependencies.map { dependency ->
                    dependency.copyForIncludeResolution()
                }
            }
        })

        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, target.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
            attribute(
                LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                target.objects.named(LibraryElements::class.java, LibraryElements.JAR),
            )
            attribute(Category.CATEGORY_ATTRIBUTE, target.objects.named(Category::class.java, Category.LIBRARY))
            attribute(Bundling.BUNDLING_ATTRIBUTE, target.objects.named(Bundling::class.java, Bundling.EXTERNAL))
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

    private fun Dependency.copyForIncludeResolution(): Dependency =
        when {
            isPlatformDependency -> copy()
            this is ModuleDependency -> copy().apply { isTransitive = false }
            else -> copy()
        }
}

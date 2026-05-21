package dev.isxander.mtk.multiloader.jarinjar

import com.electronwill.nightconfig.json.JsonFormat
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.LinkedHashMap
import javax.inject.Inject

/**
 * Resolves an include configuration and outputs the jars into a folder,
 * read for embedding into the universal jar.
 *
 * Outputs resolved jar metadata to [resolvedJarsFile] for use by the loader-specific metadata generation tasks.
 */
@DisableCachingByDefault(because = "The dependency resolution graph is part of this task's input.")
abstract class ResolveJarsTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val configurations: ConfigurableFileCollection

    @get:Input
    abstract val dependencyMetadata: ListProperty<String>

    @get:Internal
    abstract val resolvedArtifacts: SetProperty<ResolvedArtifactResult>

    @get:Internal
    abstract val rootComponents: SetProperty<ResolvedComponentResult>

    /**
     * The directory within the jar that holds the embedded jars.
     */
    @get:Input
    abstract val jarPath: Property<String>

    init {
        jarPath.convention("META-INF/embeddedJars")
        dependencyMetadata.convention(emptyList())
    }

    /**
     * Output directory that should be merged with jars, it includes the embedded jars.
     * Relative to this directory, jars are located at [jarPath]/<examplename>.jar.
     *
     * This is intended to be sourced by the relevant source set's resources.
     *
     * This does not include NeoForge's `META-INF/jarjar/metadata.json` or
     * Fabric's modified `fabric.mod.json` files.
     */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /**
     * Contains structured metadata about the embedded jars and their location.
     * This output is intended to be used as inputs by the loader-specific metadata generation tasks.
     *
     * The file contains JSON metadata for [ResolvedEmbeddedJar]s.
     */
    @get:OutputFile
    abstract val resolvedJarsFile: RegularFileProperty

    @get:Inject
    protected abstract val fileSystemOperations: FileSystemOperations

    fun from(configuration: Configuration) {
        val artifacts = configuration.incoming.artifactView {
            attributes.attribute(
                ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                ArtifactTypeDefinition.JAR_TYPE,
            )
        }

        configurations.from(artifacts.files)
        resolvedArtifacts.addAll(artifacts.artifacts.resolvedArtifacts)
        rootComponents.add(configuration.incoming.resolutionResult.rootComponent)
        dependencyMetadata.addAll(configuration.incoming.resolutionResult.rootComponent.map(::fingerprint))
        dependsOn(configuration)
    }

    @TaskAction
    fun embedJars() {
        val requestedMetadata = resolveRequestedMetadata(rootComponents.get())
        val embeddedJars = resolvedArtifacts.get()
            .mapNotNull { artifact -> resolveArtifact(artifact, requestedMetadata) }
            .sortedWith(compareBy({ it.metadata.group }, { it.metadata.artifact }, { it.metadata.classifier.orEmpty() }, { it.metadata.path }))

        val resolvedFiles = embeddedJars.mapTo(mutableSetOf()) { it.inputFile }
        val unhandledFiles = configurations.files - resolvedFiles
        if (unhandledFiles.isNotEmpty()) {
            throw GradleException(
                "Cannot create universal Jar-in-Jar metadata for ${unhandledFiles.joinToString { it.name }}. " +
                    "Use module or project dependencies so Modstitch can resolve coordinates and version ranges.",
            )
        }

        val outputDirectory = outputDirectory.get().asFile
        fileSystemOperations.delete { delete(outputDirectory) }
        outputDirectory.mkdirs()

        val paths = mutableSetOf<String>()
        embeddedJars.forEach { jar ->
            if (!paths.add(jar.metadata.path)) {
                throw GradleException("Trying to embed multiple jars at ${jar.metadata.path}.")
            }

            val outputFile = outputDirectory.resolve(jar.metadata.path)
            outputFile.parentFile.mkdirs()
            jar.inputFile.copyTo(outputFile, overwrite = true)
            addFabricModJsonIfMissing(outputFile, jar.metadata)
        }

        writeResolvedJars(embeddedJars.map(ResolvedJar::metadata))
    }

    private fun resolveArtifact(
        artifact: ResolvedArtifactResult,
        requestedMetadata: Map<JarIdentifier, RequestedMetadata>,
    ): ResolvedJar? {
        val coordinates = artifact.variant.coordinates() ?: return null
        val identifier = JarIdentifier(coordinates.group, coordinates.artifact)
        val versionRange = requestedMetadata[identifier]?.versionRange
            ?: openRange(coordinates.version)
        val classifier = artifact.classifier(coordinates)
        val path = embeddedPath(artifact.file.name)

        return ResolvedJar(
            artifact.file,
            ResolvedEmbeddedJar(
                path = path,
                group = coordinates.group,
                artifact = coordinates.artifact,
                classifier = classifier,
                version = coordinates.version,
                mavenVersionRange = VersionRange.parseMaven(versionRange).toMaven(),
            ),
        )
    }

    private fun resolveRequestedMetadata(rootComponents: Set<ResolvedComponentResult>): Map<JarIdentifier, RequestedMetadata> {
        val metadata = mutableMapOf<JarIdentifier, RequestedMetadata>()

        rootComponents.forEach { rootComponent ->
            rootComponent.dependencies
                .filterIsInstance<ResolvedDependencyResult>()
                .forEach { dependency ->
                    val variant = dependency.resolvedVariant.externalVariant()
                    val coordinates = variant.coordinates() ?: return@forEach
                    val identifier = JarIdentifier(coordinates.group, coordinates.artifact)
                    val range = dependency.requested.mavenVersionRange()
                        ?: openRange(coordinates.version)

                    metadata[identifier] = RequestedMetadata(
                        versionRange = VersionRange.parseMaven(range).toMaven(),
                    )
                }
        }

        return metadata
    }

    private fun fingerprint(rootComponent: ResolvedComponentResult): List<String> =
        rootComponent.dependencies
            .filterIsInstance<ResolvedDependencyResult>()
            .mapNotNull { dependency ->
                val coordinates = dependency.resolvedVariant.externalVariant().coordinates()
                    ?: return@mapNotNull null
                val range = dependency.requested.mavenVersionRange().orEmpty()
                "${coordinates.group}:${coordinates.artifact}:${coordinates.version}|$range"
            }
            .sorted()

    private fun writeResolvedJars(jars: List<ResolvedEmbeddedJar>) {
        val metadataFile = resolvedJarsFile.get().asFile
        metadataFile.parentFile.mkdirs()

        val json = JsonFormat.newConfig(::LinkedHashMap).apply {
            add("jars", jars.map { jar ->
                createSubConfig().apply {
                    add("path", jar.path)
                    add("group", jar.group)
                    add("artifact", jar.artifact)
                    jar.classifier?.let { add("classifier", it) }
                    add("version", jar.version)
                    add("mavenVersionRange", jar.mavenVersionRange)
                }
            })
        }

        metadataFile.writer().use { writer ->
            JsonFormat.fancyInstance().createWriter().write(json, writer)
        }
    }

    private fun addFabricModJsonIfMissing(jar: File, metadata: ResolvedEmbeddedJar) {
        FileSystems.newFileSystem(jar.toPath(), emptyMap<String, Any>()).use { jarFileSystem ->
            val fabricModJsonPath = jarFileSystem.getPath("fabric.mod.json")
            if (Files.exists(fabricModJsonPath)) return

            Files.writeString(fabricModJsonPath, metadata.generatedFabricModJson())
        }
    }

    private fun ResolvedEmbeddedJar.generatedFabricModJson(): String {
        val json = JsonFormat.newConfig(::LinkedHashMap).apply {
            add("schemaVersion", 1)
            add("id", fabricModId)
            add("version", fabricVersion)
            add("name", artifact)
            add("custom", createSubConfig().apply {
                add("fabric-loom:generated", true)
            })
        }

        return JsonFormat.fancyInstance().createWriter().writeToString(json)
    }

    private fun embeddedPath(fileName: String): String =
        "${jarPath.get().trim('/')}/$fileName"

    private fun ResolvedArtifactResult.classifier(coordinates: Coordinates): String? {
        val prefix = "${coordinates.artifact}-${coordinates.version}-"
        if (!file.name.startsWith(prefix)) return null

        return file.name
            .removePrefix(prefix)
            .substringBefore('.')
            .takeIf(String::isNotBlank)
    }

    private fun ComponentSelector.mavenVersionRange(): String? =
        (this as? ModuleComponentSelector)
            ?.versionConstraint
            ?.let { constraint ->
                sequenceOf(
                    constraint.strictVersion,
                    constraint.requiredVersion,
                    constraint.preferredVersion,
                    version,
                ).firstOrNull { it.isNotBlank() }
            }

    private fun ResolvedVariantResult.externalVariant(): ResolvedVariantResult {
        var variant = this
        while (variant.externalVariant.isPresent) {
            variant = variant.externalVariant.get()
        }
        return variant
    }

    private fun ResolvedVariantResult.coordinates(): Coordinates? {
        val moduleCoordinates = (owner as? ModuleComponentIdentifier)?.let { owner ->
            Coordinates(owner.group, owner.module, owner.version)
        }
        val capabilityCoordinates = capabilities.mapNotNull { capability ->
            capability.version?.let { version ->
                Coordinates(capability.group, capability.name, version)
            }
        }

        return when {
            moduleCoordinates != null && moduleCoordinates in capabilityCoordinates -> moduleCoordinates
            capabilityCoordinates.isNotEmpty() -> capabilityCoordinates.first()
            else -> moduleCoordinates
        }
    }

    private fun openRange(version: String): String = "[$version,)"

    private data class ResolvedJar(
        val inputFile: File,
        val metadata: ResolvedEmbeddedJar,
    )

    private data class RequestedMetadata(
        val versionRange: String,
    )

    private data class JarIdentifier(
        val group: String,
        val artifact: String,
    )

    private data class Coordinates(
        val group: String,
        val artifact: String,
        val version: String,
    )
}

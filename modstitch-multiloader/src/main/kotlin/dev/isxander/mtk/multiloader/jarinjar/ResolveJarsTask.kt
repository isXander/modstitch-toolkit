package dev.isxander.mtk.multiloader.jarinjar

import com.electronwill.nightconfig.json.JsonFormat
import dev.isxander.mtk.multiloader.utils.throwingUniversalJarInJarDuplicatePath
import dev.isxander.mtk.multiloader.utils.throwingUniversalJarInJarMissingCoordinates
import org.gradle.api.DefaultTask
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
import org.gradle.api.problems.Problems
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
 * Resolves universal Jar-in-Jar dependencies into files and metadata that can be
 * consumed by both loader-specific metadata tasks.
 *
 * The task intentionally owns this resolution step instead of delegating to Loom
 * or NeoForge JarJar. Both tools know how to build metadata for their own jar,
 * but the universal jar must embed each dependency once and then describe that
 * same copy to both loaders.
 *
 * [outputDirectory] contains the jars to merge into the universal jar.
 * [resolvedJarsFile] records the resolved coordinates, version ranges, and
 * embedded paths needed when generating Fabric and NeoForge metadata later.
 */
@DisableCachingByDefault(because = "The dependency resolution graph is part of this task's input.")
abstract class ResolveJarsTask : DefaultTask() {

    /**
     * The resolved jar files to embed.
     *
     * File inputs let Gradle notice when dependency contents change. The
     * resolution graph is kept separately because it carries the coordinates and
     * requested ranges that cannot be recovered from the file collection.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val configurations: ConfigurableFileCollection

    /**
     * A stable fingerprint of the direct dependency requests feeding this task.
     *
     * Gradle result objects are not task inputs. This mirrors the coordinate and
     * range data that affects generated metadata while [configurations] tracks
     * the actual jar files.
     */
    @get:Input
    abstract val dependencyMetadata: ListProperty<String>

    /**
     * Resolved jar artifacts used at execution time to connect files back to
     * their Gradle component metadata.
     */
    @get:Internal
    abstract val resolvedArtifacts: SetProperty<ResolvedArtifactResult>

    /**
     * Resolution roots used to recover the version range requested by the build
     * author before Gradle selected a concrete artifact version.
     */
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

    @get:Inject
    protected abstract val problems: Problems

    /**
     * Adds a configuration to the universal Jar-in-Jar resolution.
     *
     * The artifact view filters the configuration to jar artifacts. The result
     * still keeps the resolution graph because loader metadata needs module
     * coordinates and requested Maven ranges, not only files to copy.
     */
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
        // Artifact results describe the selected version. Start from the
        // resolution roots so we can keep the direct dependency's requested
        // version range in the loader metadata.
        val requestedMetadata = resolveRequestedMetadata(rootComponents.get())
        val embeddedJars = resolvedArtifacts.get()
            .mapNotNull { artifact -> resolveArtifact(artifact, requestedMetadata) }
            .sortedWith(compareBy({ it.metadata.group }, { it.metadata.artifact }, { it.metadata.classifier.orEmpty() }, { it.metadata.path }))

        // A file without coordinates cannot be represented in NeoForge JarJar
        // metadata or given a useful generated Fabric mod id.
        val resolvedFiles = embeddedJars.mapTo(mutableSetOf()) { it.inputFile }
        val unhandledFiles = configurations.files - resolvedFiles
        if (unhandledFiles.isNotEmpty()) {
            throw problems.reporter.throwingUniversalJarInJarMissingCoordinates(
                unhandledFiles.map(File::getName),
            )
        }

        val outputDirectory = outputDirectory.get().asFile
        fileSystemOperations.delete { delete(outputDirectory) }
        outputDirectory.mkdirs()

        val paths = mutableSetOf<String>()
        embeddedJars.forEach { jar ->
            if (!paths.add(jar.metadata.path)) {
                throw problems.reporter.throwingUniversalJarInJarDuplicatePath(jar.metadata.path)
            }

            val outputFile = outputDirectory.resolve(jar.metadata.path)
            outputFile.parentFile.mkdirs()
            jar.inputFile.copyTo(outputFile, overwrite = true)
            // Fabric treats nested jars as mods. Plain library jars need the
            // minimal Loom-style metadata marker so Fabric can load them.
            addFabricModJsonIfMissing(outputFile, jar.metadata)
        }

        writeResolvedJars(embeddedJars.map(ResolvedJar::metadata))
    }

    /**
     * Converts a resolved artifact into the common record used by copy and
     * metadata tasks.
     *
     * The resolved variant supplies the selected coordinates. Its requested
     * range is recovered from the root dependency map when present; transitive
     * artifacts fall back to an open range starting at the selected version.
     */
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

    /**
     * Captures metadata that only exists on the dependency requests at each
     * configured resolution root.
     *
     * The direct request is the range the project author declared. Transitives
     * are still embedded as resolved artifacts, but their selected version is the
     * only unambiguous range available at this task boundary.
     */
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

    /**
     * Mirrors the graph data that influences generated metadata into a regular
     * task input so dependency range changes invalidate the task.
     */
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

    /**
     * Serialises the common resolution result for the later Fabric and NeoForge
     * metadata tasks.
     */
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

    /**
     * Adds Loom-compatible synthetic Fabric metadata to plain nested libraries.
     *
     * Existing Fabric metadata wins. Generated metadata only exists to make
     * regular library jars valid nested Fabric mods in the universal jar.
     */
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

    /**
     * Gradle's artifact result does not expose a Maven classifier directly, so
     * infer the conventional classifier suffix from the selected jar name.
     */
    private fun ResolvedArtifactResult.classifier(coordinates: Coordinates): String? {
        val prefix = "${coordinates.artifact}-${coordinates.version}-"
        if (!file.name.startsWith(prefix)) return null

        return file.name
            .removePrefix(prefix)
            .substringBefore('.')
            .takeIf(String::isNotBlank)
    }

    /**
     * Reads the strongest available Gradle selector constraint as the Maven
     * range to carry into NeoForge JarJar metadata.
     */
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

    /**
     * Variants produced by artifact views can wrap the selected external
     * variant. Walk through wrappers before reading component coordinates.
     */
    private fun ResolvedVariantResult.externalVariant(): ResolvedVariantResult {
        var variant = this
        while (variant.externalVariant.isPresent) {
            variant = variant.externalVariant.get()
        }
        return variant
    }

    /**
     * Finds Maven-like coordinates for both module and project variants.
     *
     * Published modules expose a [ModuleComponentIdentifier]. Project
     * dependencies commonly surface coordinates through capabilities instead.
     * Prefer the module identity when capabilities agree with it.
     */
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

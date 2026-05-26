package dev.isxander.mtk.multiloader.jarinjar

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
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.problems.Problems
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
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
 * [fabricResolvedJarsFile] and [neoforgeResolvedJarsFile] record the resolved
 * coordinates, version ranges, and embedded paths needed when generating
 * loader-specific metadata later.
 */
@CacheableTask
abstract class ResolveJarsTask @Inject constructor(
    objects: ObjectFactory,
) : DefaultTask() {
    @get:Nested
    val embeddedResolution: ResolvedJarResolution = objects.newInstance(ResolvedJarResolution::class.java)

    @get:Nested
    val fabricResolution: ResolvedJarResolution = objects.newInstance(ResolvedJarResolution::class.java)

    @get:Nested
    val neoforgeResolution: ResolvedJarResolution = objects.newInstance(ResolvedJarResolution::class.java)

    /**
     * The directory within the jar that holds the embedded jars.
     */
    @get:Input
    abstract val jarPath: Property<String>

    init {
        jarPath.convention("META-INF/embeddedJars")
        embeddedResolution.jarPath.set(jarPath)
        fabricResolution.jarPath.set(jarPath)
        neoforgeResolution.jarPath.set(jarPath)
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
     * Contains structured metadata about the embedded jars Fabric should load.
     * This output is intended to be used as input by the Fabric metadata generation task.
     *
     * The file contains JSON metadata for [ResolvedEmbeddedJar]s.
     */
    @get:OutputFile
    abstract val fabricResolvedJarsFile: RegularFileProperty

    /**
     * Contains structured metadata about the embedded jars NeoForge should load.
     * This output is intended to be used as input by the NeoForge metadata generation task.
     *
     * The file contains JSON metadata for [ResolvedEmbeddedJar]s.
     */
    @get:OutputFile
    abstract val neoforgeResolvedJarsFile: RegularFileProperty

    @get:Inject
    protected abstract val fileSystemOperations: FileSystemOperations

    @get:Inject
    protected abstract val problems: Problems

    /**
     * Adds a configuration to the universal Jar-in-Jar resolution.
     *
     * The artifact view filters the configuration to jar artifacts. The nested
     * resolution model exposes the selected files and metadata as task inputs.
     */
    fun from(configuration: Configuration) {
        embeddedFrom(configuration)
        fabricFrom(configuration)
        neoforgeFrom(configuration)
    }

    /**
     * Adds a configuration to the physical universal jar embedding resolution.
     */
    fun embeddedFrom(configuration: Configuration) {
        embeddedResolution.from(configuration)
        dependsOn(configuration)
    }

    /**
     * Adds a configuration to the Fabric loader metadata resolution.
     */
    fun fabricFrom(configuration: Configuration) {
        fabricResolution.from(configuration)
        dependsOn(configuration)
    }

    /**
     * Adds a configuration to the NeoForge loader metadata resolution.
     */
    fun neoforgeFrom(configuration: Configuration) {
        neoforgeResolution.from(configuration)
        dependsOn(configuration)
    }

    @TaskAction
    fun embedJars() {
        val embeddedJars = readResolvedJars(embeddedResolution)
        val fabricJars = readResolvedJars(fabricResolution)
        val neoforgeJars = readResolvedJars(neoforgeResolution)

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

        writeResolvedJars(fabricResolvedJarsFile, fabricJars.map(ResolvedJar::metadata))
        writeResolvedJars(neoforgeResolvedJarsFile, neoforgeJars.map(ResolvedJar::metadata))
    }

    private fun readResolvedJars(resolution: ResolvedJarResolution): List<ResolvedJar> {
        return try {
            resolution.resolvedJars.get()
        } catch (e: MissingCoordinatesException) {
            throw problems.reporter.throwingUniversalJarInJarMissingCoordinates(e.fileNames)
        }
    }

    /**
     * Serialises the common resolution result for the later Fabric and NeoForge
     * metadata tasks.
     */
    private fun writeResolvedJars(outputFile: RegularFileProperty, jars: List<ResolvedEmbeddedJar>) {
        val metadataFile = outputFile.get().asFile
        metadataFile.parentFile.mkdirs()

        val json = jarInJarJsonMapper.createObjectNode().apply {
            putArray("jars").apply {
                jars.forEach { jar ->
                    addObject().apply {
                        put("path", jar.path)
                        put("group", jar.group)
                        put("artifact", jar.artifact)
                        jar.classifier?.let { put("classifier", it) }
                        put("version", jar.version)
                        put("mavenVersionRange", jar.mavenVersionRange)
                    }
                }
            }
        }

        metadataFile.writer().use { writer ->
            jarInJarJsonMapper.writerWithDefaultPrettyPrinter().writeValue(writer, json)
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
        val json = jarInJarJsonMapper.createObjectNode().apply {
            put("schemaVersion", 1)
            put("id", fabricModId)
            put("version", fabricVersion)
            put("name", artifact)
            putObject("custom").put("fabric-loom:generated", true)
        }

        return jarInJarJsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json)
    }

    data class ResolvedJar(
        @get:InputFile
        @get:PathSensitive(PathSensitivity.NAME_ONLY)
        val inputFile: File,

        @get:Nested
        val metadata: ResolvedEmbeddedJar,
    )

    abstract class ResolvedJarResolution @Inject constructor(
        objects: ObjectFactory,
    ) {
        /**
         * Resolution handles stay transient and internal. The task input is the
         * resolved, nested [resolvedJars] model derived from them.
         */
        @Transient
        private val resolvedArtifacts: SetProperty<ResolvedArtifactResult> =
            objects.setProperty(ResolvedArtifactResult::class.java)

        @Transient
        private val rootComponents: SetProperty<ResolvedComponentResult> =
            objects.setProperty(ResolvedComponentResult::class.java)

        @get:Internal
        abstract val jarPath: Property<String>

        @get:Nested
        abstract val resolvedJars: ListProperty<ResolvedJar>

        init {
            resolvedArtifacts.finalizeValueOnRead()
            rootComponents.finalizeValueOnRead()
            resolvedJars.finalizeValueOnRead()
            resolvedJars.set(
                rootComponents.zip(resolvedArtifacts) { rootComponents, artifacts ->
                    resolveJars(rootComponents, artifacts)
                },
            )
        }

        fun from(configuration: Configuration) {
            val artifacts = configuration.incoming.artifactView {
                attributes.attribute(
                    ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                    ArtifactTypeDefinition.JAR_TYPE,
                )
            }

            resolvedArtifacts.addAll(artifacts.artifacts.resolvedArtifacts)
            rootComponents.add(configuration.incoming.resolutionResult.rootComponent)
        }

        private fun resolveJars(
            rootComponents: Set<ResolvedComponentResult>,
            artifacts: Set<ResolvedArtifactResult>,
        ): List<ResolvedJar> {
            val requestedMetadata = resolveRequestedMetadata(rootComponents)
            val missingCoordinates = mutableListOf<String>()

            val jars = artifacts
                .mapNotNull { artifact ->
                    resolveArtifact(artifact, requestedMetadata)
                        ?: run {
                            missingCoordinates += artifact.file.name
                            null
                        }
                }
                .sortedWith(compareBy({ it.metadata.group }, { it.metadata.artifact }, { it.metadata.classifier.orEmpty() }, { it.metadata.path }))

            if (missingCoordinates.isNotEmpty()) {
                throw MissingCoordinatesException(missingCoordinates)
            }

            return jars
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
            val path = "${jarPath.get().trim('/')}/${artifact.file.name}"

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
         * Gradle's artifact result does not expose a Maven classifier directly,
         * so infer the conventional classifier suffix from the selected jar name.
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

    private class MissingCoordinatesException(
        val fileNames: Collection<String>,
    ) : RuntimeException("Missing coordinates for ${fileNames.joinToString()}")
}

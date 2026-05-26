package dev.isxander.mtk.moddeps

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files

@CacheableTask
abstract class GenerateModDependencySetTask : DefaultTask() {
    @get:Input
    abstract val loaderKind: Property<ModLoaderKind>

    @get:Nested
    abstract val declaredDependencies: ListProperty<DeclaredModDependencyInfo>

    @get:Nested
    abstract val selectedArtifacts: ListProperty<ResolvedModDependencyArtifact>

    @get:Input
    abstract val configurationName: Property<String>

    @get:OutputFile
    abstract val metadataFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val artifactsByCoordinates = selectedArtifacts.get()
            .associateBy { artifact -> DependencyCoordinates(artifact.group, artifact.name) }

        val resolvedDependencies = declaredDependencies.get().map { declared ->
            val artifact = artifactsByCoordinates[DependencyCoordinates(declared.group, declared.name)]
                ?: throw GradleException(
                    "Could not find selected jar artifact for ${declared.displayName()} in configuration ${configurationName.get()}.",
                )
            val loaderModId = declared.explicitModId
                ?: inferLoaderModId(artifact.artifactFile, declared, artifact.selectedVersion)

            ResolvedModDependencyInfo(
                group = declared.group,
                name = declared.name,
                selectedVersion = artifact.selectedVersion,
                loaderKind = loaderKind.get(),
                loaderModId = loaderModId,
                declaredVersionRange = declared.declaredVersionRange,
                relationship = declared.relationship,
                modrinthProject = declared.modrinthProject,
                curseForgeProject = declared.curseForgeProject,
            )
        }

        writeMetadata(resolvedDependencies)
    }

    private fun inferLoaderModId(
        jar: File,
        dependency: DeclaredModDependencyInfo,
        selectedVersion: String?,
    ): String =
        when (loaderKind.get()) {
            ModLoaderKind.Fabric -> inferFabricModId(jar)
                ?: throw GradleException(
                    "Could not infer Fabric mod id for ${dependency.displayName(selectedVersion)}. " +
                            "The selected artifact does not contain fabric.mod.json or it does not define `id`. " +
                            "Either use a Fabric mod jar or declare an explicit mod id.",
                )

            ModLoaderKind.NeoForge -> inferNeoForgeModId(jar)
                ?: throw GradleException(
                    "Could not infer NeoForge mod id for ${dependency.displayName(selectedVersion)}. " +
                            "The selected artifact does not contain META-INF/neoforge.mods.toml or no mod id could be read. " +
                            "Either use a NeoForge mod jar or declare an explicit mod id.",
                )
        }

    private fun inferFabricModId(jar: File): String? =
        FileSystems.newFileSystem(jar.toPath(), emptyMap<String, Any>()).use { fileSystem ->
            val fabricModJson = fileSystem.getPath("fabric.mod.json")
            if (!Files.exists(fabricModJson)) return null

            Files.newBufferedReader(fabricModJson).use { reader ->
                modDependencyJsonMapper.readTree(reader)
                    .path("id")
                    .takeUnless { it.isMissingNode || it.isNull }
                    ?.stringValue()
                    ?.takeIf(String::isNotBlank)
            }
        }

    private fun inferNeoForgeModId(jar: File): String? =
        FileSystems.newFileSystem(jar.toPath(), emptyMap<String, Any>()).use { fileSystem ->
            val modsToml = fileSystem.getPath("META-INF/neoforge.mods.toml")
            if (!Files.exists(modsToml)) return null

            Files.newBufferedReader(modsToml).use { reader ->
                val root = modDependencyTomlMapper.readTree(reader)
                root.path("mods")
                    .values()
                    .asSequence()
                    .mapNotNull { mod ->
                        mod.path("modId")
                            .takeUnless { it.isMissingNode || it.isNull }
                            ?.stringValue()
                            ?.takeIf(String::isNotBlank)
                    }
                    .firstOrNull()
            }
        }

    private fun writeMetadata(dependencies: List<ResolvedModDependencyInfo>) {
        val outputFile = metadataFile.get().asFile
        outputFile.parentFile.mkdirs()

        val json = modDependencyJsonMapper.createObjectNode().apply {
            put("loaderKind", loaderKind.get().name)
            put("configurationName", configurationName.get())
            putArray("dependencies").apply {
                dependencies.forEach { dependency ->
                    addObject().apply {
                        putNullable("group", dependency.group)
                        put("name", dependency.name)
                        putNullable("selectedVersion", dependency.selectedVersion)
                        put("loaderKind", dependency.loaderKind.name)
                        put("loaderModId", dependency.loaderModId)
                        putNullable("declaredVersionRange", dependency.declaredVersionRange)
                        put("relationship", dependency.relationship.name)
                        putNullable("modrinthProject", dependency.modrinthProject)
                        putNullable("curseForgeProject", dependency.curseForgeProject)
                    }
                }
            }
        }

        outputFile.writer().use { writer ->
            modDependencyJsonMapper.writerWithDefaultPrettyPrinter().writeValue(writer, json)
        }
    }

    private fun DeclaredModDependencyInfo.displayName(selectedVersion: String? = null): String =
        listOfNotNull(group, name, selectedVersion).joinToString(":")

    private fun tools.jackson.databind.node.ObjectNode.putNullable(name: String, value: String?) {
        if (value == null) {
            putNull(name)
        } else {
            put(name, value)
        }
    }

    private data class DependencyCoordinates(
        val group: String?,
        val name: String,
    )
}

package dev.isxander.mtk.moddeps

import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.File

enum class ModLoaderKind {
    Fabric,
    NeoForge,
}

enum class ModDependencyRelationship {
    Required,
    Optional,
    Incompatible,
    Embedded,
}

interface ModDependencySet {
    val loaderKind: ModLoaderKind
    val configurationName: String
    val metadataFile: Provider<RegularFile>
    val dependencies: Provider<List<ResolvedModDependencyInfo>>
}

data class ResolvedModDependencyInfo(
    val group: String?,
    val name: String,
    val selectedVersion: String?,
    val loaderKind: ModLoaderKind,
    val loaderModId: String,
    val declaredVersionRange: String?,
    val relationship: ModDependencyRelationship,
    val modrinthProject: String?,
    val curseForgeProject: String?,
)

data class DeclaredModDependencyInfo(
    @get:Input
    @get:Optional
    val group: String?,

    @get:Input
    val name: String,

    @get:Input
    @get:Optional
    val declaredVersionRange: String?,

    @get:Input
    val relationship: ModDependencyRelationship,

    @get:Input
    @get:Optional
    val explicitModId: String?,

    @get:Input
    @get:Optional
    val modrinthProject: String?,

    @get:Input
    @get:Optional
    val curseForgeProject: String?,
)

data class ResolvedModDependencyArtifact(
    @get:Input
    @get:Optional
    val group: String?,

    @get:Input
    val name: String,

    @get:Input
    @get:Optional
    val selectedVersion: String?,

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    val artifactFile: File,
)

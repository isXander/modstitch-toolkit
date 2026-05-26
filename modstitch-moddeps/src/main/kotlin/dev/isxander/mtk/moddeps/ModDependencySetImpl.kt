package dev.isxander.mtk.moddeps

import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider

internal class DefaultModDependencySet(
    override val loaderKind: ModLoaderKind,
    override val configurationName: String,
    override val metadataFile: Provider<RegularFile>,
) : ModDependencySet {
    override val dependencies: Provider<List<ResolvedModDependencyInfo>> =
        metadataFile.map { file ->
            if (!file.asFile.isFile) return@map emptyList()

            modDependencyJsonMapper.readTree(file.asFile)
                .path("dependencies")
                .values()
                .asSequence()
                .map { dependency ->
                    ResolvedModDependencyInfo(
                        group = dependency.path("group").takeUnless { it.isMissingNode || it.isNull }?.stringValue(),
                        name = dependency.path("name").stringValue(),
                        selectedVersion = dependency.path("selectedVersion").takeUnless { it.isMissingNode || it.isNull }?.stringValue(),
                        loaderKind = ModLoaderKind.valueOf(dependency.path("loaderKind").stringValue()),
                        loaderModId = dependency.path("loaderModId").stringValue(),
                        declaredVersionRange = dependency.path("declaredVersionRange")
                            .takeUnless { it.isMissingNode || it.isNull }
                            ?.stringValue(),
                        relationship = ModDependencyRelationship.valueOf(dependency.path("relationship").stringValue()),
                        modrinthProject = dependency.path("modrinthProject")
                            .takeUnless { it.isMissingNode || it.isNull }
                            ?.stringValue(),
                        curseForgeProject = dependency.path("curseForgeProject")
                            .takeUnless { it.isMissingNode || it.isNull }
                            ?.stringValue(),
                    )
                }
                .toList()
        }
}

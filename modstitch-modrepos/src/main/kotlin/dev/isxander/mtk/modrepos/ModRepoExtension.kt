package dev.isxander.mtk.modrepos

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.ArtifactRepository

interface ModRepoExtension {
    operator fun invoke(): ArtifactRepository

    // Groovy compatibility
    fun call(): ArtifactRepository = invoke()
}

interface ExclusiveCapable {
    fun exclusive()
}

interface ExclusiveModRepoExtension : ModRepoExtension, ExclusiveCapable

internal abstract class ModRepoExtensionImpl(
    @Transient protected val repositories: RepositoryHandler,
) : ModRepoExtension



package dev.isxander.mtk.manifests

import dev.isxander.mtk.manifests.service.ManifestGenerationService
import org.gradle.api.Plugin
import org.gradle.api.Project

class ModstitchManifestsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        ManifestGenerationService.register(target)
        target.extensions.create("manifests", ManifestsExtension::class.java, target)
    }
}
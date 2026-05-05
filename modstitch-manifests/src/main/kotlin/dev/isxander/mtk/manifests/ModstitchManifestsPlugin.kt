package dev.isxander.mtk.manifests

import org.gradle.api.Plugin
import org.gradle.api.Project

class ModstitchManifestsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.extensions.create("manifests", ManifestsExtension::class.java, target)
    }
}
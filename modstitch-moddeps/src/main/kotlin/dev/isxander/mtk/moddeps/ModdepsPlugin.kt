package dev.isxander.mtk.moddeps

import org.gradle.api.Plugin
import org.gradle.api.Project

class ModdepsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create(
            "minecraftDependencies",
            MinecraftDependenciesExtension::class.java,
            target,
        )
        target.extensions.add("modDependency", extension)
    }
}

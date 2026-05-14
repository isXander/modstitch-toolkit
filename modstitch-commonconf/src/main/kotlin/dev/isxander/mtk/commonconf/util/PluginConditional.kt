package dev.isxander.mtk.commonconf.util

import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.neoforged.moddevgradle.dsl.ModDevExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

internal fun configureBackingPlugins(
    target: Project,
    loomBlock: (LoomGradleExtensionAPI) -> Unit,
    mdgBlock: (ModDevExtension) -> Unit,
) {
    target.pluginManager.withPlugin("net.fabricmc.fabric-loom") {
        val extension = target.extensions.getByType<LoomGradleExtensionAPI>()
        loomBlock(extension)
    }
    target.pluginManager.withPlugin("net.neoforged.moddev") {
        val extension = target.extensions.getByType<ModDevExtension>()
        mdgBlock(extension)
    }
}
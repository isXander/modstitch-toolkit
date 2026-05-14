package dev.isxander.mtk.commonconf.util

import org.gradle.api.Project

internal fun configureBackingPlugins(
    target: Project,
    loom: () -> Unit,
    mdg: () -> Unit,
) {
    target.pluginManager.withPlugin("net.fabricmc.fabric-loom") {
        loom()
    }
    target.pluginManager.withPlugin("net.neoforged.moddev") {
        mdg()
    }
}
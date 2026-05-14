package dev.isxander.mtk.commonconf.util

import org.gradle.api.Action
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.PluginManager

internal fun PluginManager.withFabricLoom(action: Action<in AppliedPlugin>) =
    withPlugin("net.fabricmc.fabric-loom", action)

internal fun PluginManager.withModDev(action: Action<in AppliedPlugin>) =
    withPlugin("net.neoforged.moddev", action)
package dev.isxander.mtk.multiloader.utils

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import kotlin.properties.ReadOnlyProperty
import kotlin.text.toBoolean

private fun convention(default: Boolean? = null) = ReadOnlyProperty<Project, Provider<Boolean>> { project, prop ->
    val conventionName = prop.name.removePrefix("convention").replaceFirstChar { it.lowercase() }
    val propertyName = "mtk.multiloader.$conventionName"
    project.providers.gradleProperty(propertyName)
        .map { it.toBoolean() }
        .let { if (default != null) it.orElse(default) else it }
}

/**
 * Whether to create default run configurations for each loader source set.
 */
val Project.conventionCreateDefaultRuns by convention(default = true)

/**
 * Whether to create a universal jar.
 */
val Project.conventionUniversalJar by convention(default = true)

/**
 * Whether to use the `net.fabricmc.fabric-loom-remap` plugin, which supports
 * <26.1 and mappings.
 */
val Project.conventionLoomRemap by convention(default = false)
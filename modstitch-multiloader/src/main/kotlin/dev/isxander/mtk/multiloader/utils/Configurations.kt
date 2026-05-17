package dev.isxander.mtk.multiloader.utils

import org.gradle.api.Action
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.tasks.SourceSet

fun ConfigurationContainer.configureElements(sourceSet: SourceSet, action: Action<in Configuration>) {
    named {
        it in listOf(
            sourceSet.apiElementsConfigurationName,
            sourceSet.runtimeElementsConfigurationName,
            sourceSet.sourcesElementsConfigurationName,
            sourceSet.javadocElementsConfigurationName,
        )
    }.configureEach(action)
}

fun ConfigurationContainer.configureClasspaths(sourceSet: SourceSet, action: Action<in Configuration>) {
    named {
        it in listOf(
            sourceSet.compileClasspathConfigurationName,
            sourceSet.runtimeClasspathConfigurationName,
        )
    }.configureEach(action)
}
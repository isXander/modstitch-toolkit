package dev.isxander.mtk.multiloader.utils

import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.neoforged.gradle.dsl.common.extensions.JarJar
import net.neoforged.gradle.dsl.common.runs.ide.extensions.IdeaRunExtension
import net.neoforged.gradle.dsl.common.runs.run.Run
import net.neoforged.gradle.dsl.common.runs.run.RunManager
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.*

val Project.java
    get() = extensions.getByType<JavaPluginExtension>()

val Project.sourceSets
    get() = extensions.getByType<SourceSetContainer>()

val Project.loom
    get() = extensions.getByType<LoomGradleExtensionAPI>()

val Project.ngRuns
    get() = extensions.getByType<RunManager>()

val Project.jarJar
    get() = extensions.getByType<JarJar>()

val Run.idea
    get() = extensions.getByType<IdeaRunExtension>()

val SourceSet.kotlin: SourceDirectorySet?
    get() = extensions.findByName("kotlin") as SourceDirectorySet?
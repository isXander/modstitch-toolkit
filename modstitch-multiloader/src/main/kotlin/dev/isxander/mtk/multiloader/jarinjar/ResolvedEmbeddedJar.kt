package dev.isxander.mtk.multiloader.jarinjar

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal

open class ResolvedEmbeddedJar(
    /** The file; path relative to the jar */
    @get:Input
    val path: String,

    @get:Input
    val group: String,

    @get:Input
    val artifact: String,

    @get:Input
    val classifier: String?,

    @get:Input
    val version: String,

    @get:Input
    val mavenVersionRange: String,
) {
    @get:Internal
    val fabricModId: String
        // TODO: ensure this exactly matches fabric loom implementation
        // TODO: move this to an extension property somewhere relevant to fabric.mod.json generation
        get() = listOfNotNull(
            group.replace('.', '_'),
            artifact.replace('.', '_'),
            classifier?.replace('.', '_'),
        ).joinToString("_")


}

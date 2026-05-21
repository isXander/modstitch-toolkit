package dev.isxander.mtk.manifests.service

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.*

/**
 * Build-level synchronisation token for tasks that touch night-config.
 *
 * Night-config's `JsonFormat`, `TomlFormat`, and `FormatDetector` have a
 * cyclic `<clinit>` (FormatDetector's static init `Class.forName`s both
 * formats; each format's static init touches FormatDetector). When parallel
 * tasks trigger the first initialisation of these classes on different
 * threads, they deadlock on the JVM's per-class init monitors.
 *
 * Tasks that use night-config declare a reference to this service. It is
 * registered with `maxParallelUsages = 1` so Gradle's scheduler refuses to
 * run two consumers concurrently, which guarantees the first initialisation
 * happens single-threaded and breaks the cycle.
 */
abstract class ManifestGenerationService : BuildService<BuildServiceParameters.None> {
    companion object {
        const val NAME = "mtkManifestGeneration"

        fun register(project: Project): Provider<ManifestGenerationService> =
            project.gradle.sharedServices.registerIfAbsent(NAME, ManifestGenerationService::class.java) {
                maxParallelUsages = 1
            }
    }
}
package dev.isxander.mtk.manifests.task

import dev.isxander.mtk.manifests.gen.ManifestGenerator
import dev.isxander.mtk.manifests.service.ManifestGenerationService
import dev.isxander.mtk.manifests.spec.ModManifestSpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Abstract base for tasks that serialise a [ModManifestSpec] subtype to a
 * single text file (e.g. `fabric.mod.json`, `neoforge.mods.toml`).
 *
 * Night-config's `JsonFormat`, `TomlFormat`, and `FormatDetector` have a
 * cyclic `<clinit>`: parallel tasks that touch these classes for the first
 * time can deadlock on the JVM's class-init monitors. The
 * [ManifestGenerationService] reference is declared with
 * `maxParallelUsages = 1` so Gradle serialises any task that needs night-config
 * at scheduling time, breaking the cycle without per-call locking.
 *
 * Cacheable: the action is a pure function of [spec] → [outputFile]. [spec] is
 * `@Nested` so Gradle tracks each managed-property field on the spec
 * individually for up-to-date checks.
 */
@CacheableTask
abstract class ManifestGenerateTask<T : ModManifestSpec> : DefaultTask() {
    @get:Nested
    abstract val spec: Property<T>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:ServiceReference(ManifestGenerationService.NAME)
    abstract val generationService: Property<ManifestGenerationService>

    @get:Internal
    internal abstract val generator: ManifestGenerator<T>

    @TaskAction
    fun generateManifest() {
        val file = outputFile.get().asFile
        file.parentFile?.mkdirs()
        file.writeText(generator.generate(spec.get()))
    }
}
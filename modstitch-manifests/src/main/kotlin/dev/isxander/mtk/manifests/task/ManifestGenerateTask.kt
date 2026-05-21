package dev.isxander.mtk.manifests.task

import dev.isxander.mtk.manifests.gen.ManifestGenerator
import dev.isxander.mtk.manifests.spec.ModManifestSpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

/**
 * Abstract base for tasks that serialise a [ModManifestSpec] subtype to a
 * single text file (e.g. `fabric.mod.json`, `neoforge.mods.toml`).
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

    @get:Internal
    internal abstract val generator: ManifestGenerator<T>

    @TaskAction
    fun generateManifest() {
        val file = outputFile.get().asFile
        file.parentFile?.mkdirs()
        file.writeText(generator.generate(spec.get()))
    }
}

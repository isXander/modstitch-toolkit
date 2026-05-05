package dev.isxander.mtk.manifests.task

import dev.isxander.mtk.manifests.gen.ManifestGenerator
import dev.isxander.mtk.manifests.spec.ModManifestSpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Abstract base for tasks that serialise a [ModManifestSpec] subtype to a
 * single text file (e.g. `fabric.mod.json`, `neoforge.mods.toml`).
 *
 * The task is format-agnostic: it knows how to run a [ManifestGenerator] and
 * write its string output to [outputFile]. The destination filename and any
 * source-set wiring are the caller's concern — see
 * [dev.isxander.mtk.manifests.ManifestsExtension] for helpers that pre-wire
 * an instance of this task to a source set with the loader-expected paths.
 *
 * Cacheable: the action is a pure function of [spec] → [outputFile], so the
 * task is annotated `@CacheableTask` and contributes to the local/remote
 * build cache. [spec] is `@Nested` so Gradle tracks each managed-property
 * field on the spec individually for up-to-date checks; mutating any input
 * (e.g. adding a dependency, changing the version) re-runs the task without
 * affecting unrelated work.
 *
 * Subclasses provide a concrete [ManifestGenerator] for their format and do
 * not override the action body.
 *
 * @param T the manifest spec model this task consumes.
 */
@CacheableTask
abstract class ManifestGenerateTask<T : ModManifestSpec> : DefaultTask() {
    /**
     * The mod manifest spec to serialise.
     *
     * Annotated `@Nested` so Gradle walks the spec's annotated lazy
     * properties (e.g. `modId`, `dependencies`, `mixins`) and treats each as
     * an individual task input. Resolved at execution time, so providers
     * chained from other tasks are evaluated lazily and contribute their
     * task dependencies automatically.
     */
    @get:Nested
    abstract val spec: Property<T>

    /**
     * Destination file for the generated manifest.
     *
     * The parent directory is created automatically by the task action if it
     * does not yet exist. The filename is the caller's choice — formats have
     * loader-mandated names (`fabric.mod.json`, `META-INF/neoforge.mods.toml`),
     * but enforcing them is left to the helpers that wire the task into a
     * source set.
     */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    /**
     * Format-specific serialiser supplied by the concrete subclass.
     *
     * Marked `@Internal` because the generator is an implementation detail of
     * the task type, not an input — its identity is fixed by the subclass and
     * its behaviour is exercised through [spec], which is the real input.
     */
    @get:Internal
    internal abstract val generator: ManifestGenerator<T>

    @TaskAction
    fun generateManifest() {
        val file = outputFile.get().asFile
        file.parentFile?.mkdirs()
        file.writeText(generator.generate(spec.get()))
    }
}
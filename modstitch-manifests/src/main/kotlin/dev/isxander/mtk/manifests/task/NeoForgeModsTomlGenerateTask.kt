package dev.isxander.mtk.manifests.task

import dev.isxander.mtk.manifests.gen.ManifestGenerator
import dev.isxander.mtk.manifests.gen.NeoForgeModsTomlGenerator
import dev.isxander.mtk.manifests.spec.NeoForgeModsTomlSpec
import org.gradle.api.tasks.CacheableTask

/**
 * Generates a NeoForge `neoforge.mods.toml` manifest from a
 * [NeoForgeModsTomlSpec].
 *
 * The task is format-agnostic about location — set [outputFile] to wherever
 * the generated text should land. For correct loader behaviour the resulting
 * file must end up at `META-INF/neoforge.mods.toml` in the JAR; the
 * source-set helper on `ManifestsExtension` handles that wiring.
 *
 * See [ManifestGenerateTask] for caching/up-to-date semantics.
 */
@CacheableTask
abstract class NeoForgeModsTomlGenerateTask : ManifestGenerateTask<NeoForgeModsTomlSpec>() {
    override val generator: ManifestGenerator<NeoForgeModsTomlSpec> = NeoForgeModsTomlGenerator
}
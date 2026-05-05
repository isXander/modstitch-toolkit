package dev.isxander.mtk.manifests.task

import dev.isxander.mtk.manifests.gen.FabricModJsonGenerator
import dev.isxander.mtk.manifests.gen.ManifestGenerator
import dev.isxander.mtk.manifests.spec.FabricModJsonSpec
import org.gradle.api.tasks.CacheableTask

/**
 * Generates a Fabric `fabric.mod.json` v1 manifest from a [FabricModJsonSpec].
 *
 * The task is format-agnostic about location — set [outputFile] to wherever
 * the generated text should land. For correct loader behaviour the resulting
 * file must end up at the JAR root as `fabric.mod.json`; the source-set
 * helper on `ManifestsExtension` handles that wiring.
 *
 * See [ManifestGenerateTask] for caching/up-to-date semantics.
 */
@CacheableTask
abstract class FabricModJsonGenerateTask : ManifestGenerateTask<FabricModJsonSpec>() {
    override val generator: ManifestGenerator<FabricModJsonSpec> = FabricModJsonGenerator
}
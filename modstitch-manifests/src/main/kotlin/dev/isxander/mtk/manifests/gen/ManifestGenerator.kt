package dev.isxander.mtk.manifests.gen

import dev.isxander.mtk.manifests.spec.ModManifestSpec

internal interface ManifestGenerator<T : ModManifestSpec> {
    fun generate(spec: T): String
}

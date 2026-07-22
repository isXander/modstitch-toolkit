package dev.isxander.mtk.manifests.spec

import dev.isxander.mtk.accessx.plugin.ConvertAccessxTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskProvider

/**
 * Models a NeoForge `META-INF/neoforge.mods.toml` manifest.
 *
 * Treats the `[[mods]]` array as flat (always one entry in 2026 practice),
 * so most user-facing metadata lives on [ModManifestSpec]. This subclass
 * adds only NMT-specific file-level and `[[mods]]` fields, plus DX overloads
 * using NMT's preferred dependency wording (`required`, `optional`,
 * `discouraged`, `incompatible`).
 */
abstract class NeoForgeModsTomlSpec : ModManifestSpec() {
    /** The language loader. Defaults to `javafml`. */
    @get:Input
    @get:Optional
    abstract val modLoader: Property<String>

    /** Acceptable loader version range (Maven version range format). */
    @get:Input
    @get:Optional
    abstract val loaderVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val showAsResourcePack: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val showAsDataPack: Property<Boolean>

    /** Java module services this mod uses. */
    @get:Input
    @get:Optional
    abstract val services: ListProperty<String>

    /** File-level substitution properties. */
    @get:Input
    @get:Optional
    abstract val fileProperties: MapProperty<String, String>

    /** Mod namespace override (defaults to [modId]). */
    @get:Input
    @get:Optional
    abstract val namespace: Property<String>

    @get:Internal
    val logoFile: Property<String>
        get() = iconPath

    /** Whether the logo uses linear (true) or nearest (false) filtering. */
    @get:Input
    @get:Optional
    abstract val logoBlur: Property<Boolean>

    /** URL to the mod's update-checker JSON. */
    @get:Input
    @get:Optional
    abstract val updateJSONURL: Property<String>

    /** Download page URL. */
    @get:Input
    @get:Optional
    abstract val modUrl: Property<String>

    /** Path to an enum extension JSON. */
    @get:Input
    @get:Optional
    abstract val enumExtensions: Property<String>

    /** Path to a feature flags JSON. */
    @get:Input
    @get:Optional
    abstract val featureFlags: Property<String>

    /** Acceptable Java version range, written under `[[features.<modId>]]`. */
    @get:Input
    @get:Optional
    abstract val javaVersion: Property<String>

    /**
     * Custom mod properties, written under `[[modproperties.<modId>]]`.
     *
     * Values must be `java.io.Serializable` (primitives, strings, lists, and
     * maps thereof) so the task input can be fingerprinted and survive the
     * Gradle configuration cache. Non-serialisable values will fail the build
     * at task snapshotting time.
     */
    @get:Input
    @get:Optional
    abstract val modProperties: MapProperty<String, Any>

    /** Paths (relative to the JAR root) to access-transformer config files. */
    @get:Input
    @get:Optional
    abstract val accessTransformers: ListProperty<String>

    /**
     * Copies common metadata *and* NMT-specific fields from [other].
     *
     * See [ModManifestSpec.from] for merge semantics; this overload extends
     * it with `modLoader`, `loaderVersion`, `showAsResourcePack`,
     * `showAsDataPack`, `services`, `fileProperties`, `namespace`,
     * `logoBlur`, `updateJSONURL`, `modUrl`, `enumExtensions`,
     * `featureFlags`, `javaVersion`, and `modProperties`.
     */
    fun from(other: NeoForgeModsTomlSpec) {
        super.from(other)
        modLoader.set(other.modLoader)
        loaderVersion.set(other.loaderVersion)
        showAsResourcePack.set(other.showAsResourcePack)
        showAsDataPack.set(other.showAsDataPack)
        namespace.set(other.namespace)
        logoBlur.set(other.logoBlur)
        updateJSONURL.set(other.updateJSONURL)
        modUrl.set(other.modUrl)
        enumExtensions.set(other.enumExtensions)
        featureFlags.set(other.featureFlags)
        javaVersion.set(other.javaVersion)

        services.addAll(other.services)
        accessTransformers.addAll(other.accessTransformers)
        fileProperties.putAll(other.fileProperties)
        modProperties.putAll(other.modProperties)
    }

    /** Adds an access-transformer file path. */
    fun accessTransformer(path: String) {
        accessTransformers.add(path)
    }

    /**
     * Adds an access-transformer entry pointing at the output of an
     * `accessx` convert task.
     *
     * The bundled jar path is the output file's name — which matches what
     * `accessx.convert(...)` writes to the resources source dir, so the file
     * sits at the JAR root. Task dependency is preserved via the provider
     * chain.
     */
    fun accessTransformer(task: TaskProvider<ConvertAccessxTask>) {
        accessTransformers.add(task.flatMap { t -> t.outputFile.map { it.asFile.name } })
    }
}

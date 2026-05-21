package dev.isxander.mtk.manifests

import dev.isxander.mtk.manifests.spec.FabricModJsonSpec
import dev.isxander.mtk.manifests.spec.ModManifestSpec
import dev.isxander.mtk.manifests.spec.NeoForgeModsTomlSpec
import dev.isxander.mtk.manifests.spec.VersionRange
import dev.isxander.mtk.manifests.task.FabricModJsonGenerateTask
import dev.isxander.mtk.manifests.task.NeoForgeModsTomlGenerateTask
import dev.isxander.mtk.manifests.util.MinecraftReleasesValueSource
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import javax.inject.Inject

abstract class ManifestsExtension @Inject constructor(
    private val project: Project,
    private val providers: ProviderFactory,
    private val objects: ObjectFactory,
) {
    /**
     * Creates a bare [dev.isxander.mtk.manifests.spec.ModManifestSpec] holding only the common fields shared
     * by FMJ and NMT.
     *
     * Useful as a DRY template: define your shared metadata once, then
     * `from(common)` it into both a [fabricModJson] and a [neoForgeModsToml]
     * spec — and reuse the same property handles when wiring the publishing
     * extension.
     */
    @JvmOverloads
    fun manifest(action: Action<ModManifestSpec> = Action {}): ModManifestSpec =
        objects.newInstance(ModManifestSpec::class.java).apply(action::execute)

    /**
     * Creates a new [dev.isxander.mtk.manifests.spec.FabricModJsonSpec] instance, optionally configuring it.
     *
     * Returned ad-hoc — store it in a `val` in your build script and reference
     * it from generation tasks, publishing extensions, etc.
     */
    @JvmOverloads
    fun fabricModJson(action: Action<FabricModJsonSpec> = Action {}): FabricModJsonSpec =
        objects.newInstance(FabricModJsonSpec::class.java).apply(action::execute)

    /**
     * Creates a new [dev.isxander.mtk.manifests.spec.NeoForgeModsTomlSpec] instance, optionally configuring it.
     *
     * Returned ad-hoc — store it in a `val` in your build script and reference
     * it from generation tasks, publishing extensions, etc.
     */
    @JvmOverloads
    fun neoForgeModsToml(action: Action<NeoForgeModsTomlSpec> = Action {}): NeoForgeModsTomlSpec =
        objects.newInstance(NeoForgeModsTomlSpec::class.java).apply(action::execute)

    /**
     * Registers a [FabricModJsonGenerateTask] pre-wired to [sourceSet].
     *
     * The task name follows Gradle's source-set naming convention via
     * `SourceSet.getTaskName("generate", "FabricModJson")` -- `generateFabricModJson`
     * for `main`, `generateTestFabricModJson` for `test`, etc. The output
     * directory is `<buildDir>/generated/manifests/<sourceSet>/fabric/`, and
     * the file lands at the JAR root as `fabric.mod.json`.
     *
     * The output directory is added to [sourceSet]'s resources via the
     * returned `TaskProvider`, so the file is included in the JAR built from
     * that source set and the generation task is wired as a dependency of
     * the corresponding `processResources` automatically.
     *
     * @return the registered task provider, for further configuration if
     *         needed (e.g. `dependsOn`, attaching to other tasks).
     */
    @JvmOverloads
    fun fabricModJson(
        sourceSet: SourceSet,
        action: Action<FabricModJsonSpec> = Action {},
    ): TaskProvider<FabricModJsonGenerateTask> {
        val fmjSpec = fabricModJson(action)
        val taskName = sourceSet.getTaskName("generate", "FabricModJson")
        val outputDir = project.layout.buildDirectory.dir("generated/manifests/${sourceSet.name}/fabric")
        val task = project.tasks.register<FabricModJsonGenerateTask>(taskName) {
            group = "modstitch/manifests"
            spec.set(fmjSpec)
            outputFile.set(outputDir.map { it.file("fabric.mod.json") })
        }
        sourceSet.resources.srcDir(project.files(outputDir).builtBy(task))
        return task
    }

    /**
     * Registers a [NeoForgeModsTomlGenerateTask] pre-wired to [sourceSet].
     *
     * The task name follows Gradle's source-set naming convention via
     * `SourceSet.getTaskName("generate", "NeoForgeModsToml")` —
     * `generateNeoForgeModsToml` for `main`, `generateTestNeoForgeModsToml`
     * for `test`, etc. The output directory is
     * `<buildDir>/generated/manifests/<sourceSet>/neoforge/`, and the file
     * lands at `META-INF/neoforge.mods.toml` inside the JAR.
     *
     * The output directory is added to [sourceSet]'s resources via the
     * returned `TaskProvider`, so the file is included in the JAR built from
     * that source set and the generation task is wired as a dependency of
     * the corresponding `processResources` automatically.
     *
     * @return the registered task provider, for further configuration if
     *         needed (e.g. `dependsOn`, attaching to other tasks).
     */
    @JvmOverloads
    fun neoForgeModsToml(
        sourceSet: SourceSet,
        action: Action<NeoForgeModsTomlSpec> = Action {},
    ): TaskProvider<NeoForgeModsTomlGenerateTask> {
        val nmtSpec = neoForgeModsToml(action)
        val taskName = sourceSet.getTaskName("generate", "NeoForgeModsToml")
        val outputDir = project.layout.buildDirectory.dir("generated/manifests/${sourceSet.name}/neoforge")
        val task = project.tasks.register<NeoForgeModsTomlGenerateTask>(taskName) {
            group = "modstitch/manifests"
            spec.set(nmtSpec)
            outputFile.set(outputDir.map { it.file("META-INF/neoforge.mods.toml") })
        }
        sourceSet.resources.srcDir(project.files(outputDir).builtBy(task))
        return task
    }

    /** Lazy provider of every release Minecraft version with a numeric dotted id. */
    fun minecraftReleases(): Provider<List<String>> =
        providers.of(MinecraftReleasesValueSource::class.java) {}

    fun minecraftReleasesMatching(range: String): Provider<List<String>> =
        minecraftReleasesMatching(mavenRange(range))

    /** Lazy provider of every release Minecraft version that satisfies [range]. */
    fun minecraftReleasesMatching(range: VersionRange): Provider<List<String>> =
        minecraftReleases().map { releases -> releases.filter { range.satisfies(it) } }

    /**
     * Lazy provider of every release Minecraft version that satisfies the range
     * supplied by [range]. The range itself is queried lazily, so it can be fed
     * by another `Property<VersionRange>`.
     */
    fun minecraftReleasesMatching(range: Provider<VersionRange>): Provider<List<String>> =
        minecraftReleases().zip(range) { releases, r -> releases.filter { r.satisfies(it) } }

    /**
     * Parses [string] as a Maven (NeoForge) version range and returns a
     * [VersionRange] suitable for use anywhere a range is accepted —
     * dependency declarations on specs, [minecraftReleasesMatching], etc.
     *
     * Maven syntax is the canonical form because it is structural: square
     * brackets are inclusive, parentheses are exclusive, and the only
     * operator is comma. Fabric-style requirements (`>=`, `~`, `^`, …)
     * are emitted as output via [VersionRange.toFabric] when the manifest is
     * generated, so you only ever author Maven here.
     *
     * Accepted inputs:
     *  - `""` or `"*"`               — matches any version
     *  - `"[1.0,2.0]"`               — 1.0 ≤ v ≤ 2.0
     *  - `"[1.0,2.0)"`, `"(1.0,2.0]"`, `"(1.0,2.0)"`
     *  - `"[1.0,)"`                  — v ≥ 1.0
     *  - `"(,2.0)"`                  — v < 2.0
     *  - `"[1.0]"`                   — exact match, v == 1.0
     *  - `"[1.0,2.0),[3.0,)"`        — union of intervals (OR)
     *  - `"1.0"`                     — bare version, treated as `[1.0,)`
     *
     * @throws IllegalArgumentException if [string] is malformed (unclosed
     *         brackets, empty intervals, missing commas between intervals).
     */
    fun mavenRange(string: String): VersionRange =
        VersionRange.parseMaven(string)
}

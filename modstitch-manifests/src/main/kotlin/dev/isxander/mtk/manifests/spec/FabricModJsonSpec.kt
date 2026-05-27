package dev.isxander.mtk.manifests.spec

import dev.isxander.mtk.accessx.plugin.ConvertAccessxTask
import org.gradle.api.Action
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.newInstance

/**
 * Models a `fabric.mod.json` v1 manifest.
 *
 * Inherits all common fields from [ModManifestSpec]. This subclass adds only
 * FMJ-specific fields and DX overloads using FMJ's preferred wording for
 * dependency types (`depends`, `suggests`, `conflicts`, `breaks`).
 */
abstract class FabricModJsonSpec : ModManifestSpec() {
    /** Side(s) the mod runs on. Absent means both. `BOTH` serialises as `*`. */
    @get:Input
    @get:Optional
    abstract val environment: Property<Side>

    /** Other mod IDs this mod aliases. */
    @get:Input
    @get:Optional
    abstract val provides: ListProperty<String>

    /** Additional JARs to load with the mod, paths relative to the JAR root. */
    @get:Input
    @get:Optional
    abstract val jars: ListProperty<String>

    /** FMJ entrypoints. */
    @get:Input
    @get:Optional
    abstract val entrypoints: ListProperty<Entrypoint>

    /** Extra contact entries beyond [homepage], [sourcesUrl], [issueTrackerUrl]. */
    @get:Input
    @get:Optional
    abstract val contactInformation: MapProperty<String, String>

    /** Language adapters provided by this mod. Key: adapter name, value: FQCN. */
    @get:Input
    @get:Optional
    abstract val languageAdapters: MapProperty<String, String>

    /**
     * Free-form custom data, encoded to JSON via Jackson.
     *
     * Values must be `java.io.Serializable` (primitives, strings, lists, and
     * maps thereof) so the task input can be fingerprinted and survive the
     * Gradle configuration cache. Non-serialisable values will fail the build
     * at task snapshotting time.
     */
    @get:Input
    @get:Optional
    abstract val customData: MapProperty<String, Any>

    /** Path to the access-widener file inside the JAR. */
    @get:Input
    @get:Optional
    abstract val accessWidener: Property<String>

    /**
     * Copies common metadata *and* FMJ-specific fields from [other].
     *
     * See [ModManifestSpec.from] for merge semantics; this overload extends
     * it with `schemaVersion`, `environment`, `provides`, `jars`,
     * `entrypoints`, `contactInformation`, `languageAdapters`, and
     * `customData`.
     */
    fun from(other: FabricModJsonSpec) {
        super.from(other)
        environment = other.environment
        accessWidener = other.accessWidener
        provides.addAll(other.provides)
        jars.addAll(other.jars)
        entrypoints.addAll(other.entrypoints)
        contactInformation.putAll(other.contactInformation)
        languageAdapters.putAll(other.languageAdapters)
        customData.putAll(other.customData)
    }

    /** Sets [environment] to [Side.CLIENT]. */
    fun client() {
        environment = Side.CLIENT
    }

    /** Sets [environment] to [Side.SERVER]. */
    fun server() {
        environment = Side.SERVER
    }

    fun makeEntrypoint(action: Action<Entrypoint>): Entrypoint =
        objectFactory.newInstance(Entrypoint::class).apply(action::execute)

    fun entrypoint(action: Action<Entrypoint>) {
        entrypoints.add(makeEntrypoint(action))
    }

    @JvmOverloads
    fun entrypoint(name: String, value: String, adapter: String? = null) {
        entrypoint {
            this.entrypoint.set(name)
            this.value.set(value)
            if (adapter != null) {
                this.adapter.set(adapter)
            }
        }
    }

    /** Sets the FMJ access-widener path. */
    fun accessWidener(path: String) {
        accessWidener.set(path)
    }

    /**
     * Wires [accessWidener] to the output of an `accessx` convert task.
     *
     * The bundled jar path is the output file's name — which matches what
     * `accessx.convert(...)` writes to the resources source dir, so the file
     * sits at the JAR root. Task dependency is preserved via the provider
     * chain.
     */
    fun accessWidener(task: TaskProvider<ConvertAccessxTask>) {
        accessWidener.set(task.flatMap { t -> t.outputFile.map { it.asFile.name } })
    }

    abstract class Entrypoint {
        @get:Input
        abstract val entrypoint: Property<String>

        @get:Input
        abstract val value: Property<String>

        @get:Input
        @get:Optional
        abstract val adapter: Property<String>
    }
}

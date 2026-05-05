package dev.isxander.mtk.manifests.spec

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import javax.inject.Inject

/**
 * Common base for a single mod's manifest metadata.
 *
 * Treats NeoForge's `[[mods]]` array as flat (in practice always one entry)
 * to maximise the surface that can be expressed identically across both
 * `fabric.mod.json` v1 and `neoforge.mods.toml`.
 *
 * Mappings between formats:
 *  - `displayName` → FMJ `name`, NMT `displayName`
 *  - `licenses` → FMJ `license` (list), NMT `license` (joined SPDX expression)
 *  - `authors` → FMJ `authors[].name`, NMT `authors` (joined string)
 *  - `contributors` → FMJ `contributors[].name`, NMT `credits` (joined string)
 *  - `homepage` → FMJ `contact.homepage`, NMT `displayURL`
 *  - `sourcesUrl` → FMJ `contact.sources`, NMT — (no equivalent)
 *  - `issueTrackerUrl` → FMJ `contact.issues`, NMT `issueTrackerURL`
 *  - `iconPath` → FMJ unsized icon, NMT `logoFile`
 *  - [Dependency.type] mapping FMJ ↔ NMT:
 *      - `REQUIRED`     ↔ `depends`     ↔ `required`
 *      - `OPTIONAL`     ↔ `suggests`    ↔ `optional`
 *      - `DISCOURAGED`  ↔ `conflicts`   ↔ `discouraged`
 *      - `INCOMPATIBLE` ↔ `breaks`      ↔ `incompatible`
 *  - [Side] applies to FMJ `environment`/mixin `environment` and NMT
 *    dependency `side`. `BOTH` serialises as `*` for FMJ.
 */
abstract class ModManifestSpec {
    /** The unique identifier of the mod. */
    @get:Input
    abstract val modId: Property<String>

    /** The version of the mod. */
    @get:Input
    abstract val version: Property<String>

    /** The human-readable display name of the mod. */
    @get:Input
    @get:Optional
    abstract val displayName: Property<String>

    /** A short description of the mod. */
    @get:Input
    @get:Optional
    abstract val description: Property<String>

    /** SPDX licence identifiers (or names) for the mod. */
    @get:Input
    @get:Optional
    abstract val licenses: ListProperty<String>

    /** Author names. */
    @get:Input
    @get:Optional
    abstract val authors: ListProperty<String>

    /** Contributor names. */
    @get:Input
    @get:Optional
    abstract val contributors: ListProperty<String>

    /** URL of the mod's homepage / display page. */
    @get:Input
    @get:Optional
    abstract val homepage: Property<String>

    /** URL of the mod's source repository. */
    @get:Input
    @get:Optional
    abstract val sourcesUrl: Property<String>

    /** URL of the mod's issue tracker. */
    @get:Input
    @get:Optional
    abstract val issueTrackerUrl: Property<String>

    /** Path to a single icon image inside the produced JAR. */
    @get:Input
    @get:Optional
    abstract val iconPath: Property<String>

    /** Mixin configurations bundled with the mod. */
    @get:Input
    @get:Optional
    abstract val mixins: ListProperty<Mixin>

    /** Dependencies on other mods. */
    @get:Input
    @get:Optional
    abstract val dependencies: ListProperty<Dependency>

    /** Adds a mixin configuration JSON path. */
    fun mixin(config: String) {
        create(Mixin::class.java, mixins) {
            this.config.set(config)
        }
    }

    /** Adds a mixin configuration that only applies on the given [side]. */
    fun mixin(config: String, side: Side) {
        create(Mixin::class.java, mixins) {
            this.config.set(config)
            this.side.set(side)
        }
    }

    /** Adds a dependency. */
    @JvmOverloads
    fun dependency(modId: String, type: DependencyType, range: VersionRange = VersionRange.Any) {
        create(Dependency::class.java, dependencies) {
            this.modId.set(modId)
            this.type.set(type)
            this.versionRange.set(range)
        }
    }

    /** Adds a dependency, parsing [range] as a Maven version range. */
    fun dependency(modId: String, type: DependencyType, range: String) {
        dependency(modId, type, VersionRange.parseMaven(range))
    }

    /** Adds a dependency restricted to one [side]. */
    fun dependency(modId: String, type: DependencyType, range: VersionRange, side: Side) {
        create(Dependency::class.java, dependencies) {
            this.modId.set(modId)
            this.type.set(type)
            this.versionRange.set(range)
            this.side.set(side)
        }
    }

    abstract class Mixin {
        /** Path to the mixin configuration JSON, relative to the JAR root. */
        @get:Input
        abstract val config: Property<String>

        /** Side this mixin applies to. Absent means both. */
        @get:Input
        @get:Optional
        abstract val side: Property<Side>

        /**
         * Mod IDs that must be present for this mixin to apply.
         * Only applies on `neoforge.mods.toml`.
         */
        @get:Input
        @get:Optional
        abstract val requiredMods: ListProperty<String>
    }

    abstract class Dependency {
        @get:Input
        abstract val modId: Property<String>

        @get:Input
        abstract val type: Property<DependencyType>

        @get:Input
        @get:Optional
        abstract val versionRange: Property<VersionRange>

        /** Side this dependency is required on. Absent means both. */
        @get:Input
        @get:Optional
        abstract val side: Property<Side>
    }

    /** Game side a piece of mod metadata applies to. `BOTH` serialises as `*` in FMJ. */
    enum class Side { CLIENT, SERVER, BOTH }

    /**
     * How a dependency must be present.
     *
     * Mappings: `REQUIRED` ↔ FMJ `depends` / NMT `required`; `OPTIONAL` ↔
     * FMJ `suggests` / NMT `optional`; `DISCOURAGED` ↔ FMJ `conflicts` /
     * NMT `discouraged`; `INCOMPATIBLE` ↔ FMJ `breaks` / NMT `incompatible`.
     */
    enum class DependencyType { REQUIRED, OPTIONAL, DISCOURAGED, INCOMPATIBLE }

    /**
     * Copies common metadata from [other] into this spec.
     *
     * Single-value properties are wired with `set(provider)` so updates to
     * [other] flow through; later writes on this spec override. List/map
     * properties merge via `addAll` / `putAll`, preserving items already on
     * this spec.
     *
     * Subclasses may overload `from` with their concrete type; calling
     * `nmt.from(otherNmt)` copies common *and* NMT-specific fields.
     */
    open fun from(other: ModManifestSpec) {
        modId.set(other.modId)
        version.set(other.version)
        displayName.set(other.displayName)
        description.set(other.description)
        homepage.set(other.homepage)
        sourcesUrl.set(other.sourcesUrl)
        issueTrackerUrl.set(other.issueTrackerUrl)
        iconPath.set(other.iconPath)

        licenses.addAll(other.licenses)
        authors.addAll(other.authors)
        contributors.addAll(other.contributors)
        mixins.addAll(other.mixins)
        dependencies.addAll(other.dependencies)
    }

    @Inject
    protected abstract fun getObjectFactory(): ObjectFactory

    protected fun <T : Any> create(
        type: Class<T>,
        list: ListProperty<T>,
        configure: T.() -> Unit,
    ): T {
        val item = getObjectFactory().newInstance(type)
        item.configure()
        list.add(item)
        return item
    }
}

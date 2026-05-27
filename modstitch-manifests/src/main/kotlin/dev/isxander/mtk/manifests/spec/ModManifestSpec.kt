package dev.isxander.mtk.manifests.spec

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.newInstance
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

    fun makeMixin(action: Action<Mixin>): Mixin =
        objectFactory.newInstance(Mixin::class).apply(action::execute)

    fun mixin(action: Action<Mixin>) {
        mixins.add(makeMixin(action))
    }

    /** Adds a mixin configuration JSON path. */
    fun mixin(config: String) {
        mixin {
            this.config.set(config)
        }
    }

    /** Adds a mixin configuration that only applies on the given [side]. */
    fun mixin(config: String, side: Side) {
        mixin {
            this.config.set(config)
            this.side.set(side)
        }
    }

    /**
     * Creates a dependency and configures it with [action] but does not register it.
     */
    fun makeDependency(action: Action<Dependency>): Dependency =
        objectFactory.newInstance(Dependency::class).apply(action::execute)

    /**
     * Adds a dependency with the given [action].
     */
    fun dependency(action: Action<Dependency>) {
        dependencies.add(makeDependency(action))
    }

    /** Adds a dependency. */
    @JvmOverloads
    fun dependency(modId: String, type: DependencyType, mavenRange: String = VersionRange.Any.toMaven(), side: Side? = null) {
        dependencies.add(makeDependency {
            this.modId.set(modId)
            this.type.set(type)
            this.versionRange.set(VersionRange.parseMaven(mavenRange))
            if (side != null) this.side.set(side)
        })
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

        fun modId(id: String) = modId.set(id)

        @get:Input
        abstract val type: Property<DependencyType>

        @get:Input
        @get:Optional
        abstract val versionRange: Property<VersionRange>

        fun versionRange(range: VersionRange) = versionRange.set(range)
        fun versionRange(mavenRange: String) = versionRange(VersionRange.parseMaven(mavenRange))

        /** Side this dependency is required on. Absent means both. */
        @get:Input
        @get:Optional
        abstract val side: Property<Side>

        fun required() = type.set(DependencyType.REQUIRED)
        fun depends() = required()
        fun optional() = type.set(DependencyType.OPTIONAL)
        fun suggests() = optional()
        fun discouraged() = type.set(DependencyType.DISCOURAGED)
        fun conflicts() = discouraged()
        fun incompatible() = type.set(DependencyType.INCOMPATIBLE)
        fun breaks() = incompatible()
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

    // Redefine enum constants to avoid requiring imports


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

    @get:Inject
    protected abstract val objectFactory: ObjectFactory

    // Redefine enum constants to avoid requiring imports

    @JvmField @get:Internal
    val BOTH = Side.BOTH
    @JvmField @get:Internal
    val CLIENT = Side.CLIENT
    @JvmField @get:Internal
    val SERVER = Side.SERVER

    @JvmField @get:Internal
    val REQUIRED = DependencyType.REQUIRED
    @JvmField @get:Internal
    val DEPENDS = REQUIRED
    @JvmField @get:Internal
    val OPTIONAL = DependencyType.OPTIONAL
    @JvmField @get:Internal
    val SUGGESTS = OPTIONAL
    @JvmField @get:Internal
    val DISCOURAGED = DependencyType.DISCOURAGED
    @JvmField @get:Internal
    val CONFLICTS = DISCOURAGED
    @JvmField @get:Internal
    val INCOMPATIBLE = DependencyType.INCOMPATIBLE
    @JvmField @get:Internal
    val BREAKS = INCOMPATIBLE
}

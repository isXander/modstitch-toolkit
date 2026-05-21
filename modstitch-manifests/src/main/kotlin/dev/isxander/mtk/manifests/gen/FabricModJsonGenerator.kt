package dev.isxander.mtk.manifests.gen

import dev.isxander.mtk.manifests.spec.FabricModJsonSpec
import dev.isxander.mtk.manifests.spec.ModManifestSpec.DependencyType
import dev.isxander.mtk.manifests.spec.ModManifestSpec.Side
import tools.jackson.databind.json.JsonMapper

internal object FabricModJsonGenerator : ManifestGenerator<FabricModJsonSpec> {
    const val SCHEMA_VERSION: Int = 1

    private val jsonMapper = JsonMapper.builder().build()

    override fun generate(spec: FabricModJsonSpec): String {
        val config = orderedJsonConfig().apply {
            add("schemaVersion", SCHEMA_VERSION)

            // Required
            addProperty("id", spec.modId, required = true)
            addProperty("version", spec.version, required = true)

            // All other fields are optional

            addListProperty("provides", spec.provides)
            addProperty("environment", spec.environment.map { side -> when (side) {
                Side.BOTH -> "*"
                Side.CLIENT -> "client"
                Side.SERVER -> "server"
            } })

            spec.entrypoints.getOrElse(emptyList()).forEach { entrypoint ->
                val entrypointName = entrypoint.entrypoint.get()
                val entrypointValue = entrypoint.value.get()
                val entrypointAdapter = entrypoint.adapter.orNull

                if (entrypointAdapter == null) {
                    // use string shorthand if no adapter is specified
                    add("entrypoints.$entrypointName", entrypointValue)
                } else {
                    // use full object form if an adapter is specified
                    add("entrypoints.$entrypointName.value", entrypointValue)
                    add("entrypoints.$entrypointName.adapter", entrypointAdapter)
                }
            }

            add("mixins", spec.mixins.getOrElse(emptyList()).map { mixin ->
                val side = mixin.side.orNull.takeIf { it != Side.BOTH }
                if (side == null) {
                    return@map mixin.config.get()
                } else {
                    return@map createSubConfig().apply {
                        addProperty("config", mixin.config, required = true)
                        add("environment", when (side) {
                            Side.CLIENT -> "client"
                            Side.SERVER -> "server"
                            else -> error("Invalid side: $side")
                        })
                    }
                }
            })

            addProperty("accessWidener", spec.accessWidener)

            spec.dependencies.getOrElse(emptyList())
                .sortedBy { it.type.get() } // ensure depends is first etc
                .forEach { dependency ->
                    val modId = dependency.modId.get()
                    val type = when (dependency.type.get()) {
                        DependencyType.REQUIRED -> "depends"
                        DependencyType.OPTIONAL -> "suggests"
                        DependencyType.DISCOURAGED -> "conflicts"
                        DependencyType.INCOMPATIBLE -> "breaks"
                    }
                    val requirements = dependency.versionRange.get().toFabric()

                    when {
                        requirements.isEmpty() -> throw IllegalArgumentException("Empty version range for $modId")
                        requirements.size == 1 -> add("$type.$modId", requirements.single())
                        else -> add("$type.$modId", requirements)
                    }
                }

            addProperty("name", spec.displayName)
            addProperty("description", spec.description)
            addListProperty("authors", spec.authors) // does anyone use contact information? I didn't even know it existed till now
            addListProperty("contributors", spec.contributors)

            val contact = mutableMapOf<String, String>()
            spec.homepage.orNull?.let { contact["homepage"] = it }
            spec.sourcesUrl.orNull?.let { contact["sources"] = it }
            spec.issueTrackerUrl.orNull?.let { contact["issues"] = it }
            contact.putAll(spec.contactInformation.getOrElse(emptyMap()))
            if (contact.isNotEmpty()) {
                addMap("contact", contact)
            }

            spec.licenses.getOrElse(emptyList())
                .takeIf { it.isNotEmpty() }
                ?.let { add("license", if (it.size == 1) it.single() else it) }

            addProperty("icon", spec.iconPath)
            addMapProperty("languageAdapters", spec.languageAdapters)
            addMapProperty("custom", spec.customData)
        }

        return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config)
    }
}

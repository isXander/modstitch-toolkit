package dev.isxander.mtk.manifests.gen

import dev.isxander.mtk.manifests.spec.ModManifestSpec.DependencyType
import dev.isxander.mtk.manifests.spec.ModManifestSpec.Side
import dev.isxander.mtk.manifests.spec.NeoForgeModsTomlSpec
import tools.jackson.dataformat.toml.TomlMapper

internal object NeoForgeModsTomlGenerator : ManifestGenerator<NeoForgeModsTomlSpec> {
    private val tomlMapper = TomlMapper()

    override fun generate(spec: NeoForgeModsTomlSpec): String {
        val config = orderedTomlConfig().apply {
            // Non-Mod-Specific Properties
            addProperty("modLoader", spec.modLoader)
            addProperty("loaderVersion", spec.loaderVersion)
            addProperty("license", spec.licenses.map { it.joinToString(" AND ") }, required = true)
            addProperty("showAsResourcePack", spec.showAsResourcePack)
            addProperty("showAsDataPack", spec.showAsDataPack)
            addListProperty("services", spec.services)
            addMapProperty("properties", spec.fileProperties)
            addProperty("issueTrackerURL", spec.issueTrackerUrl)

            // Mod-Specific Properties
            val modId = spec.modId.get()
            add("mods", listOf(createSubConfig().apply {
                add("modId", modId)
                addProperty("namespace", spec.namespace)
                addProperty("version", spec.version)
                addProperty("displayName", spec.displayName)
                addProperty("description", spec.description)
                addProperty("logoFile", spec.logoFile)
                addProperty("logoBlur", spec.logoBlur)
                addProperty("updateJSONURL", spec.updateJSONURL)
                addProperty("modUrl", spec.modUrl)
                addProperty("credits", spec.contributors.map { it.joinToString("\n") })
                addProperty("authors", spec.authors.map { it.joinToString(", ") })
                addProperty("displayURL", spec.homepage)
                addProperty("enumExtensions", spec.enumExtensions)
                addProperty("featureFlags", spec.featureFlags)
            }))

            spec.javaVersion.orNull?.let { javaVersion ->
                add("features.$modId", listOf(createSubConfig().apply {
                    add("javaVersion", javaVersion)
                }))
            }

            spec.modProperties.getOrElse(emptyMap())
                .takeIf { it.isNotEmpty() }
                ?.let { modProperties ->
                    add("modproperties.$modId", listOf(createSubConfig().apply {
                        modProperties.forEach { (propertyKey, propertyValue) ->
                            add(propertyKey, propertyValue)
                        }
                    }))
                }

            spec.dependencies.getOrElse(emptyList())
                .takeIf { it.isNotEmpty() }
                ?.let { deps ->
                    add("dependencies.$modId", deps.map { dependency ->
                        createSubConfig().apply {
                            add("modId", dependency.modId.get())
                            add("type", when (dependency.type.get()) {
                                DependencyType.REQUIRED -> "required"
                                DependencyType.OPTIONAL -> "optional"
                                DependencyType.DISCOURAGED -> "discouraged"
                                DependencyType.INCOMPATIBLE -> "incompatible"
                            })
                            add("versionRange", dependency.versionRange.get().toMaven())
                            dependency.side.orNull?.let { side ->
                                add("side", when (side) {
                                    Side.CLIENT -> "CLIENT"
                                    Side.SERVER -> "SERVER"
                                    Side.BOTH -> "BOTH"
                                })
                            }
                        }
                    })
                }

            spec.accessTransformers.getOrElse(emptyList())
                .takeIf { it.isNotEmpty() }
                ?.map { at -> createSubConfig().apply {
                    add("file", at)
                } }
                ?.let { add("accessTransformers", it) }

            spec.mixins.getOrElse(emptyList())
                .takeIf { it.isNotEmpty() }
                ?.map { mixin -> createSubConfig().apply {
                    addProperty("config", mixin.config)
                    addListProperty("requiredMods", mixin.requiredMods)                } }
                ?.let { add("mixins", it) }
        }

        return tomlMapper.writeValueAsString(config)
    }
}

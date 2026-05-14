package dev.isxander.mtk.commonconf

import dev.isxander.mtk.commonconf.extensions.CommonconfExtension
import dev.isxander.mtk.commonconf.util.Side
import dev.isxander.mtk.commonconf.util.convertNeoForgeVersionToMinecraftVersion
import dev.isxander.mtk.commonconf.util.withFabricLoom
import dev.isxander.mtk.commonconf.util.withModDev
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.util.Constants
import net.neoforged.moddevgradle.dsl.ModDevExtension
import net.neoforged.moddevgradle.dsl.NeoForgeExtension
import net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*

class CommonconfPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.extensions.create(
            "commonconf",
            CommonconfExtension::class.java
        )

        target.configurations.create("ccJarInJar") {
            isTransitive = false
        }

        target.pluginManager.withFabricLoom {
            val extension = target.extensions.getByType<CommonconfExtension>()
            applyLoom(target, extension)
        }
        target.pluginManager.withModDev {
            val extension = target.extensions.getByType<CommonconfExtension>()
            applyMdg(target, extension)
        }
    }

    private fun applyLoom(target: Project, extension: CommonconfExtension) {
        val loom = target.extensions.getByType<LoomGradleExtensionAPI>()

        target.dependencies {
            "minecraft"(extension.minecraftVersion.map { "com.mojang:minecraft:$it" })
            "implementation"(extension.loaderVersion.map { "net.fabricmc:fabric-loader:$it" })
        }

        loom.accessWidenerPath = target.provider {
            val single = extension.accessxFiles.singleOrNull()
            if (extension.accessxFiles.isEmpty) {
                null
            } else {
                single ?: throw IllegalStateException("commonconf.accessxFiles must have exactly one or zero files in a Loom environment")
            }
        }

        target.configurations.named(Constants.Configurations.INCLUDE) {
            extendsFrom(target.configurations.getByName("ccJarInJar"))
        }

        extension.runs.configureEach {
            val ccSpec = this

            loom.runs.register(ccSpec.name) {
                val loomSpec = this

                loomSpec.displayName = ccSpec.ideRunName
                loomSpec.jvmArguments = ccSpec.jvmArgs
                loomSpec.programArguments = ccSpec.programArgs
                loomSpec.environmentVars = ccSpec.environmentVars
                loomSpec.systemProperties = ccSpec.systemProperties
                loomSpec.runtimeEnvironment = ccSpec.side.map {
                    when (it) {
                        Side.Client -> "client"
                        Side.Server -> "server"
                    }
                }
                loomSpec.mainClass = ccSpec.mainClass
                loomSpec.sourceSet = ccSpec.sourceSet.map { it.name }
                loomSpec.runDirectory = ccSpec.gameDirectory
                loomSpec.generateRunConfig = ccSpec.ideRun
            }
        }
    }

    private fun applyMdg(target: Project, extension: CommonconfExtension) {
        val modDev = target.extensions.getByType<ModDevExtension>()

        // Estimate minecraftVersion with by parsing the loader version
        extension.minecraftVersion.convention(
            extension.loaderVersion.map { convertNeoForgeVersionToMinecraftVersion(it) }
        )

        // Source all accessxFiles to accessTransformers
        modDev.accessTransformers.from(extension.accessxFiles)

        // MDG enabling is not lazy.
        target.afterEvaluate {
            // only apply if there was a successful evaluation
            if (state.failure == null) {
                val neoForge = target.extensions.findByType<NeoForgeExtension>()
                val legacyForge = target.extensions.findByType<LegacyForgeExtension>()

                extension.loaderVersion.finalizeValue()

                neoForge?.enable {
                    version = extension.loaderVersion.get()
                }

                legacyForge?.enable {
                    version = extension.loaderVersion.get()
                }

                // Definitively set minecraftVersion, provided from MDG.
                extension.minecraftVersion = neoForge?.minecraftVersion
                    ?: legacyForge?.minecraftVersion
                extension.minecraftVersion.finalizeValue()

                target.configurations.named("jarJar") {
                    extendsFrom(target.configurations.getByName("ccJarInJar"))
                }
            }
        }

        extension.runs.configureEach {
            val ccSpec = this

            modDev.runs.register(ccSpec.name) {
                val mdgSpec = this

                mdgSpec.ideName = ccSpec.ideRunName.zip(ccSpec.ideRun) { name, shouldRun ->
                    if (shouldRun) name else ""
                }
                mdgSpec.gameDirectory = ccSpec.gameDirectory
                mdgSpec.environment = ccSpec.environmentVars
                mdgSpec.systemProperties = ccSpec.systemProperties
                mdgSpec.mainClass = ccSpec.mainClass
                mdgSpec.programArguments = ccSpec.programArgs
                mdgSpec.jvmArguments = ccSpec.jvmArgs
                mdgSpec.type = ccSpec.side.zip(ccSpec.datagen) { side, datagen ->
                    when (side) {
                        Side.Client -> if (datagen) "clientData" else "client"
                        Side.Server -> if (datagen) "serverData" else "server"
                    }
                }
                mdgSpec.sourceSet = ccSpec.sourceSet
            }
        }
    }

    companion object {
        internal fun disableIdeRuns(target: Project) {
            target.pluginManager.withFabricLoom {
                val loom = target.extensions.getByType<LoomGradleExtensionAPI>()

                loom.runs.named("client") {
                    loom.runs.remove(this)
                }
                loom.runs.named("server") {
                    loom.runs.remove(this)
                }
            }
            target.pluginManager.withModDev {
                val modDev = target.extensions.getByType<ModDevExtension>()

                modDev.runs.named("client") {
                    modDev.runs.remove(this)
                }
                modDev.runs.named("server") {
                    modDev.runs.remove(this)
                }
            }
        }
    }
}
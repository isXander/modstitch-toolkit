package dev.isxander.mtk.modrepos

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.kotlin.dsl.*

fun RepositoryHandler.minecraft() =
    maven("https://libraries.minecraft.net") {
        name = "Minecraft Libraries"
    }

internal abstract class MinecraftExtension(repositories: RepositoryHandler)
    : ModRepoExtensionImpl(repositories), ExclusiveModRepoExtension {
    override fun invoke() = repositories.minecraft()

    override fun exclusive() {
        repositories.exclusiveContent {
            forRepository { invoke() }
            filter {
                includeGroupAndSubgroups("com.mojang")
                includeGroupAndSubgroups("net.minecraft")
            }
        }
    }
}

fun RepositoryHandler.terraformersMC() =
    maven("https://maven.terraformersmc.com/releases") {
        name = "TerraformersMC Releases"
    }

internal abstract class TerraformersMCExtension(repositories: RepositoryHandler)
    : ModRepoExtensionImpl(repositories), ExclusiveModRepoExtension {
    override fun invoke() = repositories.terraformersMC()

    override fun exclusive() {
        repositories.exclusiveContent {
            forRepository { invoke() }
            filter { includeGroupAndSubgroups("com.terraformersmc") }
        }
    }
}

fun RepositoryHandler.terraformersMCSnapshots() =
    maven("https://maven.terraformersmc.com/snapshots") {
        name = "TerraformersMC Snapshots"
    }

internal abstract class TerraformersMCSnapshotsExtension(repositories: RepositoryHandler)
    : ModRepoExtensionImpl(repositories), ExclusiveModRepoExtension {
    override fun invoke() = repositories.terraformersMCSnapshots()

    override fun exclusive() {
        repositories.exclusiveContent {
            forRepository { invoke() }
            filter { includeGroupAndSubgroups("com.terraformersmc") }
        }
    }
}

fun RepositoryHandler.modrinthApi() =
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth API Maven"
    }

internal abstract class ModrinthApiExtension(repositories: RepositoryHandler)
    : ModRepoExtensionImpl(repositories), ExclusiveModRepoExtension {
    override fun invoke() = repositories.modrinthApi()

    override fun exclusive() {
        repositories.exclusiveContent {
            forRepository { invoke() }
            filter { includeGroup("maven.modrinth") }
        }
    }
}

fun RepositoryHandler.curseMaven() =
    maven("https://cursemaven.com") {
        name = "Curse Maven"
    }

internal abstract class CurseMavenExtension(repositories: RepositoryHandler)
    : ModRepoExtensionImpl(repositories), ExclusiveModRepoExtension {
    override fun invoke() = repositories.curseMaven()

    override fun exclusive() {
        repositories.exclusiveContent {
            forRepository { invoke() }
            filter { includeGroup("curse.maven") }
        }
    }
}

fun RepositoryHandler.isxander() =
    maven("https://maven.isxander.dev/releases") {
        name = "isXander Releases"
    }

internal abstract class IsxanderExtension(repositories: RepositoryHandler) : ModRepoExtensionImpl(repositories) {
    override fun invoke() = repositories.isxander()
}

fun RepositoryHandler.nucleoid() =
    maven("https://maven.nucleoid.xyz/releases") {
        name = "Nucleoid Releases"
    }

internal abstract class NucleoidExtension(repositories: RepositoryHandler) : ModRepoExtensionImpl(repositories) {
    override fun invoke() = repositories.nucleoid()
}

fun RepositoryHandler.nucleoidSnapshots() =
    maven("https://maven.nucleoid.xyz/snapshots") {
        name = "Nucleoid Releases"
    }

internal abstract class NucleoidSnapshotsExtension(repositories: RepositoryHandler) : ModRepoExtensionImpl(repositories) {
    override fun invoke() = repositories.nucleoidSnapshots()
}

fun RepositoryHandler.fabricMC() =
    maven("https://maven.fabricmc.net") {
        name = "FabricMC"
    }

internal abstract class FabricMCExtension(repositories: RepositoryHandler) : ModRepoExtensionImpl(repositories) {
    override fun invoke() = repositories.fabricMC()
}

fun RepositoryHandler.neoForged() =
    maven("https://maven.neoforged.net/releases") {
        name = "NeoForged Releases"
    }

internal abstract class NeoForgedExtension(repositories: RepositoryHandler) : ModRepoExtensionImpl(repositories) {
    override fun invoke() = repositories.neoForged()
}

fun RepositoryHandler.wispForest() =
    maven("https://maven.wispforest.io/releases") {
        name = "WispForest Releases"
    }

internal abstract class WispForestExtension(repositories: RepositoryHandler) : ModRepoExtensionImpl(repositories) {
    override fun invoke() = repositories.wispForest()
}

fun RepositoryHandler.caffeineMC() =
    maven("https://maven.caffeinemc.net/releases") {
        name = "CaffeineMC Releases"
    }

internal abstract class CaffeineMCExtension(repositories: RepositoryHandler) : ModRepoExtensionImpl(repositories) {
    override fun invoke() = repositories.caffeineMC()
}

fun RepositoryHandler.caffeineMCSnapshots() =
    maven("https://maven.caffeinemc.net/snapshots") {
        name = "CaffeineMC Snapshots"
    }

internal abstract class CaffeineMCSnapshotsExtension(repositories: RepositoryHandler) : ModRepoExtensionImpl(repositories) {
    override fun invoke() = repositories.caffeineMCSnapshots()
}

fun RepositoryHandler.kikugie() =
    maven("https://maven.kikuge.dev/releases") {
        name = "Kikugie Releases"
    }

internal abstract class KikugieExtension(repositories: RepositoryHandler) : ModRepoExtensionImpl(repositories) {
    override fun invoke() = repositories.kikugie()
}

fun RepositoryHandler.kikugieSnapshots() =
    maven("https://maven.kikuge.dev/snapshots") {
        name = "Kikugie Snapshots"
    }

internal abstract class KikugieSnapshotsExtension(repositories: RepositoryHandler) : ModRepoExtensionImpl(repositories) {
    override fun invoke() = repositories.kikugieSnapshots()
}

fun RepositoryHandler.gegy() =
    maven("https://maven.gegy.dev/releases") {
        name = "Gegy Releases"
    }

internal abstract class GegyExtension(repositories: RepositoryHandler) : ModRepoExtensionImpl(repositories) {
    override fun invoke() = repositories.gegy()
}

fun RepositoryHandler.parchment() =
    maven("https://maven.parchmentmc.org") {
        name = "ParchmentMC"
    }

internal abstract class ParchmentExtension(repositories: RepositoryHandler) : ModRepoExtensionImpl(repositories) {
    override fun invoke() = repositories.parchment()
}
package dev.isxander.mtk.modrepos

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware

class ModreposPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        registerExclusive<MinecraftExtension>(target, "minecraft")
        registerExclusive<TerraformersMCExtension>(target, "terraformersMC")
        registerExclusive<TerraformersMCSnapshotsExtension>(target, "terraformersMCSnapshots")
        registerExclusive<ModrinthApiExtension>(target, "modrinthApi")
        registerExclusive<CurseMavenExtension>(target, "curseMaven")
        register<IsxanderExtension>(target, "isxander")
        register<NucleoidExtension>(target, "nucleoid")
        register<NucleoidSnapshotsExtension>(target, "nucleoidSnapshots")
        register<FabricMCExtension>(target, "fabricMC")
        register<NeoForgedExtension>(target, "neoForged")
        register<WispForestExtension>(target, "wispForest")
        register<CaffeineMCExtension>(target, "caffeineMC")
        register<CaffeineMCSnapshotsExtension>(target, "caffeineMCSnapshots")
        register<KikugieExtension>(target, "kikugie")
        register<KikugieSnapshotsExtension>(target, "kikugieSnapshots")
        register<GegyExtension>(target, "gegy")
    }

    private fun <T> register(target: Project, name: String, `class`: Class<T>)
            where T : ModRepoExtension {
        val repositories = target.repositories

        // RepositoryHandler implements ExtensionAware at runtime, but not compile time.
        // Kotlin type-safe accessors are generated regardless
        // assert to the compiler that RepositoryHandler implements ExtensionAware
        if (repositories !is ExtensionAware) {
            target.logger.error("modrepos could not add extensions to RepositoryHandler")
            return
        }

        repositories.extensions.create(
            ModRepoExtension::class.java,
            name,
            `class`,
            repositories,
        )
    }

    private fun <T> registerExclusive(target: Project, name: String, `class`: Class<T>)
            where T : ExclusiveModRepoExtension {
        val repositories = target.repositories

        // RepositoryHandler implements ExtensionAware at runtime, but not compile time.
        // Kotlin type-safe accessors are generated regardless
        // assert to the compiler that RepositoryHandler implements ExtensionAware
        if (repositories !is ExtensionAware) {
            target.logger.error("modrepos could not add extensions to RepositoryHandler")
            return
        }

        repositories.extensions.create(
            ExclusiveModRepoExtension::class.java,
            name,
            `class`,
            repositories,
        )
    }

    private inline fun <reified T> register(target: Project, name: String)
            where T : ModRepoExtension {
        register(target, name, T::class.java)
    }

    private inline fun <reified T> registerExclusive(target: Project, name: String)
            where T : ExclusiveModRepoExtension {
        registerExclusive(target, name, T::class.java)
    }
}

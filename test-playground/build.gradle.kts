import dev.isxander.mtk.manifests.spec.ModManifestSpec.DependencyType

plugins {
    java
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT"
    id("dev.isxander.mtk.commonconf")
    id("dev.isxander.mtk.manifests")
}

group = "dev.isxander"
version = "1.0.0"

commonconf {
    minecraftVersion = "26.1.2"
    loaderVersion = "0.19.2"

    runs {
        register("client") {
            client()
        }
    }
}

manifests {
    val common = manifest {
        modId = "example_mod"
        version = project.version.toString()
        displayName = "Example Mod"
        description = "Does useful things"
        authors = listOf("isXander")
        sourcesUrl = "https://github.com/isXander/modstitch-toolkit"
        licenses = listOf("LGPL-3.0-or-later")
        iconPath = "icon.png"

        dependency("minecraft", DependencyType.REQUIRED, "[26.1,26.2)")

        mixin("example_mod.mixins.json")
    }
    fabricModJson(sourceSets.main.get()) {
        from(common)

        entrypoint("client", "com.example.ExampleModClient")

        mixin("example_mod.fabric.mixins.json")

        depends("fabric-api", mavenRange("[0.89,)"))
    }
    neoForgeModsToml(sourceSets.main.get()) {
        from(common)

        mixin("example_mod.neoforge.mixins.json")
    }
}
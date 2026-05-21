import dev.isxander.mtk.manifests.spec.ModManifestSpec.DependencyType

plugins {
    java
    id("net.fabricmc.fabric-loom") version "1.17.local" apply false
    id("net.neoforged.gradle.userdev") version "7.1.27" apply false
    id("dev.isxander.mtk.manifests")
    id("dev.isxander.mtk.multiloader")
}

group = "dev.isxander"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:26.1.2")
    fabricLoader("net.fabricmc:fabric-loader:0.19.2")
    neoforgeImplementation("net.neoforged:neoforge:26.1.2.50-beta")

    add("commonInclude", "org.slf4j:slf4j-api") {
        version {
            strictly("[2.0,3.0)")
            prefer("2.0.17")
        }
    }
    add("universalOnlyInclude", "org.jetbrains:annotations:26.0.2")
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
    fabricModJson(sourceSets.fabric.get()) {
        from(common)

        entrypoint("client", "com.example.ExampleModClient")

        mixin("example_mod.fabric.mixins.json")

        depends("fabric-api", mavenRange("[0.89,)"))
    }
    neoForgeModsToml(sourceSets.neoforge.get()) {
        from(common)

        mixin("example_mod.neoforge.mixins.json")
    }
}

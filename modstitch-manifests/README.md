# MTK: `manifest`

A Gradle plugin to programmatically generate mod metadata files (e.g. `fabric.mod.json`, `META-INF/neoforge.mods.toml`).

```kotlin
plugins {
    id("dev.isxander.mtk.manifests") version "0.1.4"
}

// modstitch-manifests: programmatic fabric.mod.json and neoforge.mods.toml generation
manifests {
    // most properties are common between mod loader manifests so only need to be defined once
    // this object can be brought out of the `manifests {}` block for reference in other parts of the
    // buildscript.
    // you can also source *all* of these properties through Providers i.e., providers.gradleProperty("prop")
    val common = manifest {
        modId = "example_mod"
        version = project.version.toString()
        displayName = "Example Mod"
        description = "Does useful things"
        authors = listOf("isXander")
        sourcesUrl = "https://github.com/isXander/modstitch-toolkit-example"
        licenses = listOf("LGPL-3.0-or-later")
        iconPath = "icon.png"
        
        dependency {
            modId = "minecraft"
            // maven-like version ranges are automatically converted to the target platform's
            // version range format, i.e., fabric's ~26.1
            versionRange("[26.1,26.2)")
            required()
        }

        mixin("example_mod.mixins.json")
    }

    // Generates the defined manifests into source set's resources
    fabricModJson(sourceSets.fabric.get()) {
        from(common) // manifests can inherit from other manifests

        entrypoint("main", "com.example.fabric.ExampleModFabric")

        mixin("example_mod.fabric.mixins.json")

        accessWidener(fabricAWTask)

        dependency {
            modId = "fabric-api"
            versionRange("[0.149.0,)")
            depends()
        }
    }
    neoForgeModsToml(sourceSets.neoforge.get()) {
        from(common)

        mixin("example_mod.neoforge.mixins.json")

        accessTransformer(neoforgeAWTask)
    }
}
```
# MTK: `commonconf`

A Gradle plugin to apply type-safe configuration to either Fabric Loom or ModDevGradle, depending on which one is applied.

## Usage

```kotlin
plugins {
    id("dev.isxander.mtk.commonconf") version "0.1.0"
}

commonconf {
    // `minecraft("com.mojang:minecraft:26.1.2")` on Loom
    // Ignored on MDG, as `loaderVersion` is used to determine the Minecraft version
    minecraftVersion = "26.1.2"
    
    // Fabric Loader version on Loom
    // NeoForge version on MDG
    loaderVersion = providers.gradleProperty("loaderVersion")
    
    // `accessWidenerFile = ...` on Loom
    // `accessTransformers.from(...)` on MDG
    // If the file collection has more than one file, your build will fail on Loom, as Loom only supports a single access widener file.
    // Consider using `modstitch-accessx` tool to convert between formats 
    // and share a single source of truth for your accessx files.
    accessxFiles.from(layout.projectDirectory.file("mod.accessWidener"))

    // Removes Loom's and MDG's automatically created run configurations.
    disableDefaultRuns()
    
    // Configure run configurations for both Loom and MDG
    runs {
        register("client") {
            // configure a client-side run
            client()
            
            name = "Client Run ${project.name}"
            jvmArg("...")
            programArg("...")
            environment("...")
            
            runDir = "./run"
            
        }
        
        register("server") {
            // configure a server-side run
            server()
            // ...
        }
        
        register("data") {
            // configure a data generation run
            data()
            // ...
        }
    }
    
    // Configure the Loom extension if Loom is applied
    loom {
        // loom-specific configuration here
        // analogous to the `loom` block in a typical Loom build script
    }
    
    // Configure the MDG extension if MDG is applied
    mdg {
        // mdg-specific configuration here
        // analogous to the `neoForge` block in a typical MDG build script
    }
}

dependencies {
    // `ccJarInJar` configuration proxies Loom's `include` and MDG's `jarJar` configurations.
    ccJarInJar("dependency")
}
```
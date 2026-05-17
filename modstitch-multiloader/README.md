# MTK: `multiloader`

A Gradle plugin to apply conventions for multi-loader single-project builds,
using official first-party `net.fabricmc.fabric-loom` and `net.neoforged.gradle.userdev` plugins.

Uses source sets for per-loader code.

```
|--- src
|   |--- main
|   |   |--- java
|   |   |--- resources
|   |--- fabric
|   |   |--- java
|   |   |--- resources
|   |--- neoforge
|   |   |--- java
|   |   |--- resources
```

Only supports Fabric and NeoForge.

## Usage

```kotlin
plugins {
    // define versions of loader plugins here. can be defined elsewhere in the project e.g. settings.gradle.kts
    id("net.fabricmc.fabric-loom") version "x.y.z" apply false
    id("net.neoforged.gradle.userdev") version "x.y.z" apply false
    
    id("dev.isxander.mtk.multiloader") version "0.1.0"
}

dependencies {
    minecraft("com.mojang:minecraft:26.1.2")
    fabricLoader("net.fabricmc:fabric-loader:0.19.2")
    neoforgeImplementation("net.neoforged:neoforge:26.1.2.50-beta")
}
```

## Limitations

- **Does not support `fabric-loom-remap`**

  Currently, this plugin does not support `net.fabricmc.fabric-loom-remap` due to complexity
  with `remapJar` and how that interacts with multiple source sets. This is something I would
  like to support in the future.

- **Does not support Fabric Jar-in-Jar**

  Currently, Fabric Loom hardcodes jar-in-jar to the main source set.
  There is a PR open to fix this: https://github.com/FabricMC/fabric-loom/pull/1560

- **Universal jar does not support Jar-in-Jar / JarJar**

  The universal jar does not include the jar-in-jar and jarjar metadata and embedded jars.
  This is a bug and will be fixed soon.

- **Does not support split environment source sets**

  For obvious reasons, this plugin does not support split client/server source sets.
  This is technically possible, but would require a lot of extra work and I personally
  see little benefit for it.

- **Does not support Fabric-only access wideners / class tweakers**

  Because this plugin re-uses the Minecraft sources from the common (main) source set for the `fabric` source set,
  any access widener you apply will be applied to both the `main` and `fabric` source sets.
  
  This is not a huge problem, as you will typically want any source modifications to be applied globally.
  The `neoforge` source set is isolated from this as it uses Minecraft sources provided by NeoGradle.

## Features

### A universal jar

This plugin registers and configures a jar that contains common, fabric, and neoforge code.
This allows for a single distributable jar that can be used for all loaders.

#### Example

```kotlin
tasks.universalJar {
    // configure the jar here
}
tasks.universalSourcesJar {
    // configure the sources jar here
}

// example using `me.modmuss50.mod-publish-plugin`
publishMods {
    file = tasks.universalJar.map { it.archiveFile }
    additionalFiles.add(tasks.universalSourcesJar.map { it.archiveFile })
}
```

### Common configurations

It is common that you would need a dependency that is available across common and loader-specific code.

Dependencies using `compileOnly`, `implementation`, `runtimeOnly`, etc.,
will not be shared across loader-specific source sets, to ensure that there is no classpath leakage.

Instead, this plugin registers a set of configurations that share across all source sets:
- `commonCompileOnly`
- `commonImplementation`
- `commonRuntimeOnly`
- `commonApi`
- `commonCompileOnlyApi`
- `commonAnnotationProcessor`

You can use these configurations to declare dependencies across all source sets.

#### WARNING: Mod dependencies

DO NOT use these configurations when declaring mod dependencies that have different artifacts for each loader,
as this will cause classpath conflicts with the common parts of the mod.
Instead, use the regular `compileOnly` / `implementation` etc. to declare common-only dependencies:

```kotlin
dependencies {
    // CORRECT
    commonImplementation("org.example:common-lib:1.0.0")
    implementation("org.example:cool-mod-common:1.0.0")
    fabricImplementation("org.example:cool-mod-fabric:1.0.0")
    neoforgeImplementation("org.example:cool-mod-neoforge:1.0.0")
    
    // INCORRECT: now fabric and neoforge have cool-mod-common AND cool-mod-fabric / cool-mod-neoforge
    commonImplementation("org.example:cool-mod-common:1.0.0")
    fabricImplementation("org.example:cool-mod-fabric:1.0.0")
    neoforgeImplementation("org.example:cool-mod-neoforge:1.0.0")
}
```

### Run configurations

This plugin allows you to define run configurations for each loader.

Because this plugin just uses Fabric Loom and NeoGradle, you can configure run configurations
using the respective plugins' run configuration DSL.

`modstitch-multiloader` automatically configures any run configurations that you define to use
the correct source set.

The plugin also automatically creates:

- `runFabricClient`
- `runFabricServer`
- `runNeoforgeClient`
- `runNeoforgeServer`

run configurations.

### Publishing `mcgradleconventions`-compatible Gradle module metadata

This plugin creates many variants and artifacts.

- A common-only jar (`main` source set)
- A Fabric-compatible jar (`main` + `fabric` source sets)
- A NeoForge-compatible jar (`main` + `neoforge` source sets)
- A universal jar (`main` + `fabric` + `neoforge` source sets)

**The universal jar is *not* a published variant.**

The common, fabric, and neoforge jars are published as module metadata variants
with the attribute `io.github.mcgradleconventions.loader`.

#### Example

```kotlin
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            // publishes common, fabric, neoforge jars, and their sources
            from(components["java"]) 
            
            // optional, additional artifacts to publish
            // not recommended.
            artifact(tasks.universalJar)
            artifact(tasks.universalSourcesJar)
        }
    }
}
```

### Resolving dependencies using the `mcgradleconventions` loader attribute

If you use dependencies that publish Gradle module metadata variants
with the attribute `io.github.mcgradleconventions.loader`, then your build will automatically
resolve the correct variant for the source set.

This allows you to more effectively use Gradle version catalogs, as you only need to declare
a single dependency while your build will automatically resolve the correct variant.

#### Example

```kotlin
dependencies {
    // will resolve the common variant of gizmo
    implementation("org.example:gizmo:1.0.0")
    // will resolve the fabric variant of gizmo
    fabricImplementation("org.example:gizmo:1.0.0")
    // will resolve the neoforge variant of gizmo
    neoforgeImplementation("org.example:gizmo:1.0.0")
}
```

### Access Transformer / Access Widener / Class Tweaker support

Because this plugin just uses Fabric Loom and NeoGradle, you can use the respective plugins'
DSL to configure such things.

It's important to keep in mind that the Minecraft sources are shared between common (`main`) and `fabric`.
- Access Widener / Class Tweaker files are configured via Loom which applies to both the `main` and `fabric` source sets.
- Access Transformer files are configured via NeoGradle which applies only to the `neoforge` source set.

You configure them just as you would normally using the respective plugins.

Consider using [`modstitch-accessx`](../modstitch-accessx/README.md) to have a canonical place for
acess modification, and to convert between the various formats.

### NeoForge runtime assurance

NeoForge sometimes patches or removes methods from vanilla Minecraft.
This can be a problem for your common (main) source set, because while it will compile,
it will not run on NeoForge due to differing method signatures.

`modstitch-multiloader` creates a compile-time guarantee that your common source set will run on NeoForge.

It does this by attempting to compile your common source set against the NeoForge-patched classpath
when you attempt to compile the NeoForge source set.
It then compares the compiled class files of the common source set against the NeoForge-patched classpath.

This will effectively catch these sorts of issues.

This is also compatible with any Kotlin code.

## How does it work?

1. Applies *both* Fabric Loom and NeoGradle plugins to the same project.
2. Configures them to use dedicated source sets for each loader.
3. Both loaders share the `main` source set, used for common code.

# MTK: `accessx`

A Gradle plugin to help convert between various formats of access modifier files.

Convert between:

- Fabric Class Tweaker v2
- Fabric Class Tweaker v1
- Fabric Access Widener v1
- Fabric Access Widener v2
- (Neo)Forge Access Transformer

Each format has slightly different feature sets. 
`accessx` will fail if you attempt to convert to a target missing features from your source file.
For this reason, it is the recommendation to use the lowest common denominator format as your source file, and convert to the other formats as needed.

## Usage

```kotlin
plugins {
    id("dev.isxander.mtk.accessx") version "0.1.0"
}

accessx {
    // Convert from one source file to another format
    // You may use this for a multi-loader single-JAR setup.
    // Will be added to the main source set's resources
    convert("main") {
        inputFiles.from("mod.accessWidener")
        outputFormat = AT
    }
    
    // Convert from a single source file to multiple formats
    // Each target will be added to the respective source set's resources
    convert("fabric", sourceSets.fabric) { 
        inputFiles.from("mod.accessWidener")
        outputFormat = CT_V2 
    }
    convert("neoforge", sourceSets.neoforge) {
        inputFiles.from("mod.accessWidener")
        outputFormat = AT
    }
    
    // Merge multiple source files (can be different formats)
    // You may want to use this for separation of concerns, or
    // you have multiple targets where each widener file "extends" the previous.
    convert("merged") {
        // source all files in the wideners directory
        inputFiles.from(layout.projectDirectory.dir("wideners"))
        outputFormat = AW_V2
    }
}

// You may also create custom tasks
tasks.register<ConvertAccessxTask>("convertAccessxCustom") {
    inputFiles.from("mod.accessWidener")
    outputFormat = accessx.AT
    outputFile = layout.buildDirectory.file("custom_access.at")
}

```

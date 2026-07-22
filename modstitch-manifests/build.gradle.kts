import org.gradle.plugin.compatibility.compatibility

plugins {
    id("modstitch.gradle-plugin-conventions")
}

version = "0.1.4"

dependencies {
    // For ConvertAccessxTask wiring on FMJ/NMT specs.
    api(project(":modstitch-accessx"))

    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.toml)
}

gradlePlugin {
    plugins {
        register("manifests") {
            id = "dev.isxander.mtk.manifests"
            implementationClass = "dev.isxander.mtk.manifests.ModstitchManifestsPlugin"
            displayName = "MTK: Manifests"
            description = "Mod manifest generation across loaders."
            tags = listOf("modstitch", "minecraft", "manifest")
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

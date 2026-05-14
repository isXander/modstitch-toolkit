import org.gradle.plugin.compatibility.compatibility

plugins {
    id("modstitch.gradle-plugin-conventions")
}

version = "0.1.0"

dependencies {
    // For ConvertAccessxTask wiring on FMJ/NMT specs.
    api(project(":modstitch-accessx"))

    api(libs.night.config.json)
    api(libs.night.config.toml)
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

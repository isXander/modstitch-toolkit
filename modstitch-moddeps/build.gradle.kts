import org.gradle.plugin.compatibility.compatibility

plugins {
    id("modstitch.gradle-plugin-conventions")
}

version = "0.1.0.local.3"

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.toml)
}

gradlePlugin {
    plugins {
        register("moddeps") {
            id = "dev.isxander.mtk.moddeps"
            implementationClass = "dev.isxander.mtk.moddeps.ModdepsPlugin"
            displayName = "MTK: Moddeps"
            description = "Utilities for declaring mod dependencies with configurations."
            tags = listOf("modstitch", "minecraft")
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

import org.gradle.plugin.compatibility.compatibility

plugins {
    id("modstitch.gradle-plugin-conventions")
}

version = "0.1.1"

dependencies {
    compileOnly(libs.plugins.fabric.loom.asDependency())
    compileOnly(libs.plugins.neogradle.asDependency())

    implementation(libs.jackson.databind)
}

gradlePlugin {
    plugins {
        register("multiloader") {
            id = "dev.isxander.mtk.multiloader"
            implementationClass = "dev.isxander.mtk.multiloader.MultiloaderPlugin"
            displayName = "MTK: Multiloader"
            description = "Provides conventions for creating multiloader projects."
            tags = listOf("modstitch-toolkit", "minecraft")
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

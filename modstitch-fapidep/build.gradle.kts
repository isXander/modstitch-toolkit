import org.gradle.plugin.compatibility.compatibility

plugins {
    id("modstitch.gradle-plugin-conventions")
}

version = "0.1.0"

dependencies {
    implementation(libs.plugins.fabric.loom.asDependency())
}

gradlePlugin {
    plugins {
        register("fapidep") {
            id = "dev.isxander.mtk.fapidep"
            implementationClass = "dev.isxander.mtk.fapidep.FapidepPlugin"
            displayName = "MTK: Fapidep"
            description = "Fabric API dependency helpers for Modstitch."
            tags = listOf("modstitch", "minecraft", "fabric")
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

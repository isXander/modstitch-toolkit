import org.gradle.plugin.compatibility.compatibility

plugins {
    id("modstitch.gradle-plugin-conventions")
}

version = "0.1.1"

dependencies {
    compileOnly(libs.plugins.fabric.loom.asDependency())
    compileOnly(libs.plugins.mod.dev.gradle.asDependency())
}

gradlePlugin {
    plugins {
        register("commonconf") {
            id = "dev.isxander.mtk.commonconf"
            implementationClass = "dev.isxander.mtk.commonconf.CommonconfPlugin"
            displayName = "MTK: Commonconf"
            description = "Shared configuration helpers for Minecraft projects."
            tags = listOf("modstitch", "minecraft")
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

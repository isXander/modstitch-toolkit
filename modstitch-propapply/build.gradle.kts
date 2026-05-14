import org.gradle.plugin.compatibility.compatibility

plugins {
    id("modstitch.gradle-plugin-conventions")
}

version = "0.1.0"

gradlePlugin {
    plugins {
        register("propapply") {
            id = "dev.isxander.mtk.propapply"
            implementationClass = "dev.isxander.mtk.propapply.PropapplyPlugin"
            displayName = "MTK: Property Application"
            description = "Applies one of various plugins based on a gradle property."
            tags = listOf("modstitch", "minecraft")
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

plugins {
    `kotlin-dsl`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    implementation(libs.plugins.kotlin.dsl.asDependency())
    implementation(libs.plugins.plugin.publish.asDependency())

    // Expose the version catalog accessors (`libs`) inside precompiled script plugins.
    // https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

fun Provider<PluginDependency>.asDependency(): Provider<String> =
    map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }

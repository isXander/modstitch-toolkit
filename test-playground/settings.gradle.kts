pluginManagement {
    includeBuild("..")

    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net")
        maven("https://maven.neoforged.net/releases")
    }
}
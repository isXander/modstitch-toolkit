pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        register("libs")
    }
}

rootProject.name = "modstitch-toolkit"

// Keep the playground as a standalone consumer build. It includes this build
// for plugin substitution; including it back from here creates a composite loop
// that can leave Gradle/IDE model loading stuck on playground tasks.
includeBuild("test-playground")

include("modstitch-accessx")
include("modstitch-manifests")
include("modstitch-commonconf")
include("modstitch-modrepos")
include("modstitch-propapply")
include("modstitch-multiloader")

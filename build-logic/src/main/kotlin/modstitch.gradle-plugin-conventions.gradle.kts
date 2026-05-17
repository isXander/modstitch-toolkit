import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("org.gradle.kotlin.kotlin-dsl")
    `java-gradle-plugin`
    id("com.gradle.plugin-publish")
}

val libs = the<LibrariesForLibs>()

group = "dev.isxander"

repositories {
    mavenCentral()
    gradlePluginPortal()
    exclusiveContent {
        forRepository { maven("https://maven.fabricmc.net") }
        filter {
            includeGroupAndSubgroups("net.fabricmc")
        }
    }
    maven("https://maven.neoforged.net/releases")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

gradlePlugin {
    website = "https://github.com/isxander/modstitch2"
    vcsUrl = "https://github.com/isxander/modstitch2.git"
}

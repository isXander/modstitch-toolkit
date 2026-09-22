package dev.isxander.mtk.multiloader.publishing

import dev.isxander.mtk.multiloader.utils.*
import org.gradle.api.Project
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.kotlin.dsl.*

/**
 * Sets up the fabric and neoforge source sets.
 *
 * Enables sources jars.
 *
 * Creates the following source sets:
 * - `fabric`
 * - `neoforge`
 *
 * Creates the following features:
 * - `fabric`
 * - `neoforge`
 *
 * Configures the `*Elements` configurations to include capabilities:
 * - `$group:$name:$version` (applied to all)
 * - `$group:$name-common:$version` (applied to main)
 * - `$group:$name-fabric:$version` (applied to fabric)
 * - `$group:$name-neoforge:$version` (applied to neoforge)
 *
 * Configures the `*Elements` configurations to include mcgradleconventions' loader attribute.
 */
internal fun setupFeatures(target: Project) {
    val fabric = target.sourceSets.register("fabric")
    val neoforge = target.sourceSets.register("neoforge")

    target.java.withSourcesJar()
    target.java.withJavadocJar()

    target.java.registerFeature("fabric") {
        usingSourceSet(fabric.get())
        withSourcesJar()
        withJavadocJar()
    }

    target.java.registerFeature("neoforge") {
        usingSourceSet(neoforge.get())
        withSourcesJar()
        withJavadocJar()
    }

    target.tasks.withType<Javadoc> {
        isFailOnError = false
    }

    target.configurations {
        // Add capabilities to all the source sets
        // Every feature (including common) has to have the ambiguous capability,
        // so requesting via attribute has all features as candidates for module resolution.
        configureElements(target.sourceSets.named("main").get()) {
            attributes {
                attribute(modLoaderAttribute, MOD_LOADER_ATTRIBUTE_COMMON)
            }

            outgoing.capability(target.provider { "${target.group}:${target.name}:${target.version}" })
            outgoing.capability(target.provider { "${target.group}:${target.name}-common:${target.version}" })
        }
        configureElements(fabric.get()) {
            attributes {
                attribute(modLoaderAttribute, MOD_LOADER_ATTRIBUTE_FABRIC)
            }

            // fabric capability set by the feature def
            outgoing.capability(target.provider { "${target.group}:${target.name}:${target.version}" })
        }
        configureElements(neoforge.get()) {
            attributes {
                attribute(modLoaderAttribute, MOD_LOADER_ATTRIBUTE_NEOFORGE)
            }

            // neoforge capability set by the feature def
            outgoing.capability(target.provider { "${target.group}:${target.name}:${target.version}" })
        }
    }
}

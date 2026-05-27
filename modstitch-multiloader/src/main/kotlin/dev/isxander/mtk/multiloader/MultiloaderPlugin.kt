@file:Suppress("UnstableApiUsage")

package dev.isxander.mtk.multiloader

import dev.isxander.mtk.multiloader.jarinjar.UniversalJarInJar
import dev.isxander.mtk.multiloader.neoverification.VerifyCommonNeoforgeOutput
import dev.isxander.mtk.multiloader.utils.*
import net.neoforged.gradle.common.tasks.JarJar
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.problems.Problems
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.language.base.plugins.LifecycleBasePlugin
import javax.inject.Inject

class MultiloaderPlugin @Inject constructor(
    private val problems: Problems
) : Plugin<Project> {
    override fun apply(target: Project) {
        applyPlugins(target)
        setupFeatures(target)
        setupCommonConfigurations(target)
        setupFabricClasspath(target)
        setupNeoforgeClasspath(target)
        setupCommonSources(target)
        setupCommonNeoforgeVerification(target)
        setupFabricJarInJar(target)
        setupNeoforgeJarJar(target)
        if (target.conventionUniversalJar.get()) {
            setupUniversalJar(target)
        }
        setupJarDepends(target)
        setupFabricRunConfigs(target)
        setupNeoforgeRunConfigs(target)
        setupClasspathAttributes(target)
    }

    private fun applyPlugins(target: Project) {
        // Used to provide the `api` things.
        target.pluginManager.apply("java-library")

        // Used to provide Minecraft sources to both `main` and `fabric` source set.
        // Requires the following gradle properties in the target project:
        // - `fabric.loom.disableDefaultRunConfigs=true` (IN A PR, NOT IN LOOM YET)
        if (target.conventionLoomRemap.get()) {
            target.pluginManager.apply("net.fabricmc.fabric-loom-remap")
        } else {
            target.pluginManager.apply("net.fabricmc.fabric-loom")
        }

        // Used to provide NeoForge to the `neoforge` source set.
        // Requires the following gradle properties in the target project:
        // This won't actually work until https://github.com/neoforged/NeoGradle/pull/316
        target.extra["neogradle.subsystems.conventions.sourcesets.automatic-inclusion"] = false
        target.extra["neogradle.subsystems.conventions.runs.create-default-run-per-type"] = false
        target.extra["neogradle.subsystems.conventions.configurations.enabled"] = false
        target.extra["neogradle.subsystems.conventions.jarjar.create-main-jarjar=false"] = false

        target.pluginManager.apply("net.neoforged.gradle.userdev")
    }

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
    private fun setupFeatures(target: Project) {
        val fabric = target.sourceSets.register("fabric")
        val neoforge = target.sourceSets.register("neoforge")

        target.java.withSourcesJar()

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

        target.configurations {
            // Add capabilities to all the source sets
            // Every feature (including common) has to have the ambiguous capability,
            // so requesting via attribute has all features as candidates for module resolution.
            configureElements(target.sourceSets.main.get()) {
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

    private val SourceSetContainer.main get() = named("main")
    private val SourceSetContainer.fabric get() = named("fabric")
    private val SourceSetContainer.neoforge get() = named("neoforge")

    /**
     * Creates the following configurations:
     * - `commonCompileOnly`
     * - `commonRuntimeOnly`
     * - `commonImplementation`
     * - `commonApi`
     * - `commonCompileOnlyApi`
     * - `commonAnnotationProcessor`
     *
     * These configurations are used to share common dependencies between the main, fabric, and neoforge source sets.
     */
    private fun setupCommonConfigurations(target: Project) {
        target.configurations {
            val commonCompileOnly = dependencyScope("commonCompileOnly")
            val commonRuntimeOnly = dependencyScope("commonRuntimeOnly")
            val commonImplementation = dependencyScope("commonImplementation")
            val commonApi = dependencyScope("commonApi")
            val commonCompileOnlyApi = dependencyScope("commonCompileOnlyApi")
            val commonAnnotationProcessor = dependencyScope("commonAnnotationProcessor")

            fun inherit(sourceSet: SourceSet) {
                sourceSet.compileOnlyConfigurationName { extendsFrom(commonCompileOnly) }
                sourceSet.runtimeOnlyConfigurationName { extendsFrom(commonRuntimeOnly) }
                sourceSet.implementationConfigurationName { extendsFrom(commonImplementation) }
                sourceSet.apiConfigurationName { extendsFrom(commonApi) }
                sourceSet.compileOnlyApiConfigurationName { extendsFrom(commonCompileOnlyApi) }
                sourceSet.annotationProcessorConfigurationName { extendsFrom(commonAnnotationProcessor) }
            }

            val sourceSets = target.sourceSets
            inherit(sourceSets.main.get())
            inherit(sourceSets.fabric.get())
            inherit(sourceSets.neoforge.get())
        }
    }

    /**
     * Configures the fabric and neoforge source sets to include common output.
     *
     * - Adds the main source set output to each loader source set's compile classpath
     * - Adds the main source set output to each loader source set's runtime classpath
     * - Bundles main output into loader jars
     */
    private fun setupCommonSources(target: Project) {
        val sourceSets = target.sourceSets

        val main = sourceSets.main.get()
        val fabric = sourceSets.fabric.get()
        val neoforge = sourceSets.neoforge.get()

        fun configureSourceSet(sourceSet: SourceSet, localRuntimeName: String) {
            target.dependencies {
                sourceSet.compileOnlyConfigurationName(main.output)
                localRuntimeName(main.output)
            }

            target.tasks {
                named<Jar>(sourceSet.jarTaskName) {
                    duplicatesStrategy = DuplicatesStrategy.FAIL

                    from(main.output)
                }
                named<Jar>(sourceSet.sourcesJarTaskName) {
                    duplicatesStrategy = DuplicatesStrategy.FAIL

                    from(main.allSource)
                }
            }
        }

        configureSourceSet(fabric, "fabricLocalRuntime")
        configureSourceSet(neoforge, "neoforgeLocalRuntime")
    }

    /**
     * NeoForge patches vanilla methods and changes their signatures.
     * We need to ensure that the compiled common code is compatible with a NeoForge runtime.
     *
     * To do this, we create an internal source set, `commonNeoforgeCompat`,
     * and run `classes` task. If it succeeds, then the common code is compatible with NeoForge.
     */
    private fun setupCommonNeoforgeVerification(target: Project) {
        val sourceSets = target.sourceSets

        val main = sourceSets.main.get()
        val neoforge = sourceSets.neoforge.get()

        val commonNeoforgeCheck = sourceSets.register("commonNeoforgeCheck") {
            val mainOutput = target.files(main.output)

            java.setSrcDirs(main.java.srcDirs)
            main.kotlin?.let { kotlin?.setSrcDirs(it.srcDirs) }

            resources.setSrcDirs(emptyList<Any>())

            compileClasspath = target.files(
                neoforge.compileClasspath,
            ) - mainOutput

            annotationProcessorPath = target.files(
                neoforge.annotationProcessorPath,
            )

            runtimeClasspath = output + compileClasspath
        }
        val commonNeoforgeCheckClasses = target.tasks.named(commonNeoforgeCheck.get().classesTaskName)

        // Compare bytecode from `commonNeoforgeCheckClasses` (compiled against the NeoForge classpath)
        // against bytecode from `main` (compiled against the common classpath).
        // If they diverge, the common code resolves to different method signatures under NeoForge.
        val verifyCommonNeoforgeOutput = target.tasks.register<VerifyCommonNeoforgeOutput>("verifyCommonNeoforgeOutput") {
            group = "modstitch/multiloader"

            dependsOn(main.classesTaskName, commonNeoforgeCheckClasses)
            mainClasses.from(main.output.classesDirs)
            checkClasses.from(commonNeoforgeCheck.get().output.classesDirs)
        }

        // Any successful NeoForge `classes` build must prove common is NeoForge-compatible.
        target.tasks.named(neoforge.classesTaskName) {
            dependsOn(verifyCommonNeoforgeOutput)
        }
    }

    /**
     * Configures the fabric source set to use the Loom-provided Minecraft classpath.
     *
     * - Extends the fabric source set's compile/runtime classpath with the Minecraft classpath
     * - Sets up `fabricLocalRuntime` configuration
     * - Removes the Loom-created `localRuntime` configuration
     *
     * @see setupCommonSources
     */
    private fun setupFabricClasspath(target: Project) {
        val main = target.sourceSets.main.get()
        val fabric = target.sourceSets.fabric.get()

        target.configurations {
            // The `main` source set *needs* Fabric Loader in order for Loom to resolve its dependencies such as
            // Mixin and ASM. We make a utility configuration for users to use so they don't need to define it twice.
            // TODO: Somehow prevent fabric-loader.jar being on the main compile classpath, and JUST let Loom resolve its dependencies
            val fabricLoader = register("fabricLoader")
            named(main.compileOnlyConfigurationName) { extendsFrom(fabricLoader) }
            named(fabric.implementationConfigurationName) { extendsFrom(fabricLoader) }

            val fabricLocalRuntime = dependencyScope("fabricLocalRuntime")
            named(fabric.runtimeClasspathConfigurationName) { extendsFrom(fabricLocalRuntime) }

            // Fabric generates a `localRuntime` configuration,
            // but this is for the main source set and therefore unwanted.
            removeIf { it.name == "localRuntime" }

            // Loom doesn't create the `minecraftNamed*` until afterEvaluate
            // https://github.com/FabricMC/fabric-loom/blob/cf42ac/src/main/java/net/fabricmc/loom/configuration/providers/minecraft/MinecraftSourceSets.java#L123
            target.afterEvaluate {
                val fabric = target.sourceSets.fabric.get()

                // Get *only* the Minecraft-related classpath (includes loader dependencies such as mixin)
                // and give that to fabric. This is instead of mutating the whole classpath in the source set.
                // Common dependencies that need to be shared amongst common and fabric should use the common configurations instead.
                // WARNING: modmuss has said these configurations are Loom internal and subject to breakage/change
                fabric.compileClasspathConfigurationName { extendsFrom(named("minecraftNamedCompile")) }
                fabric.runtimeClasspathConfigurationName { extendsFrom(named("minecraftNamedRuntime")) }
            }
        }
    }

    /**
     * Registers a `neoforgeLocalRuntime` configuration.
     *
     * - Creates and configures the `neoforgeLocalRuntime` configuration
     */
    private fun setupNeoforgeClasspath(target: Project) {
        target.configurations {
            val neoforge = target.sourceSets.neoforge.get()

            // Create a local runtime configuration scoped for the neoforge source set
            val neoforgeLocalRuntime = dependencyScope("neoforgeLocalRuntime")
            neoforge.runtimeClasspathConfigurationName { extendsFrom(neoforgeLocalRuntime) }
        }
    }

    /**
     * Configures fabric jar-in-jar for the fabric source set.
     *
     * - Prevents use of the default `include` configuration.
     * - Creates a new `fabricInclude` configuration.
     * - Instructs nest jars in `fabricInclude` into `fabricJar`.
     */
    private fun setupFabricJarInJar(target: Project) {
        target.configurations.named("include") {
            dependencies.whenObjectAdded {
                val dependency = this
                throw problems.reporter.throwingLoomIncludeConfigUsage(dependency.name)
            }
        }

        val fabricInclude = target.configurations.dependencyScope("fabricInclude")
        target.loom.nestJars(target.tasks.named<Jar>("fabricJar"), fabricInclude)
    }

    /**
     * Configures jarJar for the neoforge source set.
     *
     * - Disables the default jarJar feature targeting the main source set.
     * - Enables the jarJar feature targeting the neoforge source set.
     * - Ensures that the jarJar task output does not have the default `-all` classifier.
     */
    private fun setupNeoforgeJarJar(target: Project) {
        // disable the default jarJar feature
        target.jarJar.disable()

        // create the jarJar feature for neoforge
        target.jarJar.forFeature("neoforge")
            .enable() // force-enable jarJar so it produces its jar even when there is nothing to embed

        target.tasks {
            val neoforge = target.sourceSets.neoforge.get()

            // By default, jarJar produces a jar with the classifier suffix `-all`.
            // We instead want the final jar to have no extra classifier, so this swaps around,
            // so the normal jar is the `-slim` and the jarJar is the main classifier.
            named<Jar>(neoforge.jarTaskName) {
                archiveClassifier = "neoforge-slim"
            }
            named<JarJar>("neoforgeJarJar") {
                archiveClassifier = "neoforge"
            }
        }

        target.configurations {
            val neoforgeInclude = dependencyScope("neoforgeInclude")
            named("neoforgeJarJar") { extendsFrom(neoforgeInclude) }
        }
    }

    /**
     * Configures a jar task to create a universal jar that includes all source sets' classes and universal resources.
     *
     * - Creates a `universalJar` task that includes all source sets' classes.
     * - Creates a `universalSourcesJar` task that includes all source sets' sources.
     * - Adds these tasks to the `assemble` task.
     */
    private fun setupUniversalJar(target: Project) {
        val sourceSets = target.sourceSets

        val universalJar = target.tasks.register<Jar>("universalJar") {
            group = "build"

            archiveClassifier = "universal"
            duplicatesStrategy = DuplicatesStrategy.FAIL

            // only add classes dirs so we can do another resource
            // processing step for Universal JarInJar
            from(sourceSets.main.map { it.output.classesDirs })
            from(sourceSets.fabric.map { it.output.classesDirs })
            from(sourceSets.neoforge.map { it.output.classesDirs })
        }

        val universalJarInJar = target.objects.newInstance<UniversalJarInJar>()
        universalJarInJar.setup(
            target = target,
            universalJar = universalJar,
        )

        target.tasks.register<Jar>("universalSourcesJar") {
            group = "build"

            archiveClassifier = "universal-sources"
            duplicatesStrategy = DuplicatesStrategy.FAIL

            from(sourceSets.main.map { it.allSource })
            from(sourceSets.fabric.map { it.allSource })
            from(sourceSets.neoforge.map { it.allSource })
        }

        target.tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) {
            dependsOn("universalJar", "universalSourcesJar")
        }
    }

    /**
     * Configures the `assemble` task to depend on the fabric and neoforge jar tasks.
     * This ensures that the `build` task will produce all jars.
     */
    private fun setupJarDepends(target: Project) {
        val sourceSets = target.sourceSets
        val fabric = sourceSets.fabric.get()
        val neoforge = sourceSets.neoforge.get()

        target.tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) {
            dependsOn(fabric.jarTaskName, fabric.sourcesJarTaskName)
            dependsOn(neoforge.jarTaskName, neoforge.sourcesJarTaskName)
        }
    }

    /**
     * Configures everything related to fabric loom run configs:
     * - Removes the default run configs created by Loom that are for the main source set.
     * - Ensures all created run configs use the fabric source set.
     * - Creates some default run configs: `fabricClient` and `fabricServer`.
     */
    private fun setupFabricRunConfigs(target: Project) {
        target.loom.runConfigs {
            // Loom creates a client and server run config by default,
            // these are for the main source set and therefore unwanted.
            remove(getByName("client"))
            remove(getByName("server"))

            // All run configs should be for the fabric source set.
            configureEach {
                sourceSet = target.sourceSets.fabric.name
            }

            if (target.conventionCreateDefaultRuns.get()) {
                // Create some default run configs
                register("fabricClient") {
                    client()
                }
                register("fabricServer") {
                    server()
                }
            }
        }
    }

    /**
     * Configures everything related to neoforge run configs:
     * - Ensures all created run configs use the neoforge source set.
     * - Creates some default run configs: `neoforgeClient` and `neoforgeServer`.
     *
     * This is done with the assumption that the NeoGradle convention:
     * `neogradle.subsystems.conventions.runs.create-default-run-per-type=false`
     * is set, which users will have to configure themselves.
     * Without this convention set, the run config tasks created will conflict with those of the
     * same name created by Loom, which causes a build failure.
     */
    private fun setupNeoforgeRunConfigs(target: Project) {
        target.ngRuns {
            // The convention `neogradle.subsystems.conventions.runs.create-default-run-per-type=false`
            // prevents NeoGradle from making default run configs like Loom

            configureEach {
                val main = target.sourceSets.main.get()
                val neoforge = target.sourceSets.neoforge.get()

                // All run configs should be for the neoforge source set.
                modSources {
                    add(neoforge)
                    primary = neoforge
                }
                // Makes the IDEA run configs use the correct classpath.
                idea.primarySourceSet = neoforge
            }

            if (target.conventionCreateDefaultRuns.get()) {
                // Create some default run configs
                register("neoforgeClient") {
                    runType("client")
                }
                register("neoforgeServer") {
                    runType("server")
                }
            }
        }
    }

    /**
     * Allows consumption of artifacts with automatic module resolution.
     * If consumers request an artifact and that module has the mcgradleconventions loader attribute,
     * this will allow the artifact to be resolved automatically based on which source set it is requested from.
     *
     * This allows:
     * ```kts
     * dependencies {
     *     // Will resolve the `gizmo-1.0.0.jar` artifact
     *     implementation("org.example:gizmo:1.0.0")
     *     // Will resolve the `gizmo-1.0.0-fabric.jar` artifact
     *     fabricImplementation("org.example:gizmo:1.0.0")
     *     // Will resolve the `gizmo-1.0.0-neoforge.jar` artifact
     *     neoforgeImplementation("org.example:gizmo:1.0.0")
     * }
     * ```
     * Where each will resolve a different variant of the `gizmo` artifact, if it also supports
     * the mcgradleconventions loader attribute. This allows more effective use of Gradle
     * version catalogs.
     */
    private fun setupClasspathAttributes(target: Project) {
        val sourceSets = target.sourceSets

        target.configurations.configureClasspaths(sourceSets.main.get()) {
            attributes {
                attribute(modLoaderAttribute, MOD_LOADER_ATTRIBUTE_COMMON)
            }
        }
        target.configurations.configureClasspaths(sourceSets.fabric.get()) {
            attributes {
                attribute(modLoaderAttribute, MOD_LOADER_ATTRIBUTE_FABRIC)
            }
        }
        target.configurations.configureClasspaths(sourceSets.neoforge.get()) {
            attributes {
                attribute(modLoaderAttribute, MOD_LOADER_ATTRIBUTE_NEOFORGE)
            }
        }
    }
}

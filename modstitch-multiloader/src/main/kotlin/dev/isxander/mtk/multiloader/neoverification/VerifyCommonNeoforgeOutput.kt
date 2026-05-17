@file:Suppress("UnstableApiUsage")

package dev.isxander.mtk.multiloader.neoverification

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.Problems
import org.gradle.api.problems.Severity
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import javax.inject.Inject

/**
 * Verifies that the common code is binary-compatible with the NeoForge runtime.
 *
 * NeoForge patches vanilla Minecraft methods and changes their signatures.
 * Common code only compiles against the unpatched (Fabric/Loom-mapped) classpath,
 * so identical source can resolve to different method signatures under NeoForge —
 * silently producing bytecode that would `NoSuchMethodError` at runtime on a NeoForge install.
 *
 * To catch this, the plugin compiles the common sources a second time against the NeoForge
 * classpath in the `commonNeoforgeCheck` source set. This task then compares the resulting
 * `.class` files byte-for-byte against the main source set's output. Identical bytecode proves
 * every referenced signature resolved to the same target under both classpaths.
 *
 * The two source sets are assumed to share the same source roots, so the set of class file
 * paths is identical on both sides; only bytecode contents are compared.
 *
 * Wired as a dependency of the `neoforge` source set's `classes` task — any successful
 * NeoForge build implies the common code is NeoForge-compatible.
 */
@CacheableTask
abstract class VerifyCommonNeoforgeOutput : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val mainClasses: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val checkClasses: ConfigurableFileCollection

    @get:Inject
    protected abstract val problems: Problems

    @TaskAction
    fun verify() {
        val mainFiles = collect(mainClasses)
        val checkFiles = collect(checkClasses)

        val differing = mainFiles.keys.intersect(checkFiles.keys)
            .filter { Files.mismatch(mainFiles[it]!!.toPath(), checkFiles[it]!!.toPath()) != -1L }
            .sorted()

        if (differing.isNotEmpty()) {
            val message = "Common code is not NeoForge-compatible: bytecode differs from main for:\n" +
                differing.joinToString("\n") { "  - $it" }

            throw problems.reporter.throwing(RuntimeException(message), PROBLEM_ID) {
                contextualLabel("${differing.size} class file(s) compiled differently against the NeoForge classpath")
                details(
                    "These classes resolved to different method signatures when compiled against NeoForge. " +
                        "This typically means common code calls a vanilla Minecraft method that NeoForge has patched."
                )
                solution("Move the offending call into a loader-specific source set, or guard it behind an abstraction implemented per loader.")
                severity(Severity.ERROR)
            }
        }
    }

    private companion object {
        val PROBLEM_ID: ProblemId = ProblemId.create(
            "common-neoforge-bytecode-mismatch",
            "Common bytecode incompatible with NeoForge",
            ProblemGroup.create("modstitch-multiloader", "Modstitch Multiloader"),
        )
    }

    private fun collect(dirs: Iterable<File>): Map<String, File> {
        val out = mutableMapOf<String, File>()
        for (root in dirs) {
            if (!root.isDirectory) continue
            root.walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .forEach { out[it.relativeTo(root).invariantSeparatorsPath] = it }
        }
        return out
    }
}
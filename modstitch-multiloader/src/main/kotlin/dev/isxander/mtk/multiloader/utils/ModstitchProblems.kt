@file:Suppress("UnstableApiUsage")

package dev.isxander.mtk.multiloader.utils

import org.gradle.api.GradleException
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemReporter
import org.gradle.api.problems.Severity

object ModstitchProblems {
    val GROUP = ProblemGroup.create("modstitch-multiloader", "Modstitch Multiloader")

    val UNIVERSAL_JAR_IN_JAR_FMJ_PARSE_FAILURE_ID = ProblemId.create(
        "jarinjar-fabric-mod-json-parse-failure",
        "Could not parse fabric.mod.json:",
        GROUP,
    )

    val UNIVERSAL_JAR_IN_JAR_MISSING_COORDINATES_ID = ProblemId.create(
        "jarinjar-missing-coordinates",
        "Universal Jar-in-Jar dependency has no coordinates.",
        GROUP,
    )

    val UNIVERSAL_JAR_IN_JAR_DUPLICATE_PATH_ID = ProblemId.create(
        "jarinjar-duplicate-path",
        "Universal Jar-in-Jar dependencies use the same embedded path.",
        GROUP,
    )

    val LOOM_INCLUDE_CONFIG_USAGE_ID = ProblemId.create(
        "loom-include-config-usage",
        "The Loom `include` configuration should not be used when using modstitch-multiloader.",
        GROUP,
    )
}

fun ProblemReporter.throwingUniversalJarInJarFMJParseFailure(
    exception: Exception,
    filePath: String,
) = throwing(
        exception,
        ModstitchProblems.UNIVERSAL_JAR_IN_JAR_FMJ_PARSE_FAILURE_ID,
    ) {
        severity(Severity.ERROR)
        contextualLabel(
            "The fabric.mod.json file could not be parsed as valid JSON.",
        )
        fileLocation(filePath)
        details(
            "Modstitch Multiloader found fabric.mod.json and tried to patch it with embedded jar " +
                    "entries for the universal jar, but the file could not be parsed as JSON. " +
                    "This usually means the fabric.mod.json file in one of your source sets contains " +
                    "invalid JSON syntax."
        )
        solution(
            "Check the fabric.mod.json file in your source sets and make sure it is valid JSON."
        )
        solution(
            "If fabric.mod.json is generated or processed by another task, check the generated file before this task runs."
        )
    }

fun ProblemReporter.throwingUniversalJarInJarMissingCoordinates(
    fileNames: Collection<String>,
) = throwing(
        GradleException(
            "Cannot create universal Jar-in-Jar metadata for ${fileNames.joinToString()}. " +
                "Use module or project dependencies so Modstitch can resolve coordinates and version ranges.",
        ),
        ModstitchProblems.UNIVERSAL_JAR_IN_JAR_MISSING_COORDINATES_ID,
    ) {
        severity(Severity.ERROR)
        contextualLabel(
            "Universal Jar-in-Jar could not identify coordinates for ${fileNames.joinToString()}.",
        )
        details(
            "The universal jar describes each embedded jar to both Fabric and NeoForge. " +
                "That metadata needs resolved dependency coordinates and version ranges, which are not " +
                "available for these resolved files.",
        )
        solution(
            "Use module or project dependencies for universal Jar-in-Jar includes.",
        )
    }

fun ProblemReporter.throwingUniversalJarInJarDuplicatePath(
    path: String,
) = throwing(
        GradleException("Trying to embed multiple jars at $path."),
        ModstitchProblems.UNIVERSAL_JAR_IN_JAR_DUPLICATE_PATH_ID,
    ) {
        severity(Severity.ERROR)
        contextualLabel(
            "Multiple universal Jar-in-Jar dependencies resolve to '$path'.",
        )
        details(
            "Embedded jars keep their resolved file names under the universal jar's embedded jar directory. " +
                "Two dependencies cannot share the same target path because only one jar could be written there.",
        )
        solution(
            "Use dependencies that resolve to distinct jar file names for universal Jar-in-Jar includes.",
        )
    }

fun ProblemReporter.throwingLoomIncludeConfigUsage(
    dependencyName: String
) = throwing(
        GradleException(
            "Do not use Loom's 'include' configuration with modstitch-multiloader.",
        ),
        ModstitchProblems.LOOM_INCLUDE_CONFIG_USAGE_ID,
    ) {
        contextualLabel(
            "Dependency '$dependencyName' was added to the unsupported 'include' configuration.",
        )
        severity(Severity.ERROR)
        details(
            "The 'include' configuration is created by Loom. modstitch-multiloader needs " +
                    "included dependencies to target a specific source set or jar instead.",
        )
        solution("Use 'commonInclude' to include the dependency for all loaders.")
        solution("Use 'fabricInclude' to include the dependency only for Fabric.")
        solution("Use 'neoforgeInclude' to include the dependency only for NeoForge.")
    }

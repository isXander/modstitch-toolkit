package dev.isxander.mtk.multiloader.jarinjar

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*

open class ResolvedEmbeddedJar(
    /** The file; path relative to the jar */
    @get:Input
    val path: String,

    @get:Input
    val group: String,

    @get:Input
    val artifact: String,

    @get:Input
    val classifier: String?,

    @get:Input
    val version: String,

    @get:Input
    val mavenVersionRange: String,
) {
    /**
     * Exact mod id format [copied from Loom](https://github.com/FabricMC/fabric-loom/blob/dev/1.16/src/main/java/net/fabricmc/loom/build/nesting/NestableJarGenerationTask.java#L174).
     */
    @get:Internal
    val fabricModId: String
        get() = listOfNotNull(group, artifact, classifier)
            .joinToString("_")
            .replace('.', '_')
            .lowercase(Locale.ENGLISH)
            .let { id ->
                if (id.length > FABRIC_MOD_ID_MAX_LENGTH) {
                    id.take(FABRIC_MOD_ID_HASH_PREFIX_LENGTH) +
                            id.sha256().take(FABRIC_MOD_ID_HASH_LENGTH)
                } else {
                    id
                }
            }

    @get:Internal
    val fabricVersion: String
        get() = version.removeFinalSuffixIfSemver()

    private companion object {
        const val FABRIC_MOD_ID_MAX_LENGTH = 64
        const val FABRIC_MOD_ID_HASH_PREFIX_LENGTH = 50
        const val FABRIC_MOD_ID_HASH_LENGTH = FABRIC_MOD_ID_MAX_LENGTH - FABRIC_MOD_ID_HASH_PREFIX_LENGTH

        val FABRIC_SEMVER = Regex(
            """^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?$""",
        )

        /**
         * Copied from [Fabric Loom](https://github.com/FabricMC/fabric-loom/blob/dev/1.16/src/main/java/net/fabricmc/loom/build/nesting/NestableJarGenerationTask.java#L207)
         *
         * Apparently common for Kotlin projects to have a `.Final` suffix on their version.
         * This breaks Fabric Loader semver, so it is stripped.
         */
        private fun String.removeFinalSuffixIfSemver(): String {
            val trimmed = removeSuffix(".Final").removeSuffix(".final")
            return trimmed.takeIf(FABRIC_SEMVER::matches) ?: this
        }

        private fun String.sha256(): String =
            MessageDigest.getInstance("SHA-256")
                .digest(toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}

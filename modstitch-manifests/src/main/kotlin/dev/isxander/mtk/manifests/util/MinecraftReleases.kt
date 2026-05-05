package dev.isxander.mtk.manifests.util

import dev.isxander.mtk.manifests.spec.VersionRange
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import java.net.URI

/**
 * Fetches the list of released Minecraft versions from the official launcher
 * metadata endpoint, dropping snapshots, pre-releases, and any version whose
 * id does not parse as a numeric dotted version.
 *
 * Cached for the build invocation by Gradle's [ValueSource] machinery and
 * safe under the configuration cache.
 */
abstract class MinecraftReleasesValueSource : ValueSource<List<String>, ValueSourceParameters.None> {
    override fun obtain(): List<String> {
        val text = URI(MANIFEST_URL).toURL().openStream().bufferedReader().use { it.readText() }
        // Each entry has the shape: { "id": "...", "type": "release|snapshot|...", ... }.
        // A regex extraction sidesteps the need for a JSON parsing dependency.
        return ENTRY.findAll(text)
            .filter { it.groupValues[2] == "release" }
            .map { it.groupValues[1] }
            .filter { VersionRange.Version.parseOrNull(it) != null }
            .sorted()
            .toList()
    }

    private companion object {
        const val MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
        val ENTRY = Regex(""""id"\s*:\s*"([^"]+)"\s*,\s*"type"\s*:\s*"([^"]+)"""")
    }
}

package dev.isxander.mtk.manifests.util

import dev.isxander.mtk.manifests.gen.jsonMapper
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
        val json = URI(MANIFEST_URL).toURL()
            .openStream()
            .bufferedReader()
            .use { jsonMapper.reader().readTree(it) }

        val versions = json["versions"].asArray().values().map {
            Version(
                it["id"].asString(),
                it["type"].asString(),
                it["releaseTime"].asString(),
            )
        }

        return versions
            .filter { it.type == "release" }
            .filter { VersionRange.Version.parseOrNull(it.id) != null }
            .sortedBy { it.releaseTime }
            .map { it.id }
            .toList()
    }

    private companion object {
        const val MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json"
    }

    private data class Version(val id: String, val type: String, val releaseTime: String)
}

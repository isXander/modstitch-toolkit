package dev.isxander.mtk.manifests

import dev.isxander.mtk.manifests.util.MinecraftReleasesValueSource
import org.gradle.api.provider.ValueSourceParameters
import kotlin.test.*

class MinecraftVersionTest {
    @Test
    fun `version fetching`() {
        val allVersions = object : MinecraftReleasesValueSource() {
            override fun getParameters(): ValueSourceParameters.None = error("Not used")
        }.obtain()

        assertTrue { allVersions.contains("1.20.1") }
        assertTrue { allVersions.contains("1.19.4") }
        assertTrue { allVersions.contains("26.1.2") }
        assertFalse { allVersions.contains("26.1-snapshot-1") }
    }
}
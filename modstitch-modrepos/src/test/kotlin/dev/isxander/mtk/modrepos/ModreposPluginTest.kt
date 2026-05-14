package dev.isxander.mtk.modrepos

import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.plugins.ExtensionAware
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ModreposPluginTest {
    @Test
    fun `top-level repository helpers add named maven repositories`() {
        val project = ProjectBuilder.builder().build()

        val minecraft = project.repositories.minecraft()
        val modrinth = project.repositories.modrinthApi()

        assertEquals("Minecraft Libraries", minecraft.name)
        assertEquals(project.uri("https://libraries.minecraft.net"), minecraft.url)
        assertEquals("Modrinth API Maven", modrinth.name)
        assertEquals(project.uri("https://api.modrinth.com/maven"), modrinth.url)
    }

    @Test
    fun `plugin registers invokable repository extensions`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply(ModreposPlugin::class.java)

        val repositories = project.repositories as ExtensionAware
        val fabricMC = repositories.extensions.getByName("fabricMC")
        val modrinthApi = repositories.extensions.getByName("modrinthApi")

        val fabricRepository = assertIs<ModRepoExtension>(fabricMC).invoke()
        val modrinthRepository = assertIs<ExclusiveModRepoExtension>(modrinthApi).invoke()

        val fabricMaven = assertIs<MavenArtifactRepository>(fabricRepository)
        assertEquals("FabricMC", fabricMaven.name)
        assertEquals(project.uri("https://maven.fabricmc.net"), fabricMaven.url)

        val modrinthMaven = assertIs<MavenArtifactRepository>(modrinthRepository)
        assertEquals("Modrinth API Maven", modrinthMaven.name)
        assertEquals(project.uri("https://api.modrinth.com/maven"), modrinthMaven.url)
    }
}

package dev.isxander.mtk.manifests.gen

import com.electronwill.nightconfig.core.Config
import com.electronwill.nightconfig.json.JsonFormat
import com.electronwill.nightconfig.toml.TomlFormat
import dev.isxander.mtk.manifests.spec.FabricModJsonSpec
import dev.isxander.mtk.manifests.spec.ModManifestSpec.DependencyType
import dev.isxander.mtk.manifests.spec.ModManifestSpec.Side
import dev.isxander.mtk.manifests.spec.NeoForgeModsTomlSpec
import dev.isxander.mtk.manifests.spec.VersionRange
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals

class ManifestGeneratorTest {
    @Test
    fun `fabric generator emits common metadata entrypoints mixins contacts and dependency buckets`() {
        val spec = ProjectBuilder.builder().build().objects.newInstance(FabricModJsonSpec::class.java).apply {
            modId.set("example")
            version.set("1.2.3")
            displayName.set("Example Mod")
            description.set("Does useful things")
            licenses.addAll("MIT", "Apache-2.0")
            authors.add("isXander")
            homepage.set("https://example.test")
            sourcesUrl.set("https://example.test/source")
            issueTrackerUrl.set("https://example.test/issues")
            iconPath.set("assets/example/icon.png")
            client()
            entrypoint("main", "example.Main")
            entrypoint("client", "example.Client", "kotlin")
            mixin("example.mixins.json")
            mixin("example.client.mixins.json", Side.CLIENT)
            accessWidener("example.accesswidener")
            depends("minecraft", "[1.20,1.21)")
            suggests("modmenu")
            breaks("badmod", "[2.0]")
            contactInformation.put("discord", "https://example.test/discord")
            languageAdapters.put("kotlin", "net.fabricmc.language.kotlin.KotlinAdapter")
            customData.put("extra", "value")
        }

        val expectedText = resourceText("fabric.mod.json")
        val actualText = FabricModJsonGenerator.generate(spec)
        val expected = JsonFormat.fancyInstance().createParser().parse(expectedText.reader())
        val actual = JsonFormat.fancyInstance().createParser().parse(actualText.reader())

        assertEquals(normalize(expected), normalize(actual))
        assertEquals(topLevelJsonKeys(expectedText), topLevelJsonKeys(actualText))
    }

    @Test
    fun `neoforge generator emits file mod feature modproperty and dependency sections`() {
        val spec = ProjectBuilder.builder().build().objects.newInstance(NeoForgeModsTomlSpec::class.java).apply {
            modId.set("example")
            namespace.set("example_ns")
            version.set("1.2.3")
            displayName.set("Example Mod")
            description.set("Does useful things")
            licenses.addAll("MIT", "Apache-2.0")
            modLoader.set("javafml")
            loaderVersion.set("[4,)")
            showAsResourcePack.set(true)
            services.add("example.Service")
            fileProperties.put("catalogueImageIcon", "assets/example/icon.png")
            issueTrackerUrl.set("https://example.test/issues")
            homepage.set("https://example.test")
            contributors.addAll("Contributor One", "Contributor Two")
            authors.addAll("Author One", "Author Two")
            logoFile.set("assets/example/icon.png")
            logoBlur.set(false)
            javaVersion.set("[21,)")
            modProperties.put("catalogueItemIcon", "example:item")
            required("minecraft", "[1.20,1.21)")
            dependency("client_only", DependencyType.OPTIONAL, VersionRange.parseMaven("[1.0,)"), Side.CLIENT)
        }

        val expectedText = resourceText("neoforge.mods.toml")
        val actualText = NeoForgeModsTomlGenerator.generate(spec)
        val expected = TomlFormat.instance().createParser().parse(expectedText.reader())
        val actual = TomlFormat.instance().createParser().parse(actualText.reader())

        assertEquals(normalize(expected), normalize(actual))
    }

    private fun resourceText(name: String): String =
        requireNotNull(javaClass.getResource("/dev/isxander/mtk/manifests/gen/$name")) {
            "Missing test resource $name"
        }.readText()

    private fun normalize(value: Any?): Any? =
        when (value) {
            is Config -> value.entrySet().associate { entry -> entry.key to normalize(entry.getRawValue<Any?>()) }
            is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to normalize(v) }
            is List<*> -> value.map(::normalize)
            else -> value
        }

    private fun topLevelJsonKeys(json: String): List<String> {
        val keys = mutableListOf<String>()
        var depth = 0
        var inString = false
        var escaped = false
        var i = 0

        while (i < json.length) {
            val c = json[i]
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> {
                    inString = !inString
                    if (inString && depth == 1) {
                        val end = findStringEnd(json, i + 1)
                        var j = end + 1
                        while (j < json.length && json[j].isWhitespace()) j++
                        if (j < json.length && json[j] == ':') {
                            keys += json.substring(i + 1, end)
                        }
                        i = end
                        inString = false
                    }
                }
                !inString && c == '{' -> depth++
                !inString && c == '}' -> depth--
            }
            i++
        }

        return keys
    }

    private fun findStringEnd(json: String, from: Int): Int {
        var escaped = false
        for (i in from until json.length) {
            val c = json[i]
            when {
                escaped -> escaped = false
                c == '\\' -> escaped = true
                c == '"' -> return i
            }
        }
        error("Unterminated string in JSON fixture")
    }

}

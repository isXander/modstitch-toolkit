package dev.isxander.mtk.manifests.gen

import com.electronwill.nightconfig.core.Config
import com.electronwill.nightconfig.json.JsonFormat
import com.electronwill.nightconfig.toml.TomlFormat
import org.gradle.api.provider.Provider
import java.util.LinkedHashMap

internal fun <T : Any> Config.addProperty(key: String, property: Provider<T>, required: Boolean = false) =
    property
        .takeIf { it.isPresent || required }
        ?.let { add(key, it.get()) }

internal fun <T> Config.addListProperty(key: String, property: Provider<List<T>>, addEmpty: Boolean = false) =
    property.getOrElse(emptyList())
        .takeIf { it.isNotEmpty() || addEmpty }
        ?.let { add(key, it) }

internal fun <K, V> Config.addMapProperty(key: String, property: Provider<Map<K, V>>, addEmpty: Boolean = false) =
    property.getOrElse(emptyMap())
        .takeIf { it.isNotEmpty() || addEmpty }
        ?.let { addMap(key, it) }

internal fun <K, V> Config.addMap(key: String, values: Map<K, V>) {
    add(key, createSubConfig().apply {
        values.forEach { (propertyKey, propertyValue) ->
            add(propertyKey.toString(), propertyValue)
        }
    })
}

internal fun orderedJsonConfig(): Config =
    JsonFormat.newConfig(::LinkedHashMap)

internal fun orderedTomlConfig(): Config =
    TomlFormat.newConfig(::LinkedHashMap)

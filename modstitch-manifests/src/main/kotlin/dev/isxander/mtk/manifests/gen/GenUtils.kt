package dev.isxander.mtk.manifests.gen

import org.gradle.api.provider.Provider
import tools.jackson.core.util.DefaultIndenter
import tools.jackson.core.util.DefaultPrettyPrinter
import tools.jackson.databind.JsonNode
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.NullNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.dataformat.toml.TomlMapper

internal val jsonNodeFactory = JsonNodeFactory.instance

private val prettyPrinter = DefaultPrettyPrinter()
    .withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)

internal val jsonMapper = JsonMapper.builder()
    .defaultPrettyPrinter(prettyPrinter)
    .enable(SerializationFeature.INDENT_OUTPUT)
    .build()

internal val tomlMapper = TomlMapper.builder()
    .defaultPrettyPrinter(prettyPrinter)
    .enable(SerializationFeature.INDENT_OUTPUT)
    .build()


internal fun <T : Any> ObjectNode.addProperty(key: String, property: Provider<T>, required: Boolean = false) =
    property
        .takeIf { it.isPresent || required }
        ?.let { add(key, it.get()) }

internal fun <T> ObjectNode.addListProperty(key: String, property: Provider<List<T>>, addEmpty: Boolean = false) =
    property.getOrElse(emptyList())
        .takeIf { it.isNotEmpty() || addEmpty }
        ?.let { add(key, it) }

internal fun <K, V> ObjectNode.addMapProperty(key: String, property: Provider<Map<K, V>>, addEmpty: Boolean = false) =
    property.getOrElse(emptyMap())
        .takeIf { it.isNotEmpty() || addEmpty }
        ?.let { addMap(key, it) }

internal fun <K, V> ObjectNode.addMap(key: String, values: Map<K, V>) {
    add(key, jsonNodeFactory.objectNode().apply {
        values.forEach { (propertyKey, propertyValue) ->
            add(propertyKey.toString(), propertyValue)
        }
    })
}

internal fun ObjectNode.add(key: String, value: Any?) {
    val path = key.split('.')
    val parent = path.dropLast(1).fold(this) { node, segment ->
        node.objectAt(segment)
    }

    parent.set(path.last(), value.toJsonNode())
}

private fun ObjectNode.objectAt(key: String): ObjectNode {
    val existing = get(key)
    if (existing is ObjectNode) {
        return existing
    }

    return jsonNodeFactory.objectNode().also { set(key, it) }
}

private fun Any?.toJsonNode(): JsonNode =
    when (this) {
        null -> NullNode.instance
        is JsonNode -> this
        is Map<*, *> -> jsonNodeFactory.objectNode().apply {
            entries.forEach { (key, value) -> add(key.toString(), value) }
        }
        is Iterable<*> -> jsonNodeFactory.arrayNode().addAll(mapToNodes(this))
        is Array<*> -> jsonNodeFactory.arrayNode().addAll(mapToNodes(asIterable()))
        else -> jsonMapper.valueToTree(this)
    }

private fun mapToNodes(values: Iterable<*>): ArrayNode =
    jsonNodeFactory.arrayNode().apply {
        values.forEach { add(it.toJsonNode()) }
    }

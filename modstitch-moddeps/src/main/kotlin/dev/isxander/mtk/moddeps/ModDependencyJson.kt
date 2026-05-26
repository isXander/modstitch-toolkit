package dev.isxander.mtk.moddeps

import tools.jackson.databind.json.JsonMapper
import tools.jackson.dataformat.toml.TomlMapper

internal val modDependencyJsonMapper: JsonMapper = JsonMapper.builder().build()
internal val modDependencyTomlMapper: TomlMapper = TomlMapper()

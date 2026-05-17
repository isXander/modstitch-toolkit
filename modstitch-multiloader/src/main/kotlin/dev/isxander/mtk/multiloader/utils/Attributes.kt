package dev.isxander.mtk.multiloader.utils

import org.gradle.api.attributes.Attribute

// https://github.com/mcgradleconventions#attributes
val modLoaderAttribute = Attribute.of("io.github.mcgradleconventions.loader", String::class.java)

const val MOD_LOADER_ATTRIBUTE_COMMON = "common"
const val MOD_LOADER_ATTRIBUTE_FABRIC = "fabric"
const val MOD_LOADER_ATTRIBUTE_NEOFORGE = "neoforge"

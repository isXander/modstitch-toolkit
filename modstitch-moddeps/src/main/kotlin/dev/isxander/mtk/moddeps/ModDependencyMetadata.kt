package dev.isxander.mtk.moddeps

import org.gradle.api.Action
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class ModDependencyPublishingMetadata @Inject constructor(
    objects: ObjectFactory,
) {
    val modrinthProject: Property<String> = objects.property(String::class.java)
    val curseForgeProject: Property<String> = objects.property(String::class.java)

    fun modrinth(project: String) {
        modrinthProject.set(project)
    }

    fun curseforge(project: String) {
        curseForgeProject.set(project)
    }
}

abstract class ModDependencyMetadata @Inject constructor(
    objects: ObjectFactory,
) {
    val explicitModId: Property<String> = objects.property(String::class.java)
    val relationship: Property<ModDependencyRelationship> =
        objects.property(ModDependencyRelationship::class.java)
            .convention(ModDependencyRelationship.Required)
    val publishing: ModDependencyPublishingMetadata =
        objects.newInstance(ModDependencyPublishingMetadata::class.java)

    fun required() {
        relationship.set(ModDependencyRelationship.Required)
    }

    fun optional() {
        relationship.set(ModDependencyRelationship.Optional)
    }

    fun incompatible() {
        relationship.set(ModDependencyRelationship.Incompatible)
    }

    fun embedded() {
        relationship.set(ModDependencyRelationship.Embedded)
    }

    fun modId(modId: String) {
        explicitModId.set(modId)
    }

    fun publish(configure: Action<in ModDependencyPublishingMetadata>) {
        configure.execute(publishing)
    }

    fun publish(configure: ModDependencyPublishingMetadata.() -> Unit) {
        publishing.configure()
    }
}

abstract class ModDependencySpec @Inject constructor(
    val dependency: ExternalModuleDependency,
    val metadata: ModDependencyMetadata,
) {
    fun version(configure: Action<in MutableVersionConstraint>) {
        dependency.version(configure)
    }

    fun version(configure: MutableVersionConstraint.() -> Unit) {
        dependency.version(
            object : Action<MutableVersionConstraint> {
                override fun execute(constraint: MutableVersionConstraint) {
                    constraint.configure()
                }
            },
        )
    }

    fun exclude(group: String? = null, module: String? = null) {
        val notation = mutableMapOf<String, String>()
        group?.let { notation["group"] = it }
        module?.let { notation["module"] = it }
        dependency.exclude(notation)
    }

    fun required() = metadata.required()
    fun optional() = metadata.optional()
    fun incompatible() = metadata.incompatible()
    fun embedded() = metadata.embedded()
    fun modId(modId: String) = metadata.modId(modId)

    fun publish(configure: Action<in ModDependencyPublishingMetadata>) {
        metadata.publish(configure)
    }

    fun publish(configure: ModDependencyPublishingMetadata.() -> Unit) {
        metadata.publish(configure)
    }
}

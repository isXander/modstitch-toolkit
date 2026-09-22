package dev.isxander.mtk.multiloader.publishing

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.tasks.GenerateModuleMetadata
import org.gradle.kotlin.dsl.withType
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

/**
 * Capabilities on configurations describe project dependencies. Published capabilities must
 * instead describe the publication, whose coordinates may differ (e.g. Stonecutter projects).
 * Gradle has no public API to map capabilities per publication, so adjust the generated
 * metadata as part of its producing task, before publishing or signing consumes the file.
 */
internal fun configurePublicationCapabilities(project: Project) {
    project.tasks.withType<GenerateModuleMetadata>().configureEach {
        inputs.property("mtk.capabilityGroup", project.provider { project.group.toString() })
        inputs.property("mtk.capabilityName", project.name)
        inputs.property("mtk.capabilityVersion", project.provider { project.version.toString() })
        doLast(PublicationCapabilitiesAction())
    }
}

private class PublicationCapabilitiesAction : Action<Task> {
    override fun execute(task: Task) {
        val metadataTask = task as GenerateModuleMetadata
        val inputs = task.inputs.properties
        val group = inputs.getValue("mtk.capabilityGroup") as String
        val name = inputs.getValue("mtk.capabilityName") as String
        val version = inputs.getValue("mtk.capabilityVersion") as String
        val names = setOf(name, "$name-common", "$name-fabric", "$name-neoforge")
        val file = metadataTask.outputFile.get().asFile
        val mapper = JsonMapper.builder().build()
        val metadata = mapper.readTree(file)
        val component = metadata.path("component")

        for (variant in metadata.path("variants")) {
            val loader = variant.path("attributes").path("io.github.mcgradleconventions.loader").stringValue()
            if (loader !in setOf("common", "fabric", "neoforge")) continue
            for (capability in variant.path("capabilities")) {
                val capabilityName = capability.path("name").stringValue()
                if (capability.path("group").stringValue() != group ||
                    capability.path("version").stringValue() != version ||
                    capabilityName !in names
                ) continue

                capability as ObjectNode
                capability.put("group", component.path("group").stringValue())
                capability.put("name", component.path("module").stringValue() + capabilityName.removePrefix(name))
                capability.put("version", component.path("version").stringValue())
            }
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, metadata)
    }
}

package dev.isxander.mtk.moddeps

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import java.util.IdentityHashMap
import javax.inject.Inject

abstract class MinecraftDependenciesExtension @Inject constructor(
    private val project: Project,
    private val objects: ObjectFactory,
) {
    private val metadataByDependency = IdentityHashMap<Dependency, ModDependencyMetadata>()
    private val metadataByCoordinates = mutableMapOf<DependencyCoordinates, ModDependencyMetadata>()
    private val dependencySets = mutableMapOf<DependencySetKey, ModDependencySet>()

    fun modDependency(
        notation: Any,
        configure: Action<in ModDependencySpec> = Action {},
    ): ExternalModuleDependency {
        val dependency = createDependency(notation)
        val metadata = objects.newInstance(ModDependencyMetadata::class.java)
        val spec = objects.newInstance(ModDependencySpec::class.java, dependency, metadata)

        configure.execute(spec)
        metadataByDependency[dependency] = metadata
        metadataByCoordinates[dependency.coordinates] = metadata

        return dependency
    }

    private fun createDependency(notation: Any): ExternalModuleDependency =
        when (notation) {
            is MinimalExternalModuleDependency -> notation.copy()
            is ExternalModuleDependency -> notation.copy()
            is ProviderConvertible<*> -> createDependency(notation.asProvider())
            is Provider<*> -> createDependency(
                notation.get()
                    ?: throw IllegalArgumentException("modDependency provider resolved to null."),
            )

            else -> project.dependencies.create(notation) as? ExternalModuleDependency
                ?: throw IllegalArgumentException(
                    "modDependency only supports external module dependencies. " +
                            "If this came from a version catalog, pass a leaf library alias provider, not an accessor group.",
                )
        }

    operator fun invoke(
        notation: Any,
        configure: ModDependencySpec.() -> Unit = {},
    ): ExternalModuleDependency =
        modDependency(
            notation,
            object : Action<ModDependencySpec> {
                override fun execute(spec: ModDependencySpec) {
                    spec.configure()
                }
            },
        )

    fun fabric(configuration: NamedDomainObjectProvider<Configuration>): ModDependencySet =
        getOrCreate(configuration.get(), ModLoaderKind.Fabric)

    fun fabric(configuration: Configuration): ModDependencySet =
        getOrCreate(configuration, ModLoaderKind.Fabric)

    fun fabric(configurationName: String): ModDependencySet =
        getOrCreate(project.configurations.getByName(configurationName), ModLoaderKind.Fabric)

    fun neoforge(configuration: NamedDomainObjectProvider<Configuration>): ModDependencySet =
        getOrCreate(configuration.get(), ModLoaderKind.NeoForge)

    fun neoforge(configuration: Configuration): ModDependencySet =
        getOrCreate(configuration, ModLoaderKind.NeoForge)

    fun neoforge(configurationName: String): ModDependencySet =
        getOrCreate(project.configurations.getByName(configurationName), ModLoaderKind.NeoForge)

    internal fun metadataFor(dependency: Dependency): ModDependencyMetadata? =
        metadataByDependency[dependency] ?: metadataByCoordinates[dependency.coordinates]

    private fun getOrCreate(configuration: Configuration, loaderKind: ModLoaderKind): ModDependencySet =
        dependencySets.getOrPut(DependencySetKey(project.path, configuration.name, loaderKind)) {
            val task = registerGenerateTask(configuration, loaderKind)
            DefaultModDependencySet(
                loaderKind = loaderKind,
                configurationName = configuration.name,
                metadataFile = task.flatMap { it.metadataFile },
            )
        }

    private fun registerGenerateTask(
        configuration: Configuration,
        loaderKind: ModLoaderKind,
    ): TaskProvider<GenerateModDependencySetTask> {
        val taskName = "generate${configuration.name.capitalized()}${loaderKind.name}ModDependencySet"

        return project.tasks.register<GenerateModDependencySetTask>(taskName) {
            this.loaderKind.set(loaderKind)
            configurationName.set(configuration.name)
            declaredDependencies.set(project.provider {
                configuration.dependencies.mapNotNull { dependency ->
                    dependency.toDeclaredInfo()
                }
            })
            selectedArtifacts.set(project.provider {
                configuration.directResolvedArtifacts()
            })
            metadataFile.set(
                project.layout.buildDirectory.file(
                    "modstitch-moddeps/${configuration.name}/${loaderKind.name.lowercase()}/dependencies.json",
                ),
            )
        }
    }

    private fun Configuration.directResolvedArtifacts(): List<ResolvedModDependencyArtifact> {
        val artifactResults = incoming.artifactView {
            attributes.attribute(
                ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                ArtifactTypeDefinition.JAR_TYPE,
            )
        }.artifacts.artifacts
        val artifactsByComponent = artifactResults.associateBy { artifact ->
            artifact.id.componentIdentifier
        }

        return incoming.resolutionResult.root.dependencies
            .filterIsInstance<ResolvedDependencyResult>()
            .mapNotNull { dependency ->
                val requested = dependency.requested as? ModuleComponentSelector
                    ?: return@mapNotNull null
                val selected = dependency.selected.id as? ModuleComponentIdentifier
                val artifact = artifactsByComponent[dependency.selected.id]
                    ?: return@mapNotNull null

                ResolvedModDependencyArtifact(
                    group = requested.group,
                    name = requested.module,
                    selectedVersion = selected?.version,
                    artifactFile = artifact.file,
                )
            }
    }

    private fun Dependency.toDeclaredInfo(): DeclaredModDependencyInfo? {
        val name = name.takeIf(String::isNotBlank) ?: return null
        val metadata = metadataFor(this)
        val versionConstraint = (this as? ExternalModuleDependency)?.versionConstraint
        val declaredRange = versionConstraint
            ?.strictVersion
            ?.takeIf(String::isNotBlank)
            ?: versionConstraint
                ?.requiredVersion
                ?.takeIf(String::isNotBlank)

        return DeclaredModDependencyInfo(
            group = group,
            name = name,
            declaredVersionRange = declaredRange,
            relationship = metadata?.relationship?.getOrElse(ModDependencyRelationship.Required)
                ?: ModDependencyRelationship.Required,
            explicitModId = metadata?.explicitModId?.orNull,
            modrinthProject = metadata?.publishing?.modrinthProject?.orNull,
            curseForgeProject = metadata?.publishing?.curseForgeProject?.orNull,
        )
    }

    private fun String.capitalized(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecaseChar() else it }

    private data class DependencySetKey(
        val projectPath: String,
        val configurationName: String,
        val loaderKind: ModLoaderKind,
    )

    private val Dependency.coordinates: DependencyCoordinates
        get() = DependencyCoordinates(group, name)

    private data class DependencyCoordinates(
        val group: String?,
        val name: String,
    )
}

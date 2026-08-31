package io.github.hfhbd.jib

import com.google.cloud.tools.jib.api.JavaContainerBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.features.annotations.BindsProjectFeature
import org.gradle.features.binding.ProjectFeatureApplicationContext
import org.gradle.features.binding.ProjectFeatureApplyAction
import org.gradle.features.binding.ProjectFeatureBinding
import org.gradle.features.binding.ProjectFeatureBindingBuilder
import org.gradle.features.dsl.bindProjectFeature
import org.gradle.features.file.ProjectFeatureLayout
import org.gradle.features.registration.ConfigurationRegistrar
import org.gradle.features.registration.TaskRegistrar
import org.jetbrains.kotlin.gradle.declarative.projecttypes.jvmapplication.JvmApplicationProjectType
import javax.inject.Inject

@BindsProjectFeature(JibFeature::class)
class JibFeature : Plugin<Project>, ProjectFeatureBinding {
    override fun apply(project: Project) {}
    override fun bind(builder: ProjectFeatureBindingBuilder) {
        builder.bindProjectFeature("jib", ApplyAction::class)
    }

    abstract class ApplyAction : ProjectFeatureApplyAction<JibDefinition, JibBuildModel, JvmApplicationProjectType> {
        @get:Inject
        abstract val tasks: TaskRegistrar
        @get:Inject
        abstract val configurations: ConfigurationRegistrar

        @get:Inject
        abstract val layout: ProjectFeatureLayout
        @get:Inject
        abstract val dependencyFactory: DependencyFactory

        override fun apply(
            context: ProjectFeatureApplicationContext,
            definition: JibDefinition,
            buildModel: JibBuildModel,
            parentDefinition: JvmApplicationProjectType,
        ) {
            val parentBuildModel = context.getBuildModel(parentDefinition)
            val mainCompilationUnit = parentBuildModel.compilationUnits.getByName("main")
            val mainApplication = parentBuildModel.applications.getByName("main")

            buildModel.from.image.set(definition.from.image)
            buildModel.from.image.convention(
                mainCompilationUnit.jvmEcosystem.jdkToolchain.languageVersion.map {
                    "eclipse-temurin:${it.asInt()}-jre"
                }
            )
            buildModel.from.username.set(definition.from.username)
            buildModel.from.password.set(definition.from.password)
            buildModel.from.platforms.addAll(definition.from.platforms)

            buildModel.to.image.set(definition.to.image)
            buildModel.to.username.set(definition.to.username)
            buildModel.to.password.set(definition.to.password)
            buildModel.to.format.set(definition.to.format)
            buildModel.to.format.convention(ImageFormat.OCI)

            buildModel.container.jvmFlags.addAll(definition.container.jvmFlags)
            buildModel.container.environment.putAll(definition.container.environment)
            buildModel.container.entrypoint.addAll(definition.container.entrypoint)
            buildModel.container.args.addAll(definition.container.args)
            buildModel.container.ports.addAll(definition.container.ports)
            buildModel.container.volumes.addAll(definition.container.volumes)
            buildModel.container.labels.putAll(definition.container.labels)
            buildModel.container.appRoot.set(definition.container.appRoot)
            buildModel.container.appRoot.convention(JavaContainerBuilder.DEFAULT_APP_ROOT)
            buildModel.container.user.set(definition.container.user)
            buildModel.container.workingDirectory.set(definition.container.workingDirectory)
            buildModel.container.mainClass.set(mainApplication.mainClassName)

            val workerDeps = configurations.dependencyScope("jibWorkerDeps") {
                it.dependencies.add(
                    dependencyFactory.create("${JIB_MODULE}:${JIB_VERSION}")
                )
            }
            val workerClasspath = configurations.resolvable("jibWorkerClasspath") {
                it.extendsFrom(workerDeps)
            }

            val OUTPUT_FILE_NAME = "jib/image"

            val runtimeClasspathArtifacts =
                mainApplication.runtimeOnlyConfiguration.incoming.artifacts.resolvedArtifacts
            val details = runtimeClasspathArtifacts.map {
                it.map { artifact -> artifact.id }
            }
            val files = runtimeClasspathArtifacts.map {
                it.map { artifact -> artifact.file }
            }

            tasks.register("jib", JibTask::class.java) {
                it.fromUsername.set(buildModel.from.username)
                it.fromPassword.set(buildModel.from.password)
                it.fromImage.set(buildModel.from.image)
                it.fromPlatforms.set(buildModel.from.platforms)

                it.toImage.set(buildModel.to.image)
                it.toImage.set(buildModel.to.username)
                it.toImage.set(buildModel.to.password)
                it.toTags.addAll(buildModel.to.tags)
                it.toFormat.set(buildModel.to.format)

                it.jvmFlags.set(buildModel.container.jvmFlags)
                it.environment.set(buildModel.container.environment)
                it.entrypoint.set(buildModel.container.entrypoint)
                it.mainClass.set(buildModel.container.mainClass)
                it.args.set(buildModel.container.args)
                it.ports.set(buildModel.container.ports)
                it.volumes.set(buildModel.container.volumes)
                it.labels.set(buildModel.container.labels)
                it.appRoot.set(buildModel.container.appRoot)
                it.user.set(buildModel.container.user)
                it.workingDirectory.set(buildModel.container.workingDirectory)

                it.classesDirectory.set(mainCompilationUnit.destinationDirectory)
                it.details.set(details)
                it.files.set(files)
                it.resources.set(mainCompilationUnit.resourcesOutput)

                it.digest.convention(layout.contextBuildDirectory.map { it.file("$OUTPUT_FILE_NAME.digest") })
                it.imageId.convention(layout.contextBuildDirectory.map { it.file("$OUTPUT_FILE_NAME.id") })

                it.jibClasspath.from(workerClasspath)
            }
        }
    }
}

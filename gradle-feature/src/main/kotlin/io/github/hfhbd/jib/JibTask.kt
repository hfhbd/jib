package io.github.hfhbd.jib

import com.google.cloud.tools.jib.api.Containerizer
import com.google.cloud.tools.jib.api.Credential
import com.google.cloud.tools.jib.api.ImageReference
import com.google.cloud.tools.jib.api.JavaContainerBuilder
import com.google.cloud.tools.jib.api.Jib
import com.google.cloud.tools.jib.api.JibContainerBuilder
import com.google.cloud.tools.jib.api.LogEvent.Level
import com.google.cloud.tools.jib.api.Ports
import com.google.cloud.tools.jib.api.RegistryImage
import com.google.cloud.tools.jib.api.TarImage
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath
import com.google.cloud.tools.jib.api.buildplan.ImageFormat
import com.google.cloud.tools.jib.api.buildplan.Platform
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.LocalState
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.nio.file.Path
import javax.inject.Inject

@CacheableTask
abstract class JibTask : DefaultTask() {

    private val isOffline = project.gradle.startParameter.isOffline

    @get:Input
    @get:Optional
    abstract val fromUsername: Property<String>

    @get:Input
    @get:Optional
    abstract val fromPassword: Property<String>

    @get:Input
    abstract val fromImage: Property<String>

    @get:Input
    abstract val fromPlatforms: ListProperty<String>

    @get:Input
    abstract val toImage: Property<String>

    @get:Input
    @get:Optional
    abstract val toUsername: Property<String>

    @get:Input
    @get:Optional
    abstract val toPassword: Property<String>

    @get:Input
    abstract val toTags: SetProperty<String>

    @get:Input
    abstract val toFormat: Property<io.github.hfhbd.jib.ImageFormat>

    @get:Input
    abstract val jvmFlags: ListProperty<String>

    @get:Input
    abstract val environment: MapProperty<String, String>

    @get:Input
    abstract val entrypoint: ListProperty<String>

    @get:Input
    abstract val mainClass: Property<String>

    @get:Input
    abstract val args: ListProperty<String>

    @get:Input
    abstract val ports: ListProperty<String>

    @get:Input
    abstract val volumes: ListProperty<String>

    @get:Input
    abstract val labels: MapProperty<String, String>

    @get:Input
    abstract val appRoot: Property<String>

    @get:Input
    @get:Optional
    abstract val user: Property<String>

    @get:Input
    @get:Optional
    abstract val workingDirectory: Property<String>

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    abstract val classesDirectory: DirectoryProperty

    @get:Input
    abstract val details: ListProperty<ComponentArtifactIdentifier>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val files: ListProperty<File>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:IgnoreEmptyDirectories
    abstract val resources: DirectoryProperty

    @get:LocalState
    abstract val applicationCache: DirectoryProperty

    @get:LocalState
    abstract val baseImageCache: DirectoryProperty

    @get:OutputFile
    abstract val digest: RegularFileProperty

    @get:OutputFile
    abstract val imageId: RegularFileProperty

    @get:OutputFile
    abstract val tarFile: RegularFileProperty

    @get:Classpath
    abstract val jibClasspath: ConfigurableFileCollection

    @get:Inject
    protected abstract val workerExecutor: WorkerExecutor

    @TaskAction
    protected fun performAction() {
        val dependencies = details.get().zip(files.get()).map { (identifier, jar) ->
            DependencyFileType(
                jar = jar,
                type = when (identifier.componentIdentifier) {
                    is ProjectComponentIdentifier -> DependencyFileType.Type.Project
                    is MavenUniqueSnapshotComponentIdentifier -> DependencyFileType.Type.Snapshot
                    else -> DependencyFileType.Type.External
                }
            )
        }

        workerExecutor.classLoaderIsolation {
            it.classpath.from(jibClasspath)
        }.submit(Worker::class.java) {
            it.fromImage.set(fromImage)
            it.fromUsername.set(fromUsername)
            it.fromPassword.set(fromPassword)
            it.fromPlatforms.addAll(fromPlatforms)

            it.toImage.set(toImage)
            it.toUsername.set(toUsername)
            it.toPassword.set(toPassword)
            it.toTags.set(toTags)
            it.toFormat.set(toFormat)

            it.jvmFlags.set(jvmFlags)
            it.environment.set(environment)
            it.entrypoint.set(entrypoint)
            it.mainClass.set(mainClass)
            it.args.set(args)
            it.ports.set(ports)
            it.volumes.set(volumes)
            it.labels.set(labels)
            it.appRoot.set(appRoot)
            it.user.set(user)
            it.workingDirectory.set(workingDirectory)

            it.classesDirectory.set(classesDirectory)
            it.dependencies.set(dependencies)
            it.resources.set(resources)

            it.applicationCache.set(applicationCache)
            it.baseImageCache.set(baseImageCache)

            it.digest.set(digest)
            it.imageId.set(imageId)
            it.tarFile.set(tarFile)

            it.offline.set(isOffline)
        }
    }
}

@JvmRecord
data class DependencyFileType(val jar: File, val type: Type) : java.io.Serializable {
    enum class Type : java.io.Serializable {
        External,
        Snapshot,
        Project,
    }
}

abstract class Worker : WorkAction<Worker.Params> {
    interface Params : WorkParameters {
        val fromImage: Property<String>
        val fromPlatforms: ListProperty<String>
        val fromUsername: Property<String>
        val fromPassword: Property<String>

        val toImage: Property<String>
        val toUsername: Property<String>
        val toPassword: Property<String>
        val toTags: SetProperty<String>
        val toFormat: Property<io.github.hfhbd.jib.ImageFormat>

        val jvmFlags: ListProperty<String>
        val environment: MapProperty<String, String>
        val entrypoint: ListProperty<String>
        val mainClass: Property<String>
        val args: ListProperty<String>
        val ports: ListProperty<String>
        val volumes: ListProperty<String>
        val labels: MapProperty<String, String>
        val appRoot: Property<String>
        val user: Property<String>
        val workingDirectory: Property<String>

        val classesDirectory: DirectoryProperty
        val dependencies: ListProperty<DependencyFileType>
        val resources: DirectoryProperty

        val applicationCache: DirectoryProperty
        val baseImageCache: DirectoryProperty

        val digest: RegularFileProperty
        val imageId: RegularFileProperty
        val tarFile: RegularFileProperty
        val offline: Property<Boolean>
    }

    private val logger: Logger = Logging.getLogger(javaClass)

    override fun execute() {
        val imageRef = ImageReference.parse(parameters.toImage.get())
        val containerizer = if (parameters.offline.get()) {
            val targetImage = TarImage.at(parameters.tarFile.get().asFile.toPath())
                .named(imageRef)
            Containerizer.to(targetImage)
        } else {
            val targetImage = RegistryImage.named(imageRef)
            targetImage.configureCredentialRetrievers(
                imageRef,
                parameters.toUsername.orNull,
                parameters.toPassword.orNull
            )
            Containerizer.to(targetImage)
        }

        val baseImageCachePath = parameters.baseImageCache.asFile.get().toPath()
        val applicationCachePath = parameters.applicationCache.asFile.get().toPath()

        buildImage(
            tags = parameters.toTags.get(),
            digestOutputFile = parameters.digest.asFile.get(),
            imageIdOutputFile = parameters.imageId.asFile.get(),
            appRoot = parameters.appRoot.get(),
            containerizer,
            baseImageCachePath,
            applicationCachePath,
            parameters.classesDirectory.get(),
            parameters.resources.orNull,
            parameters.dependencies.get(),
        )
    }

    private fun RegistryImage.configureCredentialRetrievers(
        imageRef: ImageReference,
        username: String?,
        password: String?,
    ) {
        val credHelperFactory = CredentialRetrieverFactory.forImage(imageRef) {
            when (it.level) {
                Level.ERROR -> logger.error(it.message)
                Level.WARN -> logger.warn(it.message)
                Level.LIFECYCLE -> logger.lifecycle(it.message)
                Level.PROGRESS -> logger.lifecycle(it.message)
                Level.INFO -> logger.info(it.message)
                Level.DEBUG -> logger.debug(it.message)
            }
        }

        addCredentialRetriever(credHelperFactory.wellKnownCredentialHelpers())
        addCredentialRetriever(credHelperFactory.googleApplicationDefaultCredentials())
        addCredentialRetriever {
            if (username != null && password != null) {
                java.util.Optional.of(Credential.from(username, password))
            } else {
                java.util.Optional.empty()
            }
        }
    }

    private fun JavaContainerBuilder(
        fromImage: String,
        fromUsername: String?,
        fromPassword: String?,
    ): JavaContainerBuilder {
        if (fromImage.startsWith(Jib.TAR_IMAGE_PREFIX)) {
            return JavaContainerBuilder.from(fromImage)
        }

        val imageReference = ImageReference.parse(fromImage)

        val baseImage = RegistryImage.named(imageReference)
        baseImage.configureCredentialRetrievers(imageReference, fromUsername, fromPassword)

        return JavaContainerBuilder.from(baseImage)
    }

    private fun setupBuilder(
        appRoot: String,
        sourceSetOutputClassesDir: Directory,
        sourceSetOutputResourcesDir: Directory?,
        dependencies: List<DependencyFileType>,
    ): JibContainerBuilder {
        val appRoot = AbsoluteUnixPath.get(appRoot)

        val javaContainerBuilder = JavaContainerBuilder(
            parameters.fromImage.get(),
            parameters.fromUsername.orNull,
            parameters.fromPassword.orNull,
        ).apply {
            setAppRoot(appRoot)

            addClasses(sourceSetOutputClassesDir.asFile.toPath())

            for ((jar, type) in dependencies) {
                when (type) {
                    DependencyFileType.Type.External -> {
                        addDependencies(jar.toPath())
                    }

                    DependencyFileType.Type.Snapshot -> {
                        addSnapshotDependencies(jar.toPath())
                    }

                    DependencyFileType.Type.Project -> {
                        addProjectDependencies(jar.toPath())
                    }
                }
            }

            if (sourceSetOutputResourcesDir != null && sourceSetOutputResourcesDir.asFile.exists()) {
                addResources(sourceSetOutputResourcesDir.asFile.toPath())
            }

            setMainClass(parameters.mainClass.get())
        }

        val platforms = parameters.fromPlatforms.get().mapTo(mutableSetOf()) {
            val (architecture, os) = it.split("/")
            Platform(architecture, os)
        }.ifEmpty { setOf(Platform("amd64", "linux")) }

        val volumes = parameters.volumes.get().mapTo(mutableSetOf()) {
            AbsoluteUnixPath.get(it)
        }.ifEmpty { emptySet() }

        return javaContainerBuilder.toContainerBuilder().apply {
            when (parameters.toFormat.get()) {
                io.github.hfhbd.jib.ImageFormat.OCI -> setFormat(ImageFormat.OCI)
                io.github.hfhbd.jib.ImageFormat.Docker -> setFormat(ImageFormat.Docker)
            }
            setPlatforms(platforms)
            parameters.args.get().takeIf { it.isNotEmpty() }?.let {
                setProgramArguments(it)
            }
            setEnvironment(parameters.environment.get())
            parameters.ports.get().takeIf { it.isNotEmpty() }?.let {
                setExposedPorts(Ports.parse(it))
            }
            setVolumes(volumes)
            setLabels(parameters.labels.get())
            parameters.user.orNull?.let {
                setUser(it)
            }
            parameters.workingDirectory.orNull?.let {
                setWorkingDirectory(AbsoluteUnixPath.get(it))
            }
        }
    }

    private fun buildImage(
        tags: Set<String>,
        digestOutputFile: File,
        imageIdOutputFile: File,
        appRoot: String,
        containerizer: Containerizer,
        baseImageCachePath: Path,
        applicationCachePath: Path,
        sourceSetOutputClassesDir: Directory,
        sourceSetOutputResourcesDir: Directory?,
        dependencies: List<DependencyFileType>,
    ) {
        val jibContainerBuilder = setupBuilder(
            appRoot,
            sourceSetOutputClassesDir,
            sourceSetOutputResourcesDir,
            dependencies = dependencies,
        )

        containerizer.setBaseImageLayersCache(baseImageCachePath)
        containerizer.setApplicationLayersCache(applicationCachePath)

        for (it in tags) {
            containerizer.withAdditionalTag(it)
        }

        val jibContainer = jibContainerBuilder.containerize(containerizer)

        val imageDigest = jibContainer.digest.toString()
        digestOutputFile.writeText(imageDigest)

        val imageId = jibContainer.imageId.toString()
        imageIdOutputFile.writeText(imageId)
    }
}

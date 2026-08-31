package io.github.hfhbd.jib

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Nested
import org.gradle.features.binding.BuildModel

interface JibBuildModel : BuildModel {
    @get:Nested
    val from: From

    @get:Nested
    val to: To

    @get:Nested
    val container: Container

    interface To {
        val image: Property<String>
        val username: Property<String>
        val password: Property<String>
        val tags: SetProperty<String>

        val format: Property<ImageFormat>
    }

    interface From {
        val image: Property<String>
        val username: Property<String>
        val password: Property<String>

        val platforms: ListProperty<String>
    }

    interface Container {
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
    }
}

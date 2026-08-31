package io.github.hfhbd.jib

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinFeatureTest {
    @Test
    fun generatesLocalTarFile() {
        val projectDir = createTempDirectory("integration-test").toFile()
        File(projectDir, "settings.gradle.kts").writeText(
            // language=kotlin
            """
               |pluginManagement {
               |    repositories {
               |        mavenCentral()
               |        gradlePluginPortal()
               |        maven {
               |            url = uri("https://raw.githubusercontent.com/Kotlin/declarative-gradle-jetbrains-ecosystem-plugin/refs/heads/maven2")
               |        }
               |    }
               |}
               |
               |
               |plugins {
               |    id("org.jetbrains.ecosystem")
               |    id("io.github.hfhbd.jib.features")
               |}
               |
               |dependencyResolutionManagement {
               |    repositories {
               |        mavenCentral()
               |        google()
               |    }
               |}
""".trimMargin()
        )

        File(projectDir, "build.gradle.dcl").writeText(
            // language=kotlin
            """
               |jvmApplication {
               |  mainClass = "com.example.MainKt"
               |  jib {
               |    to {
               |      image = "example"
               |    }
               |  }
               |}
               |
           """.trimMargin()
        )

        writeMainClass(projectDir)

        assertBuild(projectDir)
    }

    fun writeMainClass(projectDir: File) {
        File(projectDir, "src/main/kotlin/com/example/Main.kt").apply {
            parentFile.mkdirs()

            writeText(
                // language=kotlin
                """package com.example

fun main() {
    println("Hello World")
}"""
            )
        }
    }

    private fun assertBuild(projectDir: File) : File {

        val isDebug = System.getenv("DEBUGGER_ENABLED") == "true"

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withDebug(isDebug)
            .withPluginClasspath()
            .forwardOutput()
            .withArguments(
                ":assemble",
                "--write-verification-metadata",
                "sha256",
                "-Porg.gradle.kotlin.dsl.dcl=true",
                "-Porg.gradle.isolated-projects=true",
            )
            .build()

        val jibOffline = GradleRunner.create()
            .withProjectDir(projectDir)
            .withDebug(isDebug)
            .withPluginClasspath()
            .forwardOutput()
            .withArguments(
                ":jib",
                "-Porg.gradle.kotlin.dsl.dcl=true",
                "-Porg.gradle.isolated-projects=true",
                "--offline",
            )
            .build()

        assertEquals(TaskOutcome.SUCCESS, jibOffline.task(":jib")?.outcome)
        val tarFile = File(projectDir, "build/jib/image.tar")
        assertTrue(tarFile.exists())

        return tarFile
    }
}

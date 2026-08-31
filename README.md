# jib

Wrapper for [jib](https://https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin) to use it with the DCL.

## Install

This package/Gradle plugin is uploaded to GitHub packages.

## Usage

Apply the plugin in each project.

```kotlin
// build.gradle (.kts)
jvmApplication {
    mainClass.set("HelloWorld")

    jib {
        to {
            image = "my-image:latest"
        }
    }
}

```
Only publishing to an OCI registry is supported, no local tar generation.

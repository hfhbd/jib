plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS

    repositories {
        mavenCentral()

        exclusiveContent {
            forRepository {
                maven {
                    setUrl(
                        "https://maven.pkg.github.com/Kotlin/declarative-gradle-jetbrains-ecosystem-plugin"
                    )
                    name = "KDGP"
                    metadataSources {
                        gradleMetadata()
                    }
                    credentials(org.gradle.api.credentials.PasswordCredentials::class)
                }
            }
            filter {
                includeGroupAndSubgroups("org.jetbrains.ecosystem")
            }
        }
    }
}

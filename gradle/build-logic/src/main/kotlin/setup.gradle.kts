plugins {
    kotlin("jvm")
    id("java-gradle-plugin")
    id("maven-publish")
    id("signing")
    id("io.github.hfhbd.mavencentral")
}

kotlin {
    jvmToolchain(17)
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation {}
}

tasks.validatePlugins {
    enableStricterValidation.set(true)
}

testing.suites.withType(JvmTestSuite::class).configureEach {
    useKotlinTest()
}

java {
    withJavadocJar()
    withSourcesJar()
}

// Workaround for clash between `signature` and `archives`; remove when bumping to Gradle 10:
configurations.archives {
    attributes {
        attribute(Attribute.of("deprecated", String::class.java), "true")
    }
}

publishing {
    repositories {
        maven(url = "https://maven.pkg.github.com/hfhbd/jib") {
            name = "GitHubPackages"
            credentials(PasswordCredentials::class)
        }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("hfhbd jib")
            description.set("hfhbd jib")
            url.set("https://github.com/hfhbd/jib")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("hfhbd")
                    name.set("Philip Wedemann")
                    email.set("mybztg+mavencentral@icloud.com")
                }
            }
            scm {
                connection.set("scm:git://github.com/hfhbd/jib.git")
                developerConnection.set("scm:git://github.com/hfhbd/jib.git")
                url.set("https://github.com/hfhbd/jib")
            }
            distributionManagement {
                repository {
                    id = "github"
                    name = "GitHub hfhbd Apache Maven Packages"
                    url = "https://maven.pkg.github.com/hfhbd/jib"
                }
            }
        }
    }
}

signing {
    useInMemoryPgpKeys(
        providers.gradleProperty("signingKey").orNull,
        providers.gradleProperty("signingPassword").orNull,
    )
    isRequired = providers.gradleProperty("signingKey").isPresent
    sign(publishing.publications)
}

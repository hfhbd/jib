plugins {
    id("setup")
}

dependencies {
    compileOnly(libs.jibCore)
    compileOnly(libs.kotlin.ecosystem.plugin)
}

val version = tasks.register("writeVersion",StoreVersion::class) {
    version.put("JIB_MODULE", libs.jibCore.map { it.module.toString() })
    version.put("JIB_VERSION", libs.jibCore.map { it.version.toString() })
}

sourceSets.main {
    kotlin.srcDir(version)
}

gradlePlugin.plugins.register("io.github.hfhbd.jib.features") {
    implementationClass = "io.github.hfhbd.jib.JibFeaturesPlugin"
    displayName = "hfhbd jib Features Plugin"
    description = "hfhbd jib Features Plugin"
}

configurations.configureEach {
    if (isCanBeConsumed) {
        attributes {
            attribute(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE, named(GradleVersion.current().version))
        }
    }
}

testing.suites.register("integrationTest", JvmTestSuite::class) {
    gradlePlugin.testSourceSet(sources)
    dependencies {
        implementation(gradleTestKit())
    }
}

val d = configurations.dependencyScope("d") {
    dependencies.add(libs.kotlin.ecosystem.plugin.get())
}
val r = configurations.resolvable("r") {
    extendsFrom(d)
}

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(r)
}

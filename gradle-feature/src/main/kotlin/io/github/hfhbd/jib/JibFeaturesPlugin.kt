package io.github.hfhbd.jib

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.features.annotations.RegistersProjectFeatures

@RegistersProjectFeatures(JibFeature::class)
class JibFeaturesPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {}
}

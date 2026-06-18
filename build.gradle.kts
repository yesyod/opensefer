import io.gitlab.arturbosch.detekt.extensions.DetektExtension

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.detekt) apply false
}

// Static analysis: apply detekt to every module against one shared config (config/detekt/detekt.yml).
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    configure<DetektExtension> {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        source.setFrom(
            "src/commonMain/kotlin",
            "src/androidMain/kotlin",
            "src/iosMain/kotlin",
            "src/commonTest/kotlin",
        )
    }
}

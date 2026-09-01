plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.koinCompilerPlugin)
}

dependencies {
    api(project(":exposed"))
    api(project(":koin"))
    testImplementation(project(":core"))
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testEcosystem)
    testImplementation(libs.h2)
}

configure<org.koin.compiler.plugin.KoinGradleExtension> {
    strictSafety = false
    strictSafetyForceOff = true
    compileSafety = false
}

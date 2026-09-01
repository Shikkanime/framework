import org.koin.compiler.plugin.KoinGradleExtension

plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.koinCompilerPlugin)
}

dependencies {
    implementation(project(":exposed"))
    implementation(project(":koin"))
    testImplementation(project(":core"))
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testEcosystem)
    testImplementation(libs.h2)
}

configure<KoinGradleExtension> {
    strictSafety = false
    strictSafetyForceOff = true
    compileSafety = false
}

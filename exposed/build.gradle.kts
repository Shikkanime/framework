plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":core"))
    api(libs.bundles.exposedEcosystem)
    testImplementation(kotlin("test"))
    testImplementation(libs.h2)
}
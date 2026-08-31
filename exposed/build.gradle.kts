plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":core"))
    api(libs.bundles.exposedEcosystem)
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testEcosystem)
    testImplementation(libs.h2)
    testImplementation(libs.bouncyCastle)
}
plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":core"))
    api(project(":validator"))
    api(libs.bundles.ktorServerEcosystem)
    api(libs.bundles.ktorClientEcosystem)
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testEcosystem)
    testImplementation(libs.ktorServerTestHost)
}
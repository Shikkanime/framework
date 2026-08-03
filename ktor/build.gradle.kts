plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":core"))
    api(project(":validator"))
    api(libs.bundles.ktorServerEcosystem)
    testImplementation(kotlin("test"))
}
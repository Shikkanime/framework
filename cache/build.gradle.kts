plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.bundles.serializationEcosystem)
    implementation(libs.valkeyGlide)
    implementation(libs.kotlinxCoroutinesCore)
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testEcosystem)
}
plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(libs.kotlinReflect)
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testEcosystem)
}
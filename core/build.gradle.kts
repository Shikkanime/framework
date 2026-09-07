plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-test-fixtures`
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testEcosystem)
}
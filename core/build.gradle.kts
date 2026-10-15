plugins {
    id("buildsrc.convention.kotlin-jvm")
    `java-test-fixtures`
}

dependencies {
    api(libs.kotlinxDateTime)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testEcosystem)
}
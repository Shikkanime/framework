plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":core"))
    api(project(":ktor"))
    api(libs.graphqlKotlinServer)
    api(libs.graphqlKotlinKtorServer)
    api(libs.graphqlKotlinDataLoader)
    implementation(libs.ktorServerRateLimit)
    implementation(libs.ktorServerBodyLimit)
    implementation(libs.ktorServerCompression)
    implementation(libs.ktorServerDoubleReceive)
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testEcosystem)
    testImplementation(project(":ktor-test"))
}

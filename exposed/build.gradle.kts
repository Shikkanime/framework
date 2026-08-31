plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":core"))
    api(libs.bundles.exposedEcosystem)
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.testEcosystem)
    testImplementation(libs.h2)
    // Not imported by our tests: Argon2Hasher and SCryptHasher load BouncyCastle generators
    // reflectively at runtime (Exposed throws without it). Test-only so consumers opt in.
    testImplementation(libs.bouncyCastle)
}
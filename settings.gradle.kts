dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "shikkanime-framework"

include(
    ":core",
    ":exposed",
    ":validator",
    ":ktor",
    ":cache",
    ":koin"
)
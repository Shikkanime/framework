// The code in this file is a convention plugin - a Gradle mechanism for sharing reusable build logic.
// `buildSrc` is a Gradle-recognized directory and every plugin there will be easily available in the rest of the build.
package buildsrc.convention

import org.gradle.api.tasks.testing.logging.TestLogEvent

group = "fr.shikkanime.framework"
version = providers.gradleProperty("version").get()

plugins {
    // Apply the Kotlin JVM plugin to add support for Kotlin in JVM projects.
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

kotlin {
    // Use a specific Java version to make it easier to work in different environments.
    jvmToolchain(21)
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        if (!plugins.hasPlugin("java-gradle-plugin")) {
            create<MavenPublication>("maven") {
                from(components["java"])
                artifactId = project.name
            }
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Shikkanime/framework")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: providers.gradleProperty("gpr.user").orNull
                password = System.getenv("GITHUB_TOKEN") ?: providers.gradleProperty("gpr.key").orNull
            }
        }
    }
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes["Implementation-Version"] = project.version
    }
}

tasks.withType<Test>().configureEach {
    // Configure all test Gradle tasks to use JUnitPlatform.
    useJUnitPlatform()

    // The build host has 3.7 GiB of RAM and runs the Gradle daemon, the Kotlin daemon and this
    // fork at the same time. Without a cap on the fork, the three JVMs together exhaust memory and
    // the kernel kills the build. 640 MiB holds a test JVM for this project comfortably; the fork
    // also runs with a serial collector, which costs a little throughput and saves a lot of heap.
    maxHeapSize = "640m"
    jvmArgs("-XX:MaxMetaspaceSize=256m", "-XX:+UseSerialGC")

    // One fork at a time: parallel forks multiply the memory cost by the fork count, and the
    // suite is small enough that the wall-clock saving is not worth an OOM kill.
    maxParallelForks = 1
    forkEvery = 0

    // Log information about all test results, not only the failed ones.
    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}

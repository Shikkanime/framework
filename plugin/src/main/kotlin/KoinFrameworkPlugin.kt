package fr.shikkanime.framework.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle convention plugin for Koin integration in Shikkanime Framework projects.
 *
 * Applies the underlying `io.insert-koin.compiler.plugin` and configures the target project with
 * the framework `koin` module dependency automatically, so consumers never re-declare Koin
 * dependencies or the Koin compiler plugin themselves.
 */
class KoinFrameworkPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("io.insert-koin.compiler.plugin")

        val frameworkVersion = KoinFrameworkPlugin::class.java.`package`.implementationVersion
            ?: project.providers.gradleProperty("version").orNull
            ?: project.findProperty("version")?.toString()
            ?: error("Framework version is missing. Ensure Implementation-Version in MANIFEST.MF or 'version' property in gradle.properties is configured.")

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            project.dependencies.add("implementation", "fr.shikkanime.framework:koin:$frameworkVersion")
        }
    }
}

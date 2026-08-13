package fr.shikkanime.framework.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Gradle convention plugin for Ktor integration in Shikkanime Framework projects.
 *
 * Applies the underlying `io.ktor.plugin` and configures the target project with
 * framework Ktor dependencies automatically.
 */
class KtorFrameworkPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("io.ktor.plugin")

        val frameworkVersion = KtorFrameworkPlugin::class.java.`package`.implementationVersion
            ?: project.providers.gradleProperty("version").orNull
            ?: project.findProperty("version")?.toString()
            ?: error("Framework version is missing. Ensure Implementation-Version in MANIFEST.MF or 'version' property in gradle.properties is configured.")

        project.plugins.withId("org.jetbrains.kotlin.jvm") {
            project.dependencies.add("implementation", "fr.shikkanime.framework:ktor:$frameworkVersion")
        }
    }
}

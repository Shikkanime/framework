package fr.shikkanime.framework.plugin

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class KoinFrameworkPluginTest {

    @Nested
    @DisplayName("tests for KoinFrameworkPlugin application")
    inner class ApplicationTests {

        @Test
        fun `should apply io koin compiler plugin and add framework koin dependency`() {
            // Given
            val project = ProjectBuilder.builder().build()
            project.version = "0.0.8-SNAPSHOT"
            project.plugins.apply("org.jetbrains.kotlin.jvm")

            // When
            project.plugins.apply(KoinFrameworkPlugin::class.java)

            // Then
            assertTrue(project.plugins.hasPlugin("io.insert-koin.compiler.plugin"))
            val hasKoinDependency = project.configurations.getByName("implementation").dependencies.any {
                it.group == "fr.shikkanime.framework" && it.name == "koin"
            }
            assertTrue(hasKoinDependency)
        }
    }
}

package fr.shikkanime.framework.plugin

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class KtorFrameworkPluginTest {

    @Nested
    @DisplayName("tests for KtorFrameworkPlugin application")
    inner class ApplicationTests {

        @Test
        fun `should apply io ktor plugin and add framework ktor dependency`() {
            // Given
            val project = ProjectBuilder.builder().build()
            project.version = "0.0.7-SNAPSHOT"
            project.plugins.apply("org.jetbrains.kotlin.jvm")

            // When
            project.plugins.apply(KtorFrameworkPlugin::class.java)

            // Then
            assertTrue(project.plugins.hasPlugin("io.ktor.plugin"))
            val hasKtorDependency = project.configurations.getByName("implementation").dependencies.any {
                it.group == "fr.shikkanime.framework" && it.name == "ktor"
            }
            assertTrue(hasKtorDependency)
        }
    }
}

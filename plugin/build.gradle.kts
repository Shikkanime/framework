plugins {
    `java-gradle-plugin`
    id("buildsrc.convention.kotlin-jvm")
}

gradlePlugin {
    plugins {
        create("ktorFrameworkPlugin") {
            id = "fr.shikkanime.framework.ktor"
            implementationClass = "fr.shikkanime.framework.plugin.KtorFrameworkPlugin"
        }
    }
}

dependencies {
    implementation(libs.ktorGradlePlugin)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinGradlePlugin)
    testImplementation(libs.bundles.testEcosystem)
}

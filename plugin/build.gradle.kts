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
        create("koinFrameworkPlugin") {
            id = "fr.shikkanime.framework.koin"
            implementationClass = "fr.shikkanime.framework.plugin.KoinFrameworkPlugin"
        }
    }
}

dependencies {
    implementation(libs.ktorGradlePlugin)
    implementation(libs.koinGradlePlugin)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinGradlePlugin)
    testImplementation(libs.bundles.testEcosystem)
}

plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(platform(libs.koinBom))
    api(libs.koinCore)
    api(libs.koinAnnotations)
}

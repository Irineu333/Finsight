plugins {
    id("kmp-library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(projects.core.utils)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.core.domain"
}
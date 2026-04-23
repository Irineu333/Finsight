plugins {
    id("kmp-library")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.arrow.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            api(projects.core.domain)
            api(projects.core.utils)
        }
    }
}

android {
    namespace = "com.neoutils.finsight.feature.transactions.api"
}